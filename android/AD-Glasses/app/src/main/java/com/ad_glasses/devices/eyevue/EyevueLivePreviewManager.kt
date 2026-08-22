package com.ad_glasses.devices.eyevue

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import com.ad_glasses.ota.LivePreviewState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/** Eyevue live-mode flow: command BLE, join vendor Wi-Fi, then play the model URL. */
class EyevueLivePreviewManager(
    private val context: Context,
    private val eyevueManager: EyevueManager,
) {
    companion object {
        private const val TAG = "EyevueLive"
        private val CLIENT = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private val _uiState = MutableStateFlow(LivePreviewState())
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val transport = EyevueWifiTransport(context)
    private var job: Job? = null
    private var player: ExoPlayer? = null
    private var playerListener: Player.Listener? = null
    private var onSessionFinished: () -> Unit = {}
    private var finishedNotified = true

    val uiState: StateFlow<LivePreviewState> = _uiState.asStateFlow()
    val isActive: Boolean get() = job?.isActive == true

    fun start(
        profile: EyevueMediaProfile,
        onSessionFinished: () -> Unit,
    ) {
        if (isActive) return
        this.onSessionFinished = onSessionFinished
        finishedNotified = false
        job = scope.launch { run(profile) }
    }

    fun stop() {
        job?.cancel()
        job = null
        releasePlayer()
        eyevueManager.stopLive()
        transport.disconnect()
        resetState()
        notifyFinished()
    }

    fun release() {
        stop()
        scope.cancel()
    }

    fun getPlayer(): ExoPlayer? = player

    private suspend fun run(profile: EyevueMediaProfile) {
        try {
            if (!eyeVueConnected()) throw IOException("Eyevue BLE is not connected")
            updateState("Starting live mode", "Sending Eyevue 0x67 command", scanning = true)
            eyevueManager.startLive(profile.mode == EyevueWifiMode.AP)
            val ssid = eyevueManager.awaitWifiSsid(profile.mode == EyevueWifiMode.P2P)
                ?: throw IOException("Eyevue did not report the live Wi-Fi SSID")

            updateState("Connecting Wi-Fi", "Joining $ssid", scanning = true)
            transport.connect(
                mode = profile.mode,
                ssid = ssid,
                password = "12345678",
                baseIp = profile.baseIp,
            ).getOrElse { throw it }

            if (!profile.isTSeries) {
                updateState("Starting stream", "Requesting Eyevue HTTP live endpoint", scanning = true)
                requestLiveEndpoint(profile.baseIp)
            }

            val streamUrl = if (profile.isTSeries) {
                "rtsp://${profile.baseIp}/h264"
            } else {
                "rtsp://${profile.baseIp}/xxx.mov"
            }
            play(streamUrl)
            _uiState.value = LivePreviewState(
                stateLabel = "Playing",
                detail = streamUrl,
                isPlaying = true,
                streamUrl = streamUrl,
                canStart = false,
                canStop = true,
            )
            awaitCancellation()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Log.e(TAG, "Eyevue live preview failed", error)
            updateState("Error", error.message ?: "Eyevue live preview failed", scanning = false)
        } finally {
            releasePlayer()
            eyevueManager.stopLive()
            transport.disconnect()
            notifyFinished()
        }
    }

    private fun eyeVueConnected(): Boolean = eyevueManager.isConnected()

    private suspend fun requestLiveEndpoint(baseIp: String) = withContext(Dispatchers.IO) {
        val url = "http://$baseIp/?custom=1&cmd=3001&par=1"
        CLIENT.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Eyevue live HTTP request failed: ${response.code}")
        }
    }

    @OptIn(UnstableApi::class)
    private suspend fun play(streamUrl: String) {
        val mediaSource = RtspMediaSource.Factory()
            .setForceUseRtpTcp(true)
            .createMediaSource(MediaItem.fromUri(Uri.parse(streamUrl)))
        suspendCancellableCoroutine<Unit> { continuation ->
            val exoPlayer = ExoPlayer.Builder(context).build()
            val listener = object : Player.Listener {
                private var completed = false

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (completed) return
                    if (playbackState == Player.STATE_READY) {
                        completed = true
                        exoPlayer.removeListener(this)
                        player = exoPlayer
                        playerListener = this
                        if (continuation.isActive) continuation.resume(Unit)
                    } else if (playbackState == Player.STATE_ENDED) {
                        completed = true
                        exoPlayer.release()
                        if (continuation.isActive) {
                            continuation.resumeWith(Result.failure(IOException("Eyevue RTSP stream ended")))
                        }
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    if (completed) return
                    completed = true
                    exoPlayer.release()
                    if (continuation.isActive) {
                        continuation.resumeWith(
                            Result.failure(IOException("Eyevue RTSP error: ${error.errorCodeName}", error)),
                        )
                    }
                }
            }
            exoPlayer.addListener(listener)
            continuation.invokeOnCancellation {
                exoPlayer.removeListener(listener)
                exoPlayer.release()
            }
            exoPlayer.setMediaSource(mediaSource)
            exoPlayer.playWhenReady = true
            exoPlayer.prepare()
        }
    }

    private fun releasePlayer() {
        playerListener?.let { listener -> player?.removeListener(listener) }
        playerListener = null
        player?.release()
        player = null
    }

    private fun updateState(label: String, detail: String, scanning: Boolean) {
        _uiState.value = LivePreviewState(
            stateLabel = label,
            detail = detail,
            isScanning = scanning,
            canStart = !scanning,
            canStop = scanning,
        )
    }

    private fun resetState() {
        _uiState.value = LivePreviewState()
    }

    private fun notifyFinished() {
        if (finishedNotified) return
        finishedNotified = true
        onSessionFinished()
    }
}

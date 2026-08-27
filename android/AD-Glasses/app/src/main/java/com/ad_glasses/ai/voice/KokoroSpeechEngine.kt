package com.ad_glasses.ai.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.ad_glasses.shared.voice.AssistantVoiceProfile
import com.ad_glasses.shared.voice.KokoroHeartVoice
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

enum class SpeechQueueMode {
    FLUSH,
    ADD,
}

data class SpeechCallbacks(
    val onStart: (() -> Unit)? = null,
    val onDone: (() -> Unit)? = null,
    val onStopped: (() -> Unit)? = null,
    val onError: ((Throwable) -> Unit)? = null,
)

/**
 * Process-wide, offline Kokoro speech output.
 *
 * This class has no dependency on android.speech.tts. Audio is synthesized by sherpa-onnx and
 * played directly through AudioTrack. The full voices.bin pack remains installed; each request
 * carries an [AssistantVoiceProfile], so changing speakers later does not require another speech
 * backend. FLUSH invalidates queued/in-flight results and preserves assistant barge-in playback
 * semantics even though the current native non-callback generation must return before it can be
 * discarded safely.
 */
class KokoroSpeechEngine internal constructor(context: Context) {
    private data class SpeakRequest(
        val text: String,
        val utteranceId: String,
        val epoch: Long,
        val voice: AssistantVoiceProfile,
        val speed: Float,
        val callbacks: SpeechCallbacks,
    )

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val requests = Channel<SpeakRequest>(Channel.UNLIMITED)
    private val engineMutex = Mutex()
    private val epoch = AtomicLong(0L)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val trackLock = Any()

    @Volatile
    private var currentTrack: AudioTrack? = null

    @Volatile
    private var released = false

    private var offlineTts: OfflineTts? = null

    init {
        scope.launch {
            for (request in requests) {
                if (released) break
                if (request.epoch != epoch.get()) {
                    dispatch { request.callbacks.onStopped?.invoke() }
                    continue
                }
                process(request)
            }
        }
    }

    fun prepare(
        onProgress: ((downloadedBytes: Long, totalBytes: Long) -> Unit)? = null,
        onReady: (() -> Unit)? = null,
        onError: ((Throwable) -> Unit)? = null,
    ) {
        if (released) {
            onError?.invoke(IllegalStateException("KokoroSpeechEngine has been released"))
            return
        }
        scope.launch {
            runCatching { requireEngine(onProgress) }
                .onSuccess { dispatch { onReady?.invoke() } }
                .onFailure { error -> dispatch { onError?.invoke(error) } }
        }
    }

    fun speak(
        text: String,
        queueMode: SpeechQueueMode = SpeechQueueMode.FLUSH,
        utteranceId: String = "kokoro-${UUID.randomUUID()}",
        voice: AssistantVoiceProfile = KokoroHeartVoice.profile,
        speed: Float = voice.defaultSpeed,
        callbacks: SpeechCallbacks = SpeechCallbacks(),
    ): String {
        val clean = text.trim()
        if (clean.isEmpty() || released) return utteranceId

        val requestEpoch = if (queueMode == SpeechQueueMode.FLUSH) {
            val next = epoch.incrementAndGet()
            stopCurrentTrack()
            next
        } else {
            epoch.get()
        }

        val request = SpeakRequest(
            text = clean,
            utteranceId = utteranceId,
            epoch = requestEpoch,
            voice = voice,
            speed = speed.coerceIn(0.5f, 2.0f),
            callbacks = callbacks,
        )
        if (!requests.trySend(request).isSuccess) {
            dispatch {
                callbacks.onError?.invoke(IllegalStateException("Kokoro speech queue is closed"))
            }
        }
        return utteranceId
    }

    fun stop() {
        if (released) return
        epoch.incrementAndGet()
        stopCurrentTrack()
    }

    fun isSpeaking(): Boolean = synchronized(trackLock) {
        currentTrack?.playState == AudioTrack.PLAYSTATE_PLAYING
    }

    fun isModelInstalled(): Boolean = KokoroModelInstaller.installedModel(appContext) != null

    fun release() {
        if (released) return
        released = true
        epoch.incrementAndGet()
        stopCurrentTrack()
        requests.close()
        scope.cancel()
        synchronized(this) {
            offlineTts?.release()
            offlineTts = null
        }
    }

    private suspend fun process(request: SpeakRequest) {
        try {
            val tts = requireEngine()
            check(tts.numSpeakers() > request.voice.speakerId) {
                "Kokoro model does not expose speaker ${request.voice.speakerId} (${request.voice.voiceId})"
            }
            if (request.epoch != epoch.get()) {
                dispatch { request.callbacks.onStopped?.invoke() }
                return
            }

            val generationConfig = GenerationConfig(
                silenceScale = 0.2f,
                speed = request.speed,
                sid = request.voice.speakerId,
            )

            // Do not use generateWithConfigAndCallback() on Android. sherpa-onnx's JNI callback
            // currently captures a thread-local JNIEnv/local jobject and can SIGABRT when Kokoro
            // invokes the callback from a native worker thread. The non-callback path stays entirely
            // inside JNI until generation completes and avoids that process-killing failure.
            val synthesisStartNs = System.nanoTime()
            Log.i(
                TAG,
                "stage=kokoro_synthesis_start id=${request.utteranceId} chars=${request.text.length}",
            )
            val generated = tts.generateWithConfig(
                text = request.text,
                config = generationConfig,
            )
            val synthesisMs = (System.nanoTime() - synthesisStartNs) / 1_000_000L

            if (released || request.epoch != epoch.get()) {
                dispatch { request.callbacks.onStopped?.invoke() }
                return
            }

            val samples = generated.samples
            check(samples.isNotEmpty()) { "Kokoro generated no audio samples" }
            val sampleRate = generated.sampleRate.takeIf { it > 0 }
                ?: tts.sampleRate().takeIf { it > 0 }
                ?: request.voice.sampleRateHz

            Log.i(
                TAG,
                "stage=kokoro_synthesis_done id=${request.utteranceId} elapsedMs=$synthesisMs samples=${samples.size} sampleRate=$sampleRate",
            )

            val track = createAudioTrack(sampleRate)
            synchronized(trackLock) {
                if (request.epoch != epoch.get()) {
                    track.release()
                    dispatch { request.callbacks.onStopped?.invoke() }
                    return
                }
                currentTrack = track
            }

            track.play()
            dispatch { request.callbacks.onStart?.invoke() }

            val samplesQueued = writeSamples(track, samples).toLong()
            check(samplesQueued > 0L) { "Kokoro audio track accepted no samples" }

            val completed = !released && request.epoch == epoch.get()
            if (completed) {
                awaitPlaybackDrain(
                    track = track,
                    totalSamples = samplesQueued,
                    sampleRate = sampleRate,
                    requestEpoch = request.epoch,
                )
            }
            val stillCompleted = !released && request.epoch == epoch.get()
            releaseTrackIfCurrent(track)
            if (stillCompleted) {
                dispatch { request.callbacks.onDone?.invoke() }
            } else {
                dispatch { request.callbacks.onStopped?.invoke() }
            }
        } catch (error: Throwable) {
            stopCurrentTrack()
            if (!released && request.epoch == epoch.get()) {
                dispatch { request.callbacks.onError?.invoke(error) }
            } else {
                dispatch { request.callbacks.onStopped?.invoke() }
            }
        }
    }

    private suspend fun requireEngine(
        onProgress: ((downloadedBytes: Long, totalBytes: Long) -> Unit)? = null,
    ): OfflineTts = engineMutex.withLock {
        offlineTts?.let { return@withLock it }

        val files = KokoroModelInstaller.ensureInstalled(appContext, onProgress)
        val kokoro = OfflineTtsKokoroModelConfig(
            model = files.model.absolutePath,
            voices = files.voices.absolutePath,
            tokens = files.tokens.absolutePath,
            dataDir = files.espeakData.absolutePath,
            lexicon = files.englishLexicon.absolutePath,
        )
        val model = OfflineTtsModelConfig(
            kokoro = kokoro,
            numThreads = 2,
            debug = false,
            provider = "cpu",
        )
        OfflineTts(config = OfflineTtsConfig(model = model)).also { created ->
            check(created.numSpeakers() > KokoroHeartVoice.SPEAKER_ID) {
                "Kokoro model does not expose default speaker ${KokoroHeartVoice.SPEAKER_ID} (${KokoroHeartVoice.VOICE_ID})"
            }
            offlineTts = created
        }
    }

    private fun createAudioTrack(sampleRate: Int): AudioTrack {
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        val usage = playbackUsage()
        val attributes = AudioAttributes.Builder()
            .setUsage(usage)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val minimum = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
        )
        val bufferBytes = max(minimum, sampleRate / 2 * Float.SIZE_BYTES)

        Log.i(
            TAG,
            "stage=kokoro_audio_track sampleRate=$sampleRate usage=${if (usage == AudioAttributes.USAGE_VOICE_COMMUNICATION) "communication" else "assistant"}",
        )

        return AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(format)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(bufferBytes)
            .build()
    }

    @Suppress("DEPRECATION")
    private fun playbackUsage(): Int {
        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return AudioAttributes.USAGE_ASSISTANT

        val bluetoothCommunicationRoute = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            when (audioManager.communicationDevice?.type) {
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_BLE_HEADSET,
                -> true
                else -> false
            }
        } else {
            audioManager.isBluetoothScoOn
        }

        return if (bluetoothCommunicationRoute) {
            AudioAttributes.USAGE_VOICE_COMMUNICATION
        } else {
            AudioAttributes.USAGE_ASSISTANT
        }
    }

    private fun writeSamples(track: AudioTrack, samples: FloatArray): Int {
        var offset = 0
        while (offset < samples.size && track.playState == AudioTrack.PLAYSTATE_PLAYING) {
            val written = track.write(
                samples,
                offset,
                samples.size - offset,
                AudioTrack.WRITE_BLOCKING,
            )
            if (written <= 0) break
            offset += written
        }
        return offset
    }

    private suspend fun awaitPlaybackDrain(
        track: AudioTrack,
        totalSamples: Long,
        sampleRate: Int,
        requestEpoch: Long,
    ) {
        if (totalSamples <= 0L) return
        val expectedDurationMs = totalSamples * 1_000L / sampleRate.coerceAtLeast(1)
        val deadlineNs = System.nanoTime() + (expectedDurationMs + 2_000L) * 1_000_000L
        while (!released && requestEpoch == epoch.get() && System.nanoTime() < deadlineNs) {
            val playedSamples = track.playbackHeadPosition.toLong() and 0xffff_ffffL
            if (playedSamples >= totalSamples) return
            delay(10)
        }
    }

    private fun stopCurrentTrack() {
        val track = synchronized(trackLock) {
            currentTrack.also { currentTrack = null }
        } ?: return
        runCatching { track.pause() }
        runCatching { track.flush() }
        runCatching { track.stop() }
        runCatching { track.release() }
    }

    private fun releaseTrackIfCurrent(track: AudioTrack) {
        val shouldRelease = synchronized(trackLock) {
            if (currentTrack === track) {
                currentTrack = null
                true
            } else {
                false
            }
        }
        if (!shouldRelease) return
        runCatching { track.stop() }
        runCatching { track.release() }
    }

    private fun dispatch(block: () -> Unit) {
        mainHandler.post(block)
    }

    private companion object {
        const val TAG = "AssistantTiming"
    }
}

/** One model/runtime per app process; callers share queueing and interruption state. */
object KokoroSpeechService {
    @Volatile
    private var engine: KokoroSpeechEngine? = null

    fun get(context: Context): KokoroSpeechEngine {
        return engine ?: synchronized(this) {
            engine ?: KokoroSpeechEngine(context.applicationContext).also { engine = it }
        }
    }

    fun release() {
        synchronized(this) {
            engine?.release()
            engine = null
        }
    }
}

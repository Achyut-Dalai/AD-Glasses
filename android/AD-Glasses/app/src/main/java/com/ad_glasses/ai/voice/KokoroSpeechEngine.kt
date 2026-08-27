package com.ad_glasses.ai.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
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

/** Queue behavior intentionally mirrors the two Android TextToSpeech modes used by AD Glasses. */
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
 * streamed directly to AudioTrack. FLUSH invalidates queued/in-flight generations, which preserves
 * assistant barge-in semantics without keeping a platform TTS engine alive.
 */
class KokoroSpeechEngine internal constructor(context: Context) {
    private data class SpeakRequest(
        val text: String,
        val utteranceId: String,
        val epoch: Long,
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
        speed: Float = KokoroHeartVoice.DEFAULT_SPEED,
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
            if (request.epoch != epoch.get()) {
                dispatch { request.callbacks.onStopped?.invoke() }
                return
            }

            val sampleRate = tts.sampleRate().takeIf { it > 0 } ?: KokoroHeartVoice.SAMPLE_RATE_HZ
            val track = createAudioTrack(sampleRate)
            synchronized(trackLock) {
                if (request.epoch != epoch.get()) {
                    track.release()
                    dispatch { request.callbacks.onStopped?.invoke() }
                    return
                }
                currentTrack = track
            }

            dispatch { request.callbacks.onStart?.invoke() }
            track.play()

            val generationConfig = GenerationConfig(
                silenceScale = 0.2f,
                speed = request.speed,
                sid = KokoroHeartVoice.SPEAKER_ID,
            )
            var samplesQueued = 0L

            tts.generateWithConfigAndCallback(
                text = request.text,
                config = generationConfig,
            ) { samples ->
                if (released || request.epoch != epoch.get()) {
                    0
                } else {
                    if (samples.isNotEmpty()) {
                        samplesQueued += writeSamples(track, samples)
                    }
                    if (released || request.epoch != epoch.get()) 0 else 1
                }
            }

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
                "Kokoro model does not expose speaker ${KokoroHeartVoice.SPEAKER_ID} (${KokoroHeartVoice.VOICE_ID})"
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
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val minimum = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
        )
        val bufferBytes = max(minimum, sampleRate / 2 * Float.SIZE_BYTES)

        return AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(format)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(bufferBytes)
            .build()
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

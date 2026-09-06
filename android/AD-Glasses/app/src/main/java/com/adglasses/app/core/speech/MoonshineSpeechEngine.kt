package com.adglasses.app.core.speech

import android.content.Context
import ai.moonshine.voice.AssetDownloader
import ai.moonshine.voice.JNI
import ai.moonshine.voice.ModelCache
import ai.moonshine.voice.ModelSpec
import ai.moonshine.voice.Transcriber
import ai.moonshine.voice.TranscriptEvent
import ai.moonshine.voice.TranscriptLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

sealed interface LocalSpeechModelState {
    data object NotLoaded : LocalSpeechModelState
    data class Loading(val detail: String = "Preparing on-device speech") : LocalSpeechModelState
    data object Ready : LocalSpeechModelState
    data class Failed(val reason: String) : LocalSpeechModelState
}

/**
 * Shared on-device ASR engine for AD voice turns.
 *
 * We deliberately feed PCM ourselves instead of tying recognition to Android's microphone API.
 * That lets the exact same Moonshine model transcribe the glasses microphone (after Opus decode),
 * while a phone-microphone capture path can feed the same engine later without changing the
 * Assistant or command router.
 */
class MoonshineSpeechEngine(context: Context) {
    companion object {
        const val SAMPLE_RATE = 16_000
        private const val LANGUAGE = "en"
        private const val MODEL_ARCH = JNI.MOONSHINE_MODEL_ARCH_SMALL_STREAMING
        private const val UPDATE_INTERVAL_SECONDS = 0.35
    }

    private val appContext = context.applicationContext
    private val loadMutex = Mutex()
    private val sessionLock = Any()
    private val lines = ConcurrentHashMap<Long, LineSnapshot>()

    @Volatile private var transcriber: Transcriber? = null
    @Volatile private var sessionActive = false
    @Volatile private var recognitionError: Throwable? = null

    private val _state = MutableStateFlow<LocalSpeechModelState>(LocalSpeechModelState.NotLoaded)
    val state: StateFlow<LocalSpeechModelState> = _state.asStateFlow()

    suspend fun prewarm() {
        runCatching { ensureReady() }
    }

    suspend fun startSession() {
        val engine = ensureReady()
        synchronized(sessionLock) {
            if (sessionActive) runCatching { engine.stop() }
            lines.clear()
            recognitionError = null
            engine.start()
            sessionActive = true
        }
    }

    /** Moonshine documents addAudio as a cheap streaming operation. */
    fun addPcm16(samples: ShortArray) {
        if (samples.isEmpty()) return
        val engine = transcriber ?: return
        synchronized(sessionLock) {
            if (!sessionActive) return
            val floatPcm = FloatArray(samples.size) { index -> samples[index] / 32768.0f }
            engine.addAudio(floatPcm, SAMPLE_RATE)
        }
    }

    suspend fun finishSession(): String = withContext(Dispatchers.Default) {
        val engine = transcriber ?: return@withContext ""
        synchronized(sessionLock) {
            if (sessionActive) {
                engine.stop() // forces one final transcription pass for trailing audio
                sessionActive = false
            }
        }
        recognitionError?.let { throw IllegalStateException("On-device transcription failed", it) }
        lines.values
            .sortedWith(compareBy<LineSnapshot> { it.startTime }.thenBy { it.id })
            .map { it.text.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString(" ")
            .trim()
    }

    fun cancelSession() {
        val engine = transcriber ?: return
        synchronized(sessionLock) {
            if (sessionActive) runCatching { engine.stop() }
            sessionActive = false
            lines.clear()
            recognitionError = null
        }
    }

    private suspend fun ensureReady(): Transcriber {
        transcriber?.let { return it }
        return loadMutex.withLock {
            transcriber?.let { return@withLock it }
            _state.value = LocalSpeechModelState.Loading()
            try {
                val loaded = withContext(Dispatchers.IO) {
                    val spec = ModelSpec.stt(LANGUAGE, MODEL_ARCH, false)
                    val directory = ModelCache.directoryFor(appContext, spec, null)
                    AssetDownloader().ensureModelPresent(directory, spec, null)
                    Transcriber().apply {
                        setUpdateInterval(UPDATE_INTERVAL_SECONDS)
                        addListener(::consumeEvent)
                        loadFromFiles(directory.absolutePath, MODEL_ARCH)
                    }
                }
                transcriber = loaded
                _state.value = LocalSpeechModelState.Ready
                loaded
            } catch (error: Throwable) {
                _state.value = LocalSpeechModelState.Failed(
                    error.message ?: "Could not load the on-device speech model"
                )
                throw error
            }
        }
    }

    private fun consumeEvent(event: TranscriptEvent) {
        when (event) {
            is TranscriptEvent.LineStarted -> consumeLine(event.line)
            is TranscriptEvent.LineUpdated -> consumeLine(event.line)
            is TranscriptEvent.LineTextChanged -> consumeLine(event.line)
            is TranscriptEvent.LineSpeakersChanged -> consumeLine(event.line)
            is TranscriptEvent.LineCompleted -> consumeLine(event.line)
            is TranscriptEvent.Error -> recognitionError = event.cause
            else -> Unit
        }
    }

    private fun consumeLine(line: TranscriptLine) {
        val text = line.text?.trim().orEmpty()
        if (text.isBlank()) return
        lines[line.id] = LineSnapshot(
            id = line.id,
            startTime = line.startTime,
            text = text,
        )
    }

    private data class LineSnapshot(
        val id: Long,
        val startTime: Float,
        val text: String,
    )
}

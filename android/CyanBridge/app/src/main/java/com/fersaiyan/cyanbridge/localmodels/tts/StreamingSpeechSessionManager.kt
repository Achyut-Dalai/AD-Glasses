package com.fersaiyan.cyanbridge.localmodels.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import java.util.concurrent.atomic.AtomicLong

/**
 * Top-level session controller that coordinates local model streaming,
 * multilingual phrase chunking, speech sanitization, and TTS queue playback.
 */
class StreamingSpeechSessionManager(
    private val context: Context,
    val config: SpeechChunkingConfig = SpeechChunkingConfig(),
) {
    companion object {
        private const val TAG = "StreamingSpeechSessionManager"
        
        @Volatile
        private var instance: StreamingSpeechSessionManager? = null

        fun getInstance(context: Context): StreamingSpeechSessionManager {
            return instance ?: synchronized(this) {
                instance ?: StreamingSpeechSessionManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sessionIdCounter = AtomicLong(1L)

    @Volatile
    var activeSessionId: Long = 0L
        private set

    @Volatile
    var activeGenerationJob: Job? = null

    val speechQueueController = SpeechQueueController(context, config)

    val chunker = MultilingualSpeechChunker(
        config = config,
        scope = sessionScope,
        onChunkReady = { phraseChunk ->
            onPhraseChunkReady(phraseChunk)
        },
    )

    @Volatile
    private var currentLanguageTag: String? = null

    fun attachTtsEngine(tts: TextToSpeech?) {
        speechQueueController.attachTtsEngine(tts)
    }

    @Synchronized
    fun startNewSession(
        languageTag: String? = null,
        generationJob: Job? = null,
    ): Long {
        cancelActiveStreamingResponse()

        val newSessionId = sessionIdCounter.getAndIncrement()
        this.activeSessionId = newSessionId
        this.activeGenerationJob = generationJob
        this.currentLanguageTag = languageTag

        chunker.startSession(newSessionId)
        speechQueueController.startSession(newSessionId)

        Log.i(TAG, "Started new streaming speech session id=$newSessionId lang=$languageTag")
        return newSessionId
    }

    @Synchronized
    fun cancelActiveStreamingResponse() {
        if (activeSessionId == 0L) return
        Log.i(TAG, "Cancelling active streaming speech session id=$activeSessionId")
        
        val job = activeGenerationJob
        activeGenerationJob = null
        job?.cancel()

        chunker.reset()
        speechQueueController.cancelSession()
    }

    fun onModelTokenDelta(deltaText: String, sessionId: Long) {
        if (sessionId != activeSessionId) {
            speechQueueController.metrics.staleCallbacksDiscarded++
            return
        }

        if (speechQueueController.metrics.firstModelFragmentAtMs == 0L) {
            speechQueueController.metrics.firstModelFragmentAtMs = System.currentTimeMillis()
        }
        speechQueueController.metrics.totalModelFragments++

        chunker.append(deltaText, sessionId)
    }

    fun onModelGenerationCompleted(sessionId: Long) {
        if (sessionId != activeSessionId) return
        Log.i(TAG, "Model generation completed for session id=$sessionId")
        speechQueueController.metrics.generationCompletedAtMs = System.currentTimeMillis()
        chunker.finish(sessionId)
    }

    private fun onPhraseChunkReady(phraseChunkText: String) {
        val currentSession = activeSessionId
        if (currentSession == 0L) return
        speechQueueController.enqueueChunk(
            sessionId = currentSession,
            rawChunkText = phraseChunkText,
            languageTag = currentLanguageTag,
        )
    }
}

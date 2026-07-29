package com.fersaiyan.cyanbridge.localmodels.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * Session-aware application-level queue controller for Android TextToSpeech.
 * Prevents over-queuing TTS utterances, tracks performance metrics (TTFT, TTFA),
 * and discards callbacks from stale or cancelled session IDs.
 */
class SpeechQueueController(
    private val context: Context,
    private val config: SpeechChunkingConfig = SpeechChunkingConfig(),
) {
    companion object {
        private const val TAG = "SpeechQueueController"
    }

    data class QueuedSpeechItem(
        val sessionId: Long,
        val sequence: Int,
        val rawText: String,
        val normalizedSpeechText: String,
        val languageTag: String?,
    )

    data class PerformanceMetrics(
        var requestStartedAtMs: Long = 0L,
        var firstModelFragmentAtMs: Long = 0L,
        var firstChunkReadyAtMs: Long = 0L,
        var firstTtsSubmittedAtMs: Long = 0L,
        var firstTtsStartedAtMs: Long = 0L,
        var generationCompletedAtMs: Long = 0L,
        var finalTtsCompletedAtMs: Long = 0L,
        var totalModelFragments: Int = 0,
        var totalSpokenChunks: Int = 0,
        var cancelledCount: Int = 0,
        var staleCallbacksDiscarded: Int = 0,
    ) {
        val ttftMs: Long get() = (firstModelFragmentAtMs - requestStartedAtMs).coerceAtLeast(0L)
        val ttfaMs: Long get() = (firstTtsStartedAtMs - requestStartedAtMs).coerceAtLeast(0L)
    }

    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    @Volatile
    private var activeSessionId: Long = 0L
    private val sequenceCounter = AtomicInteger(0)

    private val pendingQueue = ConcurrentLinkedQueue<QueuedSpeechItem>()
    private val currentlySpeakingItem = AtomicInteger(0) // Count of active speaking/submitted items

    val metrics = PerformanceMetrics()

    fun attachTtsEngine(ttsEngine: TextToSpeech?) {
        this.tts = ttsEngine
        this.isTtsReady = ttsEngine != null
        setupUtteranceListener()
    }

    @Synchronized
    fun startSession(sessionId: Long) {
        clearQueue()
        this.activeSessionId = sessionId
        this.sequenceCounter.set(0)
        this.currentlySpeakingItem.set(0)
        this.metrics.requestStartedAtMs = System.currentTimeMillis()
        Log.i(TAG, "Speech queue started session $sessionId")
    }

    @Synchronized
    fun cancelSession() {
        metrics.cancelledCount++
        this.activeSessionId++
        clearQueue()
        runCatching { tts?.stop() }
        Log.i(TAG, "Speech queue cancelled active session")
    }

    fun enqueueChunk(
        sessionId: Long,
        rawChunkText: String,
        languageTag: String? = null,
    ) {
        if (sessionId != activeSessionId) {
            metrics.staleCallbacksDiscarded++
            return
        }

        val normalized = StreamingTextNormalizer.normalizeForSpeech(rawChunkText, languageTag)
        if (normalized.isBlank()) return

        val seq = sequenceCounter.getAndIncrement()
        val item = QueuedSpeechItem(
            sessionId = sessionId,
            sequence = seq,
            rawText = rawChunkText,
            normalizedSpeechText = normalized,
            languageTag = languageTag,
        )

        if (metrics.firstChunkReadyAtMs == 0L) {
            metrics.firstChunkReadyAtMs = System.currentTimeMillis()
        }

        pendingQueue.add(item)
        metrics.totalSpokenChunks++
        processNextInQueue()
    }

    @Synchronized
    private fun processNextInQueue() {
        val ttsEngine = tts ?: return
        if (!isTtsReady) return

        while (currentlySpeakingItem.get() < config.maxPendingTtsChunks) {
            val item = pendingQueue.poll() ?: break
            if (item.sessionId != activeSessionId) {
                metrics.staleCallbacksDiscarded++
                continue
            }

            val utteranceId = "local_${item.sessionId}_${item.sequence}"

            item.languageTag?.takeIf { it.isNotBlank() }?.let { tag ->
                runCatching {
                    val loc = Locale.forLanguageTag(tag)
                    val res = ttsEngine.setLanguage(loc)
                    if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.w(TAG, "TTS voice data missing for language $tag")
                    }
                }
            }

            val bundle = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            }

            val queueMode = if (item.sequence == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            val result = ttsEngine.speak(item.normalizedSpeechText, queueMode, bundle, utteranceId)

            if (result == TextToSpeech.SUCCESS) {
                currentlySpeakingItem.incrementAndGet()
                if (metrics.firstTtsSubmittedAtMs == 0L) {
                    metrics.firstTtsSubmittedAtMs = System.currentTimeMillis()
                }
                Log.i(TAG, "Submitted utterance $utteranceId to TTS (mode=$queueMode)")
            } else {
                Log.e(TAG, "Failed to submit utterance $utteranceId to TTS")
            }
        }
    }

    private fun setupUtteranceListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                if (!isCurrentSessionUtterance(utteranceId)) return
                if (metrics.firstTtsStartedAtMs == 0L) {
                    metrics.firstTtsStartedAtMs = System.currentTimeMillis()
                    Log.i(TAG, "Audible speech started! TTFA=${metrics.ttfaMs}ms")
                }
            }

            override fun onDone(utteranceId: String?) {
                if (!isCurrentSessionUtterance(utteranceId)) return
                currentlySpeakingItem.decrementAndGet()
                metrics.finalTtsCompletedAtMs = System.currentTimeMillis()
                processNextInQueue()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (!isCurrentSessionUtterance(utteranceId)) return
                currentlySpeakingItem.decrementAndGet()
                processNextInQueue()
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                if (!isCurrentSessionUtterance(utteranceId)) return
                Log.e(TAG, "TTS utterance error code=$errorCode id=$utteranceId")
                currentlySpeakingItem.decrementAndGet()
                processNextInQueue()
            }
        })
    }

    private fun isCurrentSessionUtterance(utteranceId: String?): Boolean {
        if (utteranceId == null || !utteranceId.startsWith("local_")) return false
        val parts = utteranceId.split("_")
        if (parts.size >= 2) {
            val session = parts[1].toLongOrNull() ?: return false
            if (session != activeSessionId) {
                metrics.staleCallbacksDiscarded++
                return false
            }
        }
        return true
    }

    @Synchronized
    private fun clearQueue() {
        pendingQueue.clear()
        currentlySpeakingItem.set(0)
    }
}

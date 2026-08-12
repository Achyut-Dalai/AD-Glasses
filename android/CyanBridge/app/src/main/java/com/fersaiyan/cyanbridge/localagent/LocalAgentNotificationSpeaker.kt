package com.achyut.adglasses.localagent

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Process-scoped TTS for explicitly enabled notification announcements. */
object LocalAgentNotificationSpeaker {
    private const val MAX_ANNOUNCEMENT_CHARS = 600

    private val lock = Any()
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pendingText: String? = null

    fun speak(context: Context, text: String) {
        val clean = text.trim().take(MAX_ANNOUNCEMENT_CHARS)
        if (clean.isBlank()) return

        synchronized(lock) {
            val existing = tts
            if (existing != null && ready) {
                existing.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "local_agent_notification")
                return
            }

            pendingText = clean
            if (existing != null) return

            tts = TextToSpeech(context.applicationContext) { status ->
                val initialization = synchronized(lock) {
                    val initialized = status == TextToSpeech.SUCCESS
                    ready = initialized
                    if (initialized) tts?.language = Locale.US
                    val pending = pendingText.also { pendingText = null }
                    val failedEngine = if (initialized) null else tts.also { tts = null }
                    InitializationResult(initialized, pending, failedEngine)
                }
                initialization.failedEngine?.shutdown()
                if (initialization.ready && !initialization.pendingText.isNullOrBlank()) {
                    tts?.speak(
                        initialization.pendingText,
                        TextToSpeech.QUEUE_FLUSH,
                        null,
                        "local_agent_notification",
                    )
                }
            }
        }
    }

    fun stop() {
        val engine = synchronized(lock) {
            pendingText = null
            ready = false
            tts.also { tts = null }
        }
        engine?.stop()
        engine?.shutdown()
    }

    private data class InitializationResult(
        val ready: Boolean,
        val pendingText: String?,
        val failedEngine: TextToSpeech?,
    )
}

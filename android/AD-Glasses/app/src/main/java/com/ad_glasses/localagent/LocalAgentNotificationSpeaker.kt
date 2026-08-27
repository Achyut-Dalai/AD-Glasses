package com.ad_glasses.localagent

import android.content.Context
import com.ad_glasses.ai.voice.KokoroSpeechService
import com.ad_glasses.ai.voice.SpeechQueueMode

/** Process-scoped Kokoro speech for explicitly enabled notification announcements. */
object LocalAgentNotificationSpeaker {
    private const val MAX_ANNOUNCEMENT_CHARS = 600

    fun speak(context: Context, text: String) {
        val clean = text.trim().take(MAX_ANNOUNCEMENT_CHARS)
        if (clean.isBlank()) return

        KokoroSpeechService.get(context).speak(
            text = clean,
            queueMode = SpeechQueueMode.FLUSH,
            utteranceId = "local_agent_notification",
        )
    }

    fun stop(context: Context? = null) {
        if (context != null) {
            KokoroSpeechService.get(context).stop()
        }
    }
}

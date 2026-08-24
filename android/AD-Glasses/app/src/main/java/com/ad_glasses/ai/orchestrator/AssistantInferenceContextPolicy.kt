package com.ad_glasses.ai.orchestrator

import com.ad_glasses.shared.chat.ChatMessage

/** Keeps Cloud AI context small while durable ChatStore history remains untouched. */
object AssistantInferenceContextPolicy {
    const val MAX_PRIOR_MESSAGES = 3

    /**
     * ChatStore/UI history never expires. This window only decides whether prior messages are sent
     * back to the model for a spoken follow-up. It starts when the prior assistant turn is persisted,
     * which is before TTS finishes; 30 seconds therefore approximates roughly 10-15 seconds of
     * follow-up time after a normal short wearable answer has finished speaking.
     */
    const val SPOKEN_FOLLOW_UP_TTL_MS = 30_000L

    @Deprecated("Use SPOKEN_FOLLOW_UP_TTL_MS.")
    const val VOICE_INACTIVITY_TTL_MS = SPOKEN_FOLLOW_UP_TTL_MS

    @Deprecated("Use MAX_PRIOR_MESSAGES.")
    const val MAX_VOICE_PRIOR_MESSAGES = MAX_PRIOR_MESSAGES

    @Deprecated("Use MAX_PRIOR_MESSAGES.")
    const val MAX_TEXT_PRIOR_MESSAGES = MAX_PRIOR_MESSAGES

    const val MAX_MESSAGE_CHARS = 900
    const val MAX_ARTIFACT_CHARS = 6_000
    const val MAX_GLASS_ARTIFACT_CHARS = 3_000

    /**
     * [history] includes the user message for the turn currently being handled. Spoken surfaces get
     * the last three prior messages only during an active follow-up window. A longer pause gives the
     * model a blank inference slate while the same durable ChatStore thread remains visible in UI.
     * Phone text/automation keep the existing durable last-three behavior.
     */
    fun priorMessages(
        history: List<ChatMessage>,
        surface: AssistantInputSurface = AssistantInputSurface.GLASSES_VOICE,
        nowMs: Long = System.currentTimeMillis(),
    ): List<ChatMessage> {
        if (history.size <= 1) return emptyList()
        val prior = history.dropLast(1)

        if (surface.usesTimeGatedSpokenContext()) {
            val lastPriorAtMs = prior.lastOrNull()?.createdAt ?: return emptyList()
            val inactiveMs = (nowMs - lastPriorAtMs).coerceAtLeast(0L)
            if (inactiveMs > SPOKEN_FOLLOW_UP_TTL_MS) return emptyList()
        }

        return prior.takeLast(MAX_PRIOR_MESSAGES)
    }

    private fun AssistantInputSurface.usesTimeGatedSpokenContext(): Boolean = when (this) {
        AssistantInputSurface.GLASSES_VOICE,
        AssistantInputSurface.GLASSES_VISION,
        AssistantInputSurface.PHONE_VOICE -> true
        AssistantInputSurface.PHONE_TEXT,
        AssistantInputSurface.AUTOMATION -> false
    }

    fun artifactLimit(surface: AssistantInputSurface): Int = when (surface) {
        AssistantInputSurface.GLASSES_VOICE,
        AssistantInputSurface.GLASSES_VISION,
        AssistantInputSurface.PHONE_VOICE -> MAX_GLASS_ARTIFACT_CHARS
        AssistantInputSurface.PHONE_TEXT,
        AssistantInputSurface.AUTOMATION -> MAX_ARTIFACT_CHARS
    }
}

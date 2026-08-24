package com.ad_glasses.ai.orchestrator

import com.ad_glasses.shared.chat.ChatMessage

/** Keeps Cloud AI context small while durable ChatStore history remains untouched. */
object AssistantInferenceContextPolicy {
    const val MAX_PRIOR_MESSAGES = 3
    const val MAX_MESSAGE_CHARS = 900
    const val MAX_ARTIFACT_CHARS = 6_000
    const val MAX_GLASS_ARTIFACT_CHARS = 3_000

    /**
     * [history] includes the user message for the turn currently being handled.
     * Multi-turn inference is intentionally simple: send only the last three prior messages.
     * Surface and time are retained in the signature for source compatibility, but no longer alter
     * conversation continuity.
     */
    @Suppress("UNUSED_PARAMETER")
    fun priorMessages(
        history: List<ChatMessage>,
        surface: AssistantInputSurface = AssistantInputSurface.GLASSES_VOICE,
        nowMs: Long = System.currentTimeMillis(),
    ): List<ChatMessage> {
        if (history.size <= 1) return emptyList()
        return history.dropLast(1).takeLast(MAX_PRIOR_MESSAGES)
    }

    fun artifactLimit(surface: AssistantInputSurface): Int = when (surface) {
        AssistantInputSurface.GLASSES_VOICE,
        AssistantInputSurface.GLASSES_VISION,
        AssistantInputSurface.PHONE_VOICE -> MAX_GLASS_ARTIFACT_CHARS
        AssistantInputSurface.PHONE_TEXT,
        AssistantInputSurface.AUTOMATION -> MAX_ARTIFACT_CHARS
    }
}

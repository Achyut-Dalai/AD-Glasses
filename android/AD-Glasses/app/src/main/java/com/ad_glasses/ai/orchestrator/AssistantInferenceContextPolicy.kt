package com.ad_glasses.ai.orchestrator

import com.ad_glasses.shared.chat.ChatMessage

/**
 * Keeps inference context intentionally short for a voice-first wearable without deleting durable
 * ChatStore history. A conversation can remain visible for days while only the current conversational
 * bubble is sent back to the model.
 */
object AssistantInferenceContextPolicy {
    const val INACTIVITY_TTL_MS = 45_000L
    const val MAX_PRIOR_MESSAGES = 4
    const val MAX_MESSAGE_CHARS = 900
    const val MAX_ARTIFACT_CHARS = 6_000
    const val MAX_GLASS_ARTIFACT_CHARS = 3_000

    /**
     * [history] includes the user message for the turn currently being handled. Return only prior
     * messages that still belong to the active micro-session.
     */
    fun priorMessages(
        history: List<ChatMessage>,
        nowMs: Long = System.currentTimeMillis(),
    ): List<ChatMessage> {
        if (history.size <= 1) return emptyList()
        val prior = history.dropLast(1)
        val lastPrior = prior.lastOrNull() ?: return emptyList()
        if (nowMs - lastPrior.createdAt > INACTIVITY_TTL_MS) return emptyList()

        val selected = ArrayDeque<ChatMessage>()
        var newerTimestamp = history.last().createdAt
        for (message in prior.asReversed()) {
            if (selected.size >= MAX_PRIOR_MESSAGES) break
            if (newerTimestamp - message.createdAt > INACTIVITY_TTL_MS) break
            selected.addFirst(message)
            newerTimestamp = message.createdAt
        }
        return selected.toList()
    }

    fun artifactLimit(surface: AssistantInputSurface): Int = when (surface) {
        AssistantInputSurface.GLASSES_VOICE,
        AssistantInputSurface.GLASSES_VISION,
        AssistantInputSurface.PHONE_VOICE -> MAX_GLASS_ARTIFACT_CHARS
        AssistantInputSurface.PHONE_TEXT,
        AssistantInputSurface.AUTOMATION -> MAX_ARTIFACT_CHARS
    }
}

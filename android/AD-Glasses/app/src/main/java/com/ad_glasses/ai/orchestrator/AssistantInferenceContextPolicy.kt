package com.ad_glasses.ai.orchestrator

import com.ad_glasses.shared.chat.ChatMessage
import com.ad_glasses.shared.chat.ChatRole

/**
 * Keeps inference context intentionally short for a voice-first wearable without deleting durable
 * ChatStore history. A conversation can remain visible indefinitely while only the current
 * conversational bubble is sent back to the model.
 */
object AssistantInferenceContextPolicy {
    const val INACTIVITY_TTL_MS = 45_000L
    const val MAX_PRIOR_MESSAGES = 3
    const val MAX_MESSAGE_CHARS = 900
    const val MAX_ARTIFACT_CHARS = 6_000
    const val MAX_GLASS_ARTIFACT_CHARS = 3_000

    /**
     * [history] includes the user message for the turn currently being handled. Return only prior
     * messages that still belong to the active micro-session.
     *
     * The inactivity boundary is measured when a user message follows older conversation activity.
     * A slow model response must not itself expire context: user -> assistant latency can exceed the
     * TTL without representing user inactivity. Conversely, a long assistant -> next-user pause does
     * start a fresh inference bubble while keeping the durable chat untouched.
     */
    fun priorMessages(
        history: List<ChatMessage>,
        nowMs: Long = System.currentTimeMillis(),
    ): List<ChatMessage> {
        if (history.size <= 1) return emptyList()
        val current = history.last()
        val prior = history.dropLast(1)

        // Keep the wall clock guard for malformed/future current-message timestamps, but base the
        // conversational boundary on message-to-message user inactivity below.
        if (current.createdAt <= nowMs && nowMs - current.createdAt > INACTIVITY_TTL_MS) return emptyList()

        val selected = ArrayDeque<ChatMessage>()
        var newer = current
        for (message in prior.asReversed()) {
            if (selected.size >= MAX_PRIOR_MESSAGES) break
            val gapMs = newer.createdAt - message.createdAt
            if (newer.role == ChatRole.USER && gapMs > INACTIVITY_TTL_MS) break
            selected.addFirst(message)
            newer = message
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

package com.ad_glasses.ai.orchestrator

import com.ad_glasses.shared.chat.ChatMessage
import com.ad_glasses.shared.chat.ChatRole

/**
 * Keeps inference context bounded without deleting durable ChatStore history.
 *
 * Voice surfaces use a short inactivity window because a wearable interaction is naturally
 * ephemeral. Phone text is an explicit chat thread, so it keeps a larger recent window even when
 * the user pauses for more than a few seconds.
 */
object AssistantInferenceContextPolicy {
    const val VOICE_INACTIVITY_TTL_MS = 45_000L
    const val MAX_VOICE_PRIOR_MESSAGES = 3
    const val MAX_TEXT_PRIOR_MESSAGES = 8
    const val MAX_MESSAGE_CHARS = 900
    const val MAX_ARTIFACT_CHARS = 6_000
    const val MAX_GLASS_ARTIFACT_CHARS = 3_000

    /**
     * [history] includes the user message for the turn currently being handled. Return only prior
     * messages appropriate for [surface].
     *
     * Voice keeps the existing micro-session behavior: a long assistant -> next-user pause starts a
     * fresh inference bubble, while slow user -> assistant generation does not expire the exchange.
     * Phone text/automation are durable thread surfaces, so elapsed wall-clock time alone does not
     * erase their recent context.
     */
    fun priorMessages(
        history: List<ChatMessage>,
        surface: AssistantInputSurface = AssistantInputSurface.GLASSES_VOICE,
        nowMs: Long = System.currentTimeMillis(),
    ): List<ChatMessage> {
        if (history.size <= 1) return emptyList()
        val current = history.last()
        val prior = history.dropLast(1)
        val enforceInactivity = usesVoiceInactivityBoundary(surface)

        if (
            enforceInactivity &&
            current.createdAt <= nowMs &&
            nowMs - current.createdAt > VOICE_INACTIVITY_TTL_MS
        ) {
            return emptyList()
        }

        val selected = ArrayDeque<ChatMessage>()
        var newer = current
        val maxPrior = maxPriorMessages(surface)
        for (message in prior.asReversed()) {
            if (selected.size >= maxPrior) break
            val gapMs = newer.createdAt - message.createdAt
            if (
                enforceInactivity &&
                newer.role == ChatRole.USER &&
                gapMs > VOICE_INACTIVITY_TTL_MS
            ) {
                break
            }
            selected.addFirst(message)
            newer = message
        }
        return selected.toList()
    }

    fun maxPriorMessages(surface: AssistantInputSurface): Int = when (surface) {
        AssistantInputSurface.PHONE_TEXT,
        AssistantInputSurface.AUTOMATION -> MAX_TEXT_PRIOR_MESSAGES
        AssistantInputSurface.GLASSES_VOICE,
        AssistantInputSurface.GLASSES_VISION,
        AssistantInputSurface.PHONE_VOICE -> MAX_VOICE_PRIOR_MESSAGES
    }

    fun artifactLimit(surface: AssistantInputSurface): Int = when (surface) {
        AssistantInputSurface.GLASSES_VOICE,
        AssistantInputSurface.GLASSES_VISION,
        AssistantInputSurface.PHONE_VOICE -> MAX_GLASS_ARTIFACT_CHARS
        AssistantInputSurface.PHONE_TEXT,
        AssistantInputSurface.AUTOMATION -> MAX_ARTIFACT_CHARS
    }

    private fun usesVoiceInactivityBoundary(surface: AssistantInputSurface): Boolean = when (surface) {
        AssistantInputSurface.GLASSES_VOICE,
        AssistantInputSurface.GLASSES_VISION,
        AssistantInputSurface.PHONE_VOICE -> true
        AssistantInputSurface.PHONE_TEXT,
        AssistantInputSurface.AUTOMATION -> false
    }
}

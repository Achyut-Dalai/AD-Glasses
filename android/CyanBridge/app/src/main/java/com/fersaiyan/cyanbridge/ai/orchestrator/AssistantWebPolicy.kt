package com.fersaiyan.cyanbridge.ai.orchestrator

import com.fersaiyan.cyanbridge.shared.chat.ChatMessage
import com.fersaiyan.cyanbridge.shared.chat.ChatRole

/**
 * Decides whether a turn should prefer fresh web-grounded information.
 *
 * Explicit user/UI preference always wins. Automatic policy handles clearly live requests
 * and short contextual follow-ups such as "how much is it?" after AD identified an object.
 */
object AssistantWebPolicy {
    fun shouldUseWeb(
        text: String,
        requested: Boolean? = null,
        history: List<ChatMessage> = emptyList(),
    ): Boolean {
        requested?.let { return it }
        val normalized = text.trim().lowercase()
        if (normalized.isBlank()) return false

        if (EXPLICIT_WEB.containsMatchIn(normalized)) return true
        if (FRESHNESS.containsMatchIn(normalized)) return true
        if (LIVE_LOOKUP.containsMatchIn(normalized)) return true
        if (CONTEXTUAL_COMMERCE.containsMatchIn(normalized) && hasUsefulPriorContext(history)) return true

        return false
    }

    private fun hasUsefulPriorContext(history: List<ChatMessage>): Boolean = history
        .asReversed()
        .take(8)
        .any { message ->
            message.role == ChatRole.ASSISTANT &&
                message.content.trim().length >= MIN_CONTEXT_LENGTH
        }

    private val EXPLICIT_WEB = Regex(
        "\\b(search (?:the )?web|look (?:it )?up online|google (?:it|this)|browse (?:the )?web|online search|web search)\\b",
        RegexOption.IGNORE_CASE,
    )

    private val FRESHNESS = Regex(
        "\\b(today|tonight|tomorrow|current|currently|latest|recent|recently|right now|this week|this month|this year|news|price|prices|weather|forecast|score|scores|schedule|availability|available now|opening hours|open now)\\b",
        RegexOption.IGNORE_CASE,
    )

    private val LIVE_LOOKUP = Regex(
        "\\b(find me|find (?:a|an|the)|compare prices|near me|nearby|book|reserve|buy|shopping|deal|deals|stock price|exchange rate)\\b",
        RegexOption.IGNORE_CASE,
    )

    private val CONTEXTUAL_COMMERCE = Regex(
        "\\b(how much(?: is (?:it|this|that))?|what does (?:it|this|that) cost|where can i (?:get|buy) (?:it|this|that)|is (?:it|this|that) available|find (?:something|one) better|cheaper alternative|better alternative)\\b",
        RegexOption.IGNORE_CASE,
    )

    private const val MIN_CONTEXT_LENGTH = 3
}

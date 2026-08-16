package com.fersaiyan.cyanbridge.ai.orchestrator

/**
 * Decides whether a turn should prefer fresh web-grounded information.
 *
 * Explicit user language wins. Otherwise we only auto-enable grounding for clearly
 * freshness-sensitive requests so ordinary conversation stays fast and cheap.
 */
object AssistantWebPolicy {
    fun shouldUseWeb(text: String, requested: Boolean? = null): Boolean {
        requested?.let { return it }
        val normalized = text.trim().lowercase()
        if (normalized.isBlank()) return false

        if (EXPLICIT_WEB.containsMatchIn(normalized)) return true
        if (FRESHNESS.containsMatchIn(normalized)) return true
        if (LIVE_LOOKUP.containsMatchIn(normalized)) return true

        return false
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
}

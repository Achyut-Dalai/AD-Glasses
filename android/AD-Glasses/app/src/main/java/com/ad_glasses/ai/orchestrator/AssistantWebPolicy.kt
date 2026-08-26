package com.ad_glasses.ai.orchestrator

import com.ad_glasses.shared.chat.ChatMessage

/** Web use is explicit or inferred for questions whose answer is inherently time-sensitive. */
object AssistantWebPolicy {
    fun shouldUseWeb(
        text: String,
        requested: Boolean? = null,
        history: List<ChatMessage> = emptyList(),
    ): Boolean {
        val clean = text.trim()
        if (requested == true) return true
        if (EXPLICIT_WEB.containsMatchIn(clean)) return true
        // A visible per-turn "off" remains authoritative. Automatic freshness only applies when
        // the surface did not supply a preference at all. Bare "current" is deliberately not a
        // trigger so "what is my current location?" stays on the OSM-only privacy path.
        return requested == null && LIVE_FACT.containsMatchIn(clean)
    }

    private val EXPLICIT_WEB = Regex(
        pattern = "\\b(search (?:the )?web|browse (?:the )?web|search online|browse online|use web search|" +
            "look up .{0,80} (?:online|on the web|on the internet)|search the internet)\\b",
        option = RegexOption.IGNORE_CASE,
    )

    private val LIVE_FACT = Regex(
        pattern = "\\b(latest|newest|currently|today|tonight|tomorrow|right now|breaking|news|" +
            "weather|forecast|temperature|price|prices|pricing|stock price|exchange rate|score|scores|" +
            "open now|closed now|opening hours|business hours|operating hours|operational status|availability|" +
            "current (?:news|events?|weather|forecast|temperature|price|prices|pricing|score|scores|status|availability|version))\\b",
        option = RegexOption.IGNORE_CASE,
    )
}

package com.ad_glasses.ai.orchestrator

import com.ad_glasses.shared.chat.ChatMessage

/**
 * Decides whether a turn needs public-web retrieval.
 *
 * This deliberately classifies intent instead of treating single words such as `current`, `price`,
 * `score`, or `availability` as sufficient. False-positive web use is both a latency regression and
 * a privacy leak, so automatic retrieval is limited to clearly time-sensitive fact families.
 */
object AssistantWebPolicy {
    fun shouldUseWeb(
        text: String,
        requested: Boolean? = null,
        history: List<ChatMessage> = emptyList(),
    ): Boolean {
        val clean = text.trim()
        if (clean.isBlank()) return requested == true
        if (requested == true) return true
        // Explicit language in the user's utterance is stronger than a stale/off UI default.
        if (EXPLICIT_WEB.containsMatchIn(clean)) return true
        // A visible per-turn "off" is authoritative for inferred freshness.
        if (requested == false) return false
        return isInherentlyFresh(clean)
    }

    internal fun isInherentlyFresh(text: String): Boolean {
        val clean = text.trim()
        if (clean.isBlank()) return false

        // These concepts are time-varying by definition when asked as facts.
        if (NEWS.containsMatchIn(clean)) return true
        if (WEATHER_QUERY.containsMatchIn(clean)) return true
        if (MARKETS.containsMatchIn(clean)) return true
        if (BUSINESS_LIVE.containsMatchIn(clean)) return true
        if (SPORTS_LIVE.containsMatchIn(clean)) return true

        // Relative recency words only activate retrieval when paired with a fact family that can
        // materially change. This keeps phrases such as "my current location", "current in a wire",
        // "newest recipe ideas", and "next prime number" on the normal assistant path.
        return FRESHNESS.containsMatchIn(clean) && FRESH_TARGET.containsMatchIn(clean)
    }

    private val EXPLICIT_WEB = Regex(
        pattern = "\\b(search (?:the )?(?:web|internet)|browse (?:the )?(?:web|internet)|search online|browse online|" +
            "use web search|check online|look online|find online|look (?:it|this|that) up online|" +
            "look up .{0,80} (?:online|on the web|on the internet)|verify .{0,80} (?:online|on the web|with (?:web )?sources))\\b",
        option = RegexOption.IGNORE_CASE,
    )

    private val NEWS = Regex(
        "\\b(?:breaking news|latest news|news (?:about|on|for)|headlines?|news today)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val WEATHER_QUERY = Regex(
        "\\b(?:what(?:'s| is) the weather|how(?:'s| is) the weather|" +
            "weather (?:in|for|at|near|here|today|tonight|tomorrow|now|this (?:morning|afternoon|evening|weekend))|" +
            "forecast (?:for|in|at|near|here|today|tonight|tomorrow|now)|" +
            "what(?:'s| is) the temperature|temperature (?:here|today|tonight|tomorrow|now))\\b",
        RegexOption.IGNORE_CASE,
    )
    private val MARKETS = Regex(
        "\\b(?:stock (?:price|quote)|share price|market price|exchange rate|forex rate|currency rate|crypto price|bitcoin price|ethereum price)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val BUSINESS_LIVE = Regex(
        "\\b(?:open now|closed now|opening hours|business hours|operating hours|hours today|in stock|sold out|available (?:now|today|tonight)|availability (?:today|tonight|now))\\b",
        RegexOption.IGNORE_CASE,
    )
    private val SPORTS_LIVE = Regex(
        "\\b(?:live score|final score|match score|game score|sports? scores?|league standings?|match results?|game results?)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val FRESHNESS = Regex(
        "\\b(?:latest|most recent|recent|newest|new version|today|tonight|tomorrow|right now|as of now|this week|this month|current)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val FRESH_TARGET = Regex(
        "\\b(?:news|headlines?|developments?|updates?|research|release|release date|version|software|firmware|app|model|" +
            "weather|forecast|temperature|price|pricing|cost|stock|share|market|exchange rate|currency|crypto|" +
            "score|scores|standings|results?|schedule|fixture|game|match|race|tournament|event|" +
            "availability|in stock|sold out|opening hours|business hours|operating hours|outage|service status|flight status)\\b",
        RegexOption.IGNORE_CASE,
    )
}

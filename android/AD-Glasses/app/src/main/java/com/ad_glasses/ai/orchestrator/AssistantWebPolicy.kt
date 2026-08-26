package com.ad_glasses.ai.orchestrator

import com.ad_glasses.shared.chat.ChatMessage
import com.ad_glasses.shared.chat.ChatRole

/**
 * Decides whether a turn needs public-web retrieval.
 *
 * This deliberately classifies intent instead of treating single words such as `search`, `find`,
 * `current`, `price`, `score`, or `availability` as sufficient. False-positive web use is both a
 * latency regression and a privacy leak, so automatic retrieval is limited to clearly dynamic fact
 * families and tightly-bounded conversational follow-ups.
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

        // Explicit language in the user's utterance is stronger than a stale/off UI default. This
        // means a user can say "search the web" even if an earlier surface toggle was off.
        if (isExplicitWebRequest(clean)) return true

        // A visible per-turn "off" is authoritative for inferred freshness and inherited context.
        if (requested == false) return false
        if (isInherentlyFresh(clean)) return true

        val priorUserText = previousUserText(clean, history)
        return priorUserText != null &&
            isEllipticalFreshFollowUp(clean) &&
            (isInherentlyFresh(priorUserText) || isExplicitWebRequest(priorUserText))
    }

    internal fun isExplicitWebRequest(text: String): Boolean {
        val clean = text.trim()
        if (clean.isBlank()) return false
        return EXPLICIT_WEB.containsMatchIn(clean) ||
            LOOK_UP.containsMatchIn(clean) ||
            SEARCH_EXTERNAL.containsMatchIn(clean)
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

    private fun previousUserText(current: String, history: List<ChatMessage>): String? {
        val users = history.filter { it.role == ChatRole.USER }
        if (users.isEmpty()) return null
        val last = users.last()
        return if (last.content.trim() == current) {
            users.getOrNull(users.lastIndex - 1)?.content?.trim()?.takeIf { it.isNotBlank() }
        } else {
            last.content.trim().takeIf { it.isNotBlank() }
        }
    }

    private fun isEllipticalFreshFollowUp(text: String): Boolean =
        ELLIPTICAL_TIME_FOLLOW_UP.matches(text.trim()) ||
            ELLIPTICAL_FACT_FOLLOW_UP.matches(text.trim()) ||
            ELLIPTICAL_SPORTS_FOLLOW_UP.matches(text.trim())

    private val EXPLICIT_WEB = Regex(
        pattern = "\\b(search (?:the )?(?:web|internet)|browse (?:the )?(?:web|internet)|search online|browse online|" +
            "browse for|use web search|check online|look online|find online|google this|google it|" +
            "look (?:it|this|that) up online|look up .{0,80} (?:online|on the web|on the internet)|" +
            "verify .{0,80} (?:online|on the web|with (?:web )?sources))\\b",
        option = RegexOption.IGNORE_CASE,
    )
    // "Look up" is itself a compound retrieval directive in ordinary speech. This is intentionally
    // different from bare "find"/"search", which are common in local reasoning and code tasks.
    private val LOOK_UP = Regex("\\blook up\\b", RegexOption.IGNORE_CASE)
    private val SEARCH_EXTERNAL = Regex(
        "\\bsearch for\\b.{0,100}\\b(?:latest|most recent|today|tonight|tomorrow|news|weather|forecast|" +
            "current price|stock price|exchange rate|score|opening hours?|release date|specs?|specifications?|recall)\\b",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    private val NEWS = Regex(
        "\\b(?:(?:breaking|latest|current|top|local|today(?:'s)?) news|" +
            "news (?:today|tonight|right now|about|on|for)|(?:latest|top|today(?:'s)?) headlines?)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val WEATHER_QUERY = Regex(
        "(?:\\b(?:what(?:'s| is)|how(?:'s| is)) the weather(?:\\s*\\?|$)|" +
            "\\bweather (?:in|for|at|near|here|outside|today|tonight|tomorrow|now|this (?:morning|afternoon|evening|weekend))\\b|" +
            "\\b(?:weather )?forecast (?:for|in|at|near|here|today|tonight|tomorrow|this (?:week|weekend))\\b|" +
            "\\b(?:what(?:'s| is)) the temperature(?:\\s*\\?|$|\\s+(?:in|at|near|outside|here|today|tonight|tomorrow|now)\\b)|" +
            "\\b(?:is it|will it|is it going to|is there going to be)\\s+(?:rain(?:ing)?|snow(?:ing)?|storm(?:ing)?)\\b|" +
            "\\bdo i need (?:an? )?umbrella(?:\\s+(?:today|tonight|tomorrow|now|this (?:morning|afternoon|evening)))?\\b)",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val MARKETS = Regex(
        "\\b(?:stock (?:price|quote)|share price|market price|exchange rate|forex rate|currency rate|crypto price|" +
            "bitcoin price|ethereum price|price of (?:bitcoin|ethereum|btc|eth)|" +
            "how much is (?:bitcoin|ethereum|btc|eth)|what(?:'s| is) (?:bitcoin|ethereum|btc|eth) worth)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val BUSINESS_LIVE = Regex(
        "(?:\\b(?:open now|closed now|opening hours|closing hours|business hours|operating hours|hours today|" +
            "in stock|sold out|available (?:now|today|tonight)|availability (?:today|tonight|now))\\b|" +
            "\\b(?:when|what time) (?:does|do) (?:the )?.{0,60}\\b(?:store|shop|restaurant|cafe|café|museum|" +
            "pharmacy|bank|library|gym|mall|supermarket|office|park|attraction)\\b (?:open|close)(?:\\s+(?:today|tonight|tomorrow))?\\s*[?.!]*$|" +
            "\\b(?:is|are) (?:the )?.{0,60}\\b(?:store|shop|restaurant|cafe|café|museum|pharmacy|bank|library|" +
            "gym|mall|supermarket|office|park|attraction)\\b (?:open|closed)(?:\\s+(?:now|today|tonight))?\\s*[?.!]*$)",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val SPORTS_LIVE = Regex(
        "(?:\\b(?:live score|final score|match score|game score|sports? scores?|league standings?|match results?|game results?)\\b|" +
            "\\bwho won (?:the )?(?:match|game|race|final|tournament|championship|fight|bout)\\b|" +
            "\\bwho is winning (?:the )?(?:match|game|race|final|tournament|championship|fight|bout)\\b)",
        RegexOption.IGNORE_CASE,
    )
    private val FRESHNESS = Regex(
        "\\b(?:latest|most recent|recent|newest|new version|today(?:'s)?|tonight|tomorrow|right now|as of now|" +
            "this week|this month|current|updated|newly released)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val FRESH_TARGET = Regex(
        "\\b(?:news|headlines?|developments?|updates?|research|release|release date|version|software|firmware|app|model|" +
            "specs?|specifications?|documentation|docs|security patch|patch|device|phone|laptop|product|" +
            "weather|forecast|temperature|price|pricing|cost|stock|share|market|exchange rate|currency|crypto|" +
            "score|scores|standings|results?|schedule|fixture|game|match|race|tournament|event|" +
            "availability|in stock|sold out|opening hours|business hours|operating hours|outage|service status|flight status|" +
            "law|laws|regulations?|policy|rules|requirements?|standards?|president|prime minister|ceo|leader|office holder|" +
            "movie|film|album|song|episode|season)\\b",
        RegexOption.IGNORE_CASE,
    )

    private val ELLIPTICAL_TIME_FOLLOW_UP = Regex(
        "^(?:and\\s+|what about\\s+|how about\\s+)?(?:today|tonight|tomorrow|yesterday|now|right now|later|" +
            "this weekend|next week|next month|monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\s*[?.!]*$",
        RegexOption.IGNORE_CASE,
    )
    private val ELLIPTICAL_FACT_FOLLOW_UP = Regex(
        "^(?:and|what about|how about)\\s+(?:the\\s+)?(?:price|score|weather|forecast|hours|availability|version|" +
            "status|results?|standings)(?:\\s+(?:now|today|tonight|tomorrow))?\\s*[?.!]*$",
        RegexOption.IGNORE_CASE,
    )
    private val ELLIPTICAL_SPORTS_FOLLOW_UP = Regex(
        "^(?:and\\s+)?who won\\s*[?.!]*$",
        RegexOption.IGNORE_CASE,
    )
}

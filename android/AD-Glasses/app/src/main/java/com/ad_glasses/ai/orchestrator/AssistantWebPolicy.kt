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

        if (isExplicitWebRequest(clean)) return true
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
        val safeBareLookup = LOOK_UP.containsMatchIn(clean) &&
            !NON_WEB_LOOKUP_CONTEXT.containsMatchIn(clean) &&
            !LOOK_UP_GAZE.containsMatchIn(clean)
        return EXPLICIT_WEB.containsMatchIn(clean) || safeBareLookup || SEARCH_EXTERNAL.containsMatchIn(clean)
    }

    internal fun isInherentlyFresh(text: String): Boolean {
        val clean = text.trim()
        if (clean.isBlank()) return false

        // Dynamic nouns can be discussed as language/concepts. Those questions are not live lookups.
        if (META_LIVE_FACT_CONCEPT.containsMatchIn(clean)) return false
        if (CONCEPTUAL_DYNAMIC_TERM.containsMatchIn(clean)) return false

        if (NEWS.containsMatchIn(clean)) return true
        if (WEATHER_QUERY.containsMatchIn(clean)) return true
        if (MARKETS.containsMatchIn(clean)) return true
        if (BUSINESS_LIVE.containsMatchIn(clean)) return true
        if (SPORTS_LIVE.containsMatchIn(clean)) return true
        if (PUBLIC_OFFICE_LIVE.containsMatchIn(clean)) return true
        if (ELECTION_LIVE.containsMatchIn(clean)) return true
        if (FLIGHT_LIVE.containsMatchIn(clean)) return true
        if (SERVICE_STATUS.containsMatchIn(clean)) return true

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
    private val LOOK_UP = Regex("\\blook up\\b", RegexOption.IGNORE_CASE)
    private val LOOK_UP_GAZE = Regex(
        "\\blook up\\s+(?:at|toward|towards|to)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val NON_WEB_LOOKUP_CONTEXT = Regex(
        "\\blook up\\b.{0,100}\\b(?:in|from|inside|within)\\s+(?:(?:a|an|the|this|that)\\s+)?" +
            "(?:array|list|map|hashmap|hash map|dictionary|table|database|cache|index|json|object|file|code|" +
            "function|method|class|source|collection|tree|graph|matrix|data structure)\\b",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val SEARCH_EXTERNAL = Regex(
        "\\bsearch for\\b.{0,100}\\b(?:latest|most recent|today|tonight|tomorrow|news|weather|forecast|" +
            "current price|stock price|exchange rate|score|opening hours?|release date|specs?|specifications?|recall)\\b",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    private val META_LIVE_FACT_CONCEPT = Regex(
        "(?:^\\s*(?:define|definition of|meaning of)\\s+(?:the\\s+)?(?:term\\s+|phrase\\s+)?" +
            "(?:local news|breaking news|latest news|weather forecast|live score|stock price|share price|exchange rate|" +
            "service status|website status|opening hours|business hours|operating hours|availability)\\s*[?.!]*$|" +
            "^\\s*what does\\s+(?:the\\s+)?(?:term\\s+|phrase\\s+)?" +
            "(?:local news|breaking news|latest news|weather forecast|live score|stock price|share price|exchange rate|" +
            "service status|website status|opening hours|business hours|operating hours|availability)\\s+mean\\s*[?.!]*$|" +
            "^\\s*what is\\s+(?:a|an)\\s+(?:weather forecast|live score|stock price|share price|exchange rate|" +
            "service status|website status)\\s*[?.!]*$|" +
            "^\\s*what is\\s+(?:local news|breaking news|latest news)\\s*[?.!]*$|" +
            "^\\s*explain\\s+(?:the\\s+)?(?:term|phrase|concept)(?:\\s+of)?\\s+" +
            "(?:local news|breaking news|latest news|weather forecast|live score|stock price|share price|exchange rate|" +
            "service status|website status|opening hours|business hours|operating hours|availability)\\s*[?.!]*$|" +
            "^\\s*explain\\s+(?:local news|breaking news|weather forecast|stock prices?|share prices?|exchange rates?|" +
            "service status|website status|opening hours|business hours|operating hours|availability)\\s+" +
            "(?:as|in)\\s+.{0,60}\\b(?:concept|term|phrase|system|field)\\b.*$)",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val CONCEPTUAL_DYNAMIC_TERM = Regex(
        "(?:^\\s*(?:define|explain|what does|what is|what are)\\s+(?:the\\s+term\\s+|a\\s+|an\\s+)?" +
            "(?:stock price|share price|exchange rate|forex rate|currency rate|service status|website status|" +
            "opening hours|business hours|operating hours|availability)\\b.{0,100}\\b(?:mean|concept|term|" +
            "in (?:programming|computer science|economics|finance|distributed systems|a state machine|state machines?))\\b|" +
            "^\\s*what (?:is|are)\\s+(?:a|an)\\s+(?:stock price|share price|exchange rate|forex rate|currency rate|" +
            "service status|website status|opening hours|business hours|operating hours)\\s*[?.!]*$|" +
            "^\\s*explain\\s+(?:the\\s+)?(?:stock prices?|share prices?|exchange rates?|forex rates?|currency rates?|" +
            "service status|website status|opening hours|business hours|operating hours|availability)\\s*[?.!]*$)",
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
        "(?:\\b(?:stock (?:price|quote)|share price|market price|crypto price|bitcoin price|ethereum price|" +
            "price of (?:bitcoin|ethereum|btc|eth)|how much is (?:bitcoin|ethereum|btc|eth)|" +
            "what(?:'s| is) (?:bitcoin|ethereum|btc|eth) worth)\\b|" +
            "\\b[A-Z]{3}\\s+(?:to|/|vs\\.?|versus)\\s+[A-Z]{3}\\s+(?:exchange|forex|currency) rate\\b|" +
            "\\b(?:exchange|forex|currency) rate\\s+(?:for|between)\\s+[A-Z]{3}\\b.{0,24}\\b[A-Z]{3}\\b)",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
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
            "\\bwho is winning (?:the )?(?:match|game|race|final|tournament|championship|fight|bout)\\b|" +
            "\\b(?:when is|what time is) (?:the )?next (?:game|match|race|fixture|fight|bout)\\b|" +
            "\\bnext (?:game|match|race|fixture|fight|bout) (?:time|date|schedule)\\b)",
        RegexOption.IGNORE_CASE,
    )
    private val PUBLIC_OFFICE_LIVE = Regex(
        "(?:\\bwho (?:is|'s) (?:the )?(?:president|prime minister|chief minister|governor|mayor|" +
            "ceo|chief executive|chairperson|chairman|chairwoman) (?:of|at|for)\\b|" +
            "\\bwho (?:runs|leads|heads) (?:the )?.{1,80}\\b(?:company|government|administration|organization|organisation)\\b)",
        RegexOption.IGNORE_CASE,
    )
    private val ELECTION_LIVE = Regex(
        "\\b(?:who won (?:the )?(?:election|primary|referendum)|(?:election|primary|referendum) results?|" +
            "election outcome|vote count)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val FLIGHT_LIVE = Regex(
        "(?:\\bflight\\s+[A-Z0-9-]{2,10}\\b.{0,50}\\b(?:status|on time|delayed|cancelled|canceled|departed|landed)\\b|" +
            "\\b(?:status of|track) flight\\s+[A-Z0-9-]{2,10}\\b)",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val SERVICE_STATUS = Regex(
        "(?:\\b(?:service|website|site|api|platform|network) (?:status|outage)\\b" +
            "(?:\\s+(?:now|today|right now))?\\s*[?.!]*$|" +
            "\\b(?:status|outage) (?:for|of) (?:the )?(?:service|website|site|api|platform|network)\\b" +
            "(?:\\s+(?:now|today|right now))?\\s*[?.!]*$)",
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

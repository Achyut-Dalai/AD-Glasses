package com.ad_glasses.ai.grounding

import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

enum class TavilySearchTopic(val wire: String) {
    GENERAL("general"),
    NEWS("news"),
    FINANCE("finance"),
}

enum class TavilyTimeRange(val wire: String) {
    DAY("day"),
    WEEK("week"),
    MONTH("month"),
    YEAR("year"),
}

internal data class TavilySearchPlan(
    val query: String,
    val topic: TavilySearchTopic,
    val timeRange: TavilyTimeRange? = null,
    val startDate: String? = null,
    val endDate: String? = null,
)

/** API-24-safe calendar date used only for deterministic search planning. */
internal data class TavilySearchDate(
    val year: Int,
    val month: Int,
    val day: Int,
) : Comparable<TavilySearchDate> {
    override fun compareTo(other: TavilySearchDate): Int = when {
        year != other.year -> year.compareTo(other.year)
        month != other.month -> month.compareTo(other.month)
        else -> day.compareTo(other.day)
    }

    fun plusDays(days: Int): TavilySearchDate {
        val calendar = utcCalendar(year, month, day) ?: return this
        calendar.add(Calendar.DAY_OF_MONTH, days)
        return fromCalendar(calendar)
    }

    override fun toString(): String = String.format(Locale.US, "%04d-%02d-%02d", year, month, day)

    companion object {
        fun today(): TavilySearchDate = fromCalendar(Calendar.getInstance())

        fun parseIso(value: String): TavilySearchDate? {
            val parts = value.split('-')
            if (parts.size != 3) return null
            val year = parts[0].toIntOrNull() ?: return null
            val month = parts[1].toIntOrNull() ?: return null
            val day = parts[2].toIntOrNull() ?: return null
            return if (utcCalendar(year, month, day) != null) TavilySearchDate(year, month, day) else null
        }

        private fun fromCalendar(calendar: Calendar): TavilySearchDate = TavilySearchDate(
            year = calendar.get(Calendar.YEAR),
            month = calendar.get(Calendar.MONTH) + 1,
            day = calendar.get(Calendar.DAY_OF_MONTH),
        )

        private fun utcCalendar(year: Int, month: Int, day: Int): Calendar? = runCatching {
            GregorianCalendar(TimeZone.getTimeZone("UTC"), Locale.US).apply {
                isLenient = false
                clear()
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month - 1)
                set(Calendar.DAY_OF_MONTH, day)
                // Force validation while lenient=false.
                timeInMillis
            }
        }.getOrNull()
    }
}

/**
 * Converts the user's temporal intent into Tavily-native search constraints.
 *
 * Explicit historical dates always win over freshness words. A request such as "Bitcoin price in
 * 2025" must search 2025 rather than being rewritten as a current-price lookup. Current/live
 * questions get bounded freshness, while timeless questions remain unrestricted.
 */
internal object TavilySearchPolicy {
    private val ISO_DATE = Regex("\\b(?:19|20)\\d{2}-(?:0[1-9]|1[0-2])-(?:0[1-9]|[12]\\d|3[01])\\b")
    private val YEAR = Regex("\\b(?:19|20)\\d{2}\\b")
    private val TODAY_NOW = Regex(
        "\\b(today|right now|now|currently|at the moment|live)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val CURRENT_LIVE_FACT = Regex(
        "\\bcurrent\\s+(?:price|value|rate|score|status|weather|temperature|exchange rate|market price)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val MARKET_LIVE_FACT = Regex(
        "\\b(?:price|market price|share price|quote|trading at|exchange rate|forex rate|worth)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val HISTORICAL_PUBLICATION_EVENT = Regex(
        "\\b(?:news|headlines?|happened|event|announc(?:e|ed|ement)|releas(?:e|ed)|launch(?:ed)?|" +
            "election|elections|who won|result|results)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val THIS_WEEK = Regex("\\bthis week\\b", RegexOption.IGNORE_CASE)
    private val THIS_MONTH = Regex("\\bthis month\\b", RegexOption.IGNORE_CASE)
    private val LATEST = Regex("\\b(latest|most recent|recent|current)\\b", RegexOption.IGNORE_CASE)
    private val NEWS = Regex(
        "\\b(news|headlines?|breaking|election|elections|who won|score|scores|sports results?|outage|incident)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val FINANCE = Regex(
        "\\b(bitcoin|btc|ethereum|eth|crypto|cryptocurrency|stock|stocks|share price|market price|trading at|" +
            "exchange rate|forex|nasdaq|s&p|dow jones|gold price|silver price)\\b",
        RegexOption.IGNORE_CASE,
    )

    fun plan(rawQuery: String, today: TavilySearchDate = TavilySearchDate.today()): TavilySearchPlan {
        val clean = rawQuery.replace(Regex("\\s+"), " ").trim()
        val topic = when {
            FINANCE.containsMatchIn(clean) -> TavilySearchTopic.FINANCE
            NEWS.containsMatchIn(clean) -> TavilySearchTopic.NEWS
            else -> TavilySearchTopic.GENERAL
        }

        // Explicit user dates are authoritative. This branch intentionally runs before every
        // current/live heuristic so "Bitcoin price in 2025" never becomes a present-day lookup.
        // Tavily's start/end dates filter SOURCE publication/update time, not the date of a fact
        // inside a page. Apply those bounds only when publication timing is actually relevant (for
        // example historical news/events), not to historical market-data pages updated later.
        explicitDateWindow(clean, today)?.let { window ->
            val constrainPublicationDate = topic == TavilySearchTopic.NEWS ||
                HISTORICAL_PUBLICATION_EVENT.containsMatchIn(clean)
            return TavilySearchPlan(
                query = "$clean. Search for evidence about the requested historical period ${window.label}; do not substitute current data.",
                topic = topic,
                startDate = window.start.toString().takeIf { constrainPublicationDate },
                endDate = window.end.toString().takeIf { constrainPublicationDate },
            )
        }

        val freshness = when {
            THIS_WEEK.containsMatchIn(clean) -> TavilyTimeRange.WEEK
            THIS_MONTH.containsMatchIn(clean) -> TavilyTimeRange.MONTH
            TODAY_NOW.containsMatchIn(clean) || CURRENT_LIVE_FACT.containsMatchIn(clean) -> TavilyTimeRange.DAY
            topic == TavilySearchTopic.FINANCE && MARKET_LIVE_FACT.containsMatchIn(clean) -> TavilyTimeRange.DAY
            LATEST.containsMatchIn(clean) && topic == TavilySearchTopic.NEWS -> TavilyTimeRange.WEEK
            LATEST.containsMatchIn(clean) -> TavilyTimeRange.YEAR
            else -> null
        }
        if (freshness == null) return TavilySearchPlan(query = clean, topic = topic)

        return TavilySearchPlan(
            query = "$clean. Current date: $today. Use information valid for the requested current/recent period.",
            topic = topic,
            timeRange = freshness,
        )
    }

    private fun explicitDateWindow(text: String, today: TavilySearchDate): DateWindow? {
        val isoMatches = ISO_DATE.findAll(text).toList()
        if (isoMatches.isNotEmpty()) {
            val parsedDates = isoMatches.mapNotNull { match -> TavilySearchDate.parseIso(match.value) }
            // A date-shaped but impossible value (for example 2025-02-31) is not permission to
            // silently broaden the request into a whole-year filter.
            if (parsedDates.size != isoMatches.size) return null
            val earliest = parsedDates.minOrNull() ?: return null
            val latest = parsedDates.maxOrNull() ?: return null
            return DateWindow(
                start = earliest.plusDays(-1),
                end = latest.plusDays(1),
                label = if (earliest == latest) earliest.toString() else "$earliest to $latest",
            )
        }

        val years = YEAR.findAll(text)
            .mapNotNull { it.value.toIntOrNull() }
            .filter { it in 1900..today.year }
            .toList()
        if (years.isEmpty()) return null

        val firstYear = years.minOrNull() ?: return null
        val lastYear = years.maxOrNull() ?: return null
        val requestedStart = TavilySearchDate(firstYear, 1, 1).plusDays(-1)
        val requestedEnd = TavilySearchDate(lastYear + 1, 1, 1)
        val boundedEnd = minOf(requestedEnd, today.plusDays(1))
        return DateWindow(
            start = requestedStart,
            end = boundedEnd,
            label = if (firstYear == lastYear) firstYear.toString() else "$firstYear to $lastYear",
        )
    }

    private data class DateWindow(
        val start: TavilySearchDate,
        val end: TavilySearchDate,
        val label: String,
    )
}

package com.ad_glasses.ai.grounding

import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

/**
 * Structured ESPN event/score lookup.
 *
 * The client deliberately avoids article bodies and avoids a cross-sport event dump. It first
 * infers or discovers the relevant ESPN league/series, fetches only those scoreboards, and ranks
 * structured event records against the user's query. If structured data cannot answer the request,
 * the caller receives a failure and may tell the user that no reliable score was found.
 *
 * ESPN's JSON endpoints are public but undocumented, so parsing is intentionally defensive.
 */
internal class EspnScoreClient(
    private val client: OkHttpClient = defaultClient(),
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    suspend fun lookup(query: String): Result<StructuredKnowledgeResult> = try {
        val clean = query.clean(MAX_QUERY_CHARS)
        require(clean.isNotBlank()) { "Sports score query cannot be blank." }
        val dateRange = dateRangeFor(clean)
        val candidates = mutableListOf<EspnEvent>()
        val attempted = linkedSetOf<LeagueSpec>()

        // Explicit league names are the cheapest and most reliable path.
        inferKnownLeagues(clean).take(MAX_LEAGUE_SCOREBOARDS).forEach { spec ->
            attempted += spec
            fetchLeagueScoreboard(spec, dateRange)?.let {
                candidates += parseEventsContainer(it, sourceLabel = spec.label)
            }
        }

        // Cricket uses ESPN's active-series header because series identifiers are numeric/dynamic.
        if (isCricketLikely(clean)) {
            fetchCricketHeader()?.let { candidates += parseHeader(it, sportLabel = "Cricket") }
        }

        // A team/player name often omits the league. Use ESPN's small search endpoint only for
        // discovery, harvest league references, then fetch the structured scoreboard itself.
        if (candidates.none { it.matchScore(clean) >= STRONG_MATCH_SCORE }) {
            discoverLeagues(clean)
                .filterNot(attempted::contains)
                .take(MAX_DISCOVERED_LEAGUES)
                .forEach { spec ->
                    attempted += spec
                    fetchLeagueScoreboard(spec, dateRange)?.let {
                        candidates += parseEventsContainer(it, sourceLabel = spec.label)
                    }
                }
        }

        // For ambiguous cricket wording, one bounded header read is still safer than article search.
        if (!isCricketLikely(clean) && candidates.none { it.matchScore(clean) >= STRONG_MATCH_SCORE }) {
            if (semanticTokens(clean).any(CRICKET_TEAM_TERMS::contains)) {
                fetchCricketHeader()?.let { candidates += parseHeader(it, sportLabel = "Cricket") }
            }
        }

        val ranked = candidates
            .distinctBy { it.id.ifBlank { "${it.name.lowercase(Locale.US)}|${it.date}" } }
            .map { event -> event to event.matchScore(clean) }
            .filter { (_, score) -> score > 0 }
            .sortedWith(
                compareByDescending<Pair<EspnEvent, Int>> { it.second }
                    .thenByDescending { it.first.stateRank },
            )

        val selected = ranked.take(MAX_MATCHES).map(Pair<EspnEvent, Int>::first)
        if (selected.isEmpty()) {
            throw IllegalStateException("ESPN structured scoreboards returned no event matching the sports query.")
        }
        Result.success(toResult(clean, selected))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Result.failure(error)
    }

    private suspend fun fetchLeagueScoreboard(spec: LeagueSpec, range: DateRange): JSONObject? {
        val url = HttpUrl.Builder()
            .scheme("https")
            .host("site.api.espn.com")
            .addPathSegments("apis/site/v2/sports/${spec.sport}/${spec.league}/scoreboard")
            .addQueryParameter("dates", range.wire)
            .build()
        return fetchJson(url, "ESPN ${spec.label} scoreboard").getOrNull()
    }

    private suspend fun fetchCricketHeader(): JSONObject? {
        val url = HttpUrl.Builder()
            .scheme("https")
            .host("site.web.api.espn.com")
            .addPathSegments("apis/personalized/v2/scoreboard/header")
            .addQueryParameter("sport", "cricket")
            .addQueryParameter("region", "in")
            .addQueryParameter("tz", zoneId.id)
            .build()
        return fetchJson(url, "ESPN cricket scoreboard header").getOrNull()
    }

    private suspend fun discoverLeagues(query: String): List<LeagueSpec> {
        val url = HttpUrl.Builder()
            .scheme("https")
            .host("site.web.api.espn.com")
            .addPathSegments("apis/search/v2")
            .addQueryParameter("query", query.take(SEARCH_QUERY_CHARS))
            .addQueryParameter("limit", SEARCH_RESULT_LIMIT.toString())
            .build()
        val root = fetchJson(url, "ESPN search").getOrNull() ?: return emptyList()
        val refs = linkedSetOf<LeagueSpec>()
        collectLeagueRefs(root, refs, depth = 0)
        return refs.take(MAX_DISCOVERED_LEAGUES)
    }

    private fun collectLeagueRefs(value: Any?, output: MutableSet<LeagueSpec>, depth: Int) {
        if (value == null || depth > MAX_JSON_WALK_DEPTH || output.size >= MAX_DISCOVERED_LEAGUES) return
        when (value) {
            is JSONObject -> {
                inferLeagueObject(value)?.let(output::add)
                value.keys().forEach { key -> collectLeagueRefs(value.opt(key), output, depth + 1) }
            }
            is JSONArray -> for (index in 0 until value.length()) {
                collectLeagueRefs(value.opt(index), output, depth + 1)
            }
            is String -> {
                val lower = value.lowercase(Locale.US)
                LEAGUE_REF.findAll(lower).forEach { match ->
                    val sport = match.groupValues[1]
                    val league = match.groupValues[2]
                    if (sport.isNotBlank() && league.isNotBlank()) {
                        output += LeagueSpec(sport, league, "$sport/$league")
                    }
                }
                SITE_LEAGUE_REF.findAll(lower).forEach { match ->
                    val sport = match.groupValues[1]
                    val league = match.groupValues[2]
                    if (sport.isNotBlank() && league.isNotBlank()) {
                        output += LeagueSpec(sport, league, "$sport/$league")
                    }
                }
            }
        }
    }

    private fun inferLeagueObject(json: JSONObject): LeagueSpec? {
        val sport = firstString(
            json.optJSONObject("sport"),
            "slug",
            "name",
            "id",
        )?.lowercase(Locale.US)?.replace(' ', '-')
        val league = sequenceOf(
            firstString(json.optJSONObject("league"), "slug", "abbreviation", "name"),
            firstString(json, "leagueSlug", "leagueAbbreviation"),
        ).firstOrNull { !it.isNullOrBlank() }
            ?.lowercase(Locale.US)
            ?.replace(' ', '-')
        if (sport.isNullOrBlank() || league.isNullOrBlank()) return null
        if (!SAFE_SLUG.matches(sport) || !SAFE_LEAGUE.matches(league)) return null
        return LeagueSpec(sport, league, "$sport/$league")
    }

    internal fun parseEventsContainer(root: JSONObject, sourceLabel: String): List<EspnEvent> {
        val arrays = listOfNotNull(
            root.optJSONArray("events"),
            root.optJSONArray("items"),
            root.optJSONObject("content")?.optJSONArray("events"),
        )
        val result = mutableListOf<EspnEvent>()
        arrays.forEach { array ->
            for (index in 0 until array.length()) {
                parseEvent(array.optJSONObject(index), sourceLabel)?.let(result::add)
            }
        }
        return result
    }

    internal fun parseHeader(root: JSONObject, sportLabel: String): List<EspnEvent> {
        val sports = root.optJSONArray("sports") ?: return emptyList()
        return buildList {
            for (sportIndex in 0 until sports.length()) {
                val sport = sports.optJSONObject(sportIndex) ?: continue
                val leagues = sport.optJSONArray("leagues") ?: continue
                for (leagueIndex in 0 until leagues.length()) {
                    val league = leagues.optJSONObject(leagueIndex) ?: continue
                    val leagueName = firstString(league, "name", "shortName", "abbreviation") ?: sportLabel
                    val events = league.optJSONArray("events") ?: continue
                    for (eventIndex in 0 until events.length()) {
                        parseEvent(events.optJSONObject(eventIndex), leagueName)?.let(::add)
                    }
                }
            }
        }
    }

    private fun parseEvent(event: JSONObject?, sourceLabel: String): EspnEvent? {
        if (event == null) return null
        val name = firstString(event, "name", "shortName", "headline") ?: return null
        val competition = event.optJSONArray("competitions")?.optJSONObject(0)
        val competitors = competition?.optJSONArray("competitors") ?: event.optJSONArray("competitors")
        val sides = buildList {
            if (competitors != null) {
                for (index in 0 until competitors.length()) {
                    val competitor = competitors.optJSONObject(index) ?: continue
                    val team = competitor.optJSONObject("team") ?: competitor.optJSONObject("athlete") ?: competitor
                    val sideName = firstString(team, "displayName", "shortDisplayName", "name", "abbreviation")
                        ?: firstString(competitor, "displayName", "name", "abbreviation")
                        ?: continue
                    val score = scoreString(competitor.opt("score"))
                        ?: firstString(competitor, "score", "displayScore")
                    add(EspnSide(sideName, score))
                }
            }
        }
        val status = statusString(competition?.optJSONObject("status") ?: event.optJSONObject("status"))
        val date = firstString(event, "date", "startDate") ?: competition?.let { firstString(it, "date", "startDate") }
        val league = sequenceOf(
            firstString(event.optJSONObject("league"), "name", "abbreviation", "slug"),
            firstString(event.optJSONObject("season"), "name", "displayName"),
            sourceLabel,
        ).firstOrNull { !it.isNullOrBlank() } ?: sourceLabel
        return EspnEvent(
            id = event.optString("id").trim(),
            name = name.clean(MAX_EVENT_NAME_CHARS),
            league = league.clean(MAX_LEAGUE_NAME_CHARS),
            date = date?.clean(MAX_DATE_CHARS),
            status = status?.clean(MAX_STATUS_CHARS),
            sides = sides.take(MAX_SIDES),
        )
    }

    private fun scoreString(value: Any?): String? = when (value) {
        null, JSONObject.NULL -> null
        is Number -> value.toString()
        is String -> value.clean(80).takeIf(String::isNotBlank)
        is JSONObject -> firstString(value, "displayValue", "value", "score", "summary")
        else -> null
    }

    private fun statusString(status: JSONObject?): String? {
        if (status == null) return null
        val type = status.optJSONObject("type")
        return sequenceOf(
            type?.let { firstString(it, "shortDetail", "detail", "description", "name") },
            firstString(status, "shortDetail", "detail", "displayClock", "description"),
        ).firstOrNull { !it.isNullOrBlank() }
    }

    private fun toResult(query: String, events: List<EspnEvent>): StructuredKnowledgeResult {
        val shown = events.take(MAX_SPOKEN_MATCHES)
        val answer = buildString {
            append("ESPN structured results for $query: ")
            append(shown.joinToString("; ") { it.spokenSummary() })
            append('.')
        }.take(MAX_ANSWER_CHARS)
        val context = buildString {
            appendLine("ESPN structured scoreboard/event data for: $query")
            events.take(MAX_MATCHES).forEachIndexed { index, event ->
                appendLine("[${index + 1}] ${event.contextSummary()}")
            }
            append("Use only these structured score/status records. Do not infer a score from sports articles.")
        }.take(MAX_CONTEXT_CHARS)
        return StructuredKnowledgeResult(
            answer = answer,
            context = context,
            sources = listOf(GroundingSource("ESPN Scores", ESPN_SCORES_URL)),
        )
    }

    private fun inferKnownLeagues(query: String): List<LeagueSpec> {
        val normalized = query.lowercase(Locale.US)
        return KNOWN_LEAGUES.filter { spec ->
            spec.aliases.any { alias -> WORD_BOUNDARY(alias).containsMatchIn(normalized) }
        }
    }

    private fun isCricketLikely(query: String): Boolean {
        val tokens = semanticTokens(query)
        return tokens.any(CRICKET_TERMS::contains)
    }

    private fun dateRangeFor(query: String): DateRange {
        val today = LocalDate.now(zoneId)
        val lower = query.lowercase(Locale.US)
        return when {
            "yesterday" in lower -> DateRange(today.minusDays(1), today.minusDays(1))
            "tomorrow" in lower -> DateRange(today.plusDays(1), today.plusDays(1))
            RECENT_TERMS.any { it in lower } -> DateRange(today.minusDays(7), today)
            UPCOMING_TERMS.any { it in lower } -> DateRange(today, today.plusDays(7))
            else -> DateRange(today.minusDays(1), today.plusDays(1))
        }
    }

    private suspend fun fetchJson(url: HttpUrl, label: String): Result<JSONObject> = try {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .get()
            .build()
        val call = client.newCall(request)
        call.timeout().timeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        val root = call.awaitEspnResponse().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("$label HTTP ${response.code}")
            JSONObject(body)
        }
        Result.success(root)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Result.failure(error)
    }

    private data class DateRange(val start: LocalDate, val end: LocalDate) {
        val wire: String
            get() {
                val formatter = DateTimeFormatter.BASIC_ISO_DATE
                return if (start == end) start.format(formatter)
                else "${start.format(formatter)}-${end.format(formatter)}"
            }
    }

    internal data class EspnEvent(
        val id: String,
        val name: String,
        val league: String,
        val date: String?,
        val status: String?,
        val sides: List<EspnSide>,
    ) {
        val stateRank: Int
            get() {
                val text = status.orEmpty().lowercase(Locale.US)
                return when {
                    "live" in text || "in progress" in text || "halftime" in text ||
                        Regex("\\bq[1-4]\\b").containsMatchIn(text) -> 4
                    "final" in text || "full time" in text || "completed" in text -> 3
                    else -> 1
                }
            }

        fun matchScore(query: String): Int {
            val queryTokens = semanticTokens(query)
            if (queryTokens.isEmpty()) return 1
            val eventTokens = semanticTokens(buildString {
                append(name).append(' ').append(league).append(' ').append(status.orEmpty()).append(' ')
                sides.forEach { append(it.name).append(' ') }
            })
            var score = queryTokens.count(eventTokens::contains) * 3
            val normalizedQuery = normalizeText(query)
            val normalizedName = normalizeText(name)
            if (normalizedName.contains(normalizedQuery) || normalizedQuery.contains(normalizedName)) score += 5
            sides.forEach { side ->
                val normalizedSide = normalizeText(side.name)
                if (normalizedSide.length >= 3 && normalizedQuery.contains(normalizedSide)) score += 4
            }
            return score
        }

        fun spokenSummary(): String {
            val scoreText = sides.takeIf { it.isNotEmpty() }?.joinToString(" to ") { side ->
                if (side.score.isNullOrBlank()) side.name else "${side.name} ${side.score}"
            }
            return listOfNotNull(scoreText ?: name, status, date?.take(10)).joinToString(", ")
        }

        fun contextSummary(): String = buildString {
            append(name)
            if (sides.isNotEmpty()) {
                append("; competitors=")
                append(sides.joinToString(" vs ") { side ->
                    if (side.score.isNullOrBlank()) side.name else "${side.name} ${side.score}"
                })
            }
            status?.let { append("; status=$it") }
            date?.let { append("; date=$it") }
            append("; league=$league")
        }
    }

    internal data class EspnSide(val name: String, val score: String?)

    private data class LeagueSpec(
        val sport: String,
        val league: String,
        val label: String,
        val aliases: Set<String> = emptySet(),
    )

    private companion object {
        const val USER_AGENT = "AD-Glasses/alpha (https://github.com/Achyut-Dalai/AD-Glasses)"
        const val ESPN_SCORES_URL = "https://www.espn.com/scoreboard/"
        const val CALL_TIMEOUT_SECONDS = 5L
        const val SEARCH_RESULT_LIMIT = 8
        const val MAX_QUERY_CHARS = 420
        const val SEARCH_QUERY_CHARS = 180
        const val MAX_EVENT_NAME_CHARS = 260
        const val MAX_LEAGUE_NAME_CHARS = 140
        const val MAX_DATE_CHARS = 80
        const val MAX_STATUS_CHARS = 160
        const val MAX_SIDES = 4
        const val MAX_MATCHES = 6
        const val MAX_SPOKEN_MATCHES = 3
        const val MAX_LEAGUE_SCOREBOARDS = 3
        const val MAX_DISCOVERED_LEAGUES = 3
        const val MAX_JSON_WALK_DEPTH = 9
        const val MAX_ANSWER_CHARS = 1_800
        const val MAX_CONTEXT_CHARS = 2_200
        const val STRONG_MATCH_SCORE = 6
        val SAFE_SLUG = Regex("[a-z0-9-]{1,64}")
        val SAFE_LEAGUE = Regex("[a-z0-9.-]{1,80}")
        val LEAGUE_REF = Regex("/sports/([a-z0-9-]+)/leagues/([a-z0-9.-]+)")
        val SITE_LEAGUE_REF = Regex("/sports/([a-z0-9-]+)/([a-z0-9.-]+)/")
        val GENERIC_TOKENS = setOf(
            "a", "an", "and", "are", "at", "for", "from", "game", "games", "give", "how", "in", "is", "it",
            "latest", "live", "match", "matches", "me", "of", "on", "please", "result", "results", "score", "scores",
            "show", "the", "today", "tonight", "tomorrow", "what", "when", "who", "won", "yesterday",
        )
        val CRICKET_TERMS = setOf("cricket", "ipl", "odi", "t20", "t20i", "test", "wicket", "innings")
        val CRICKET_TEAM_TERMS = setOf(
            "india", "england", "australia", "pakistan", "bangladesh", "srilanka", "afghanistan",
            "westindies", "newzealand", "zimbabwe",
        )
        val RECENT_TERMS = setOf("last", "latest", "recent", "result", "results", "won", "winner")
        val UPCOMING_TERMS = setOf("next", "upcoming", "schedule", "scheduled", "fixture", "fixtures")
        val KNOWN_LEAGUES = listOf(
            LeagueSpec("basketball", "nba", "NBA", setOf("nba")),
            LeagueSpec("basketball", "wnba", "WNBA", setOf("wnba")),
            LeagueSpec("basketball", "mens-college-basketball", "NCAA men's basketball", setOf("ncaam", "march madness")),
            LeagueSpec("basketball", "womens-college-basketball", "NCAA women's basketball", setOf("ncaaw")),
            LeagueSpec("football", "nfl", "NFL", setOf("nfl")),
            LeagueSpec("football", "college-football", "College football", setOf("college football", "ncaaf")),
            LeagueSpec("baseball", "mlb", "MLB", setOf("mlb")),
            LeagueSpec("hockey", "nhl", "NHL", setOf("nhl")),
            LeagueSpec("soccer", "usa.1", "MLS", setOf("mls", "major league soccer")),
            LeagueSpec("soccer", "eng.1", "Premier League", setOf("premier league", "epl")),
            LeagueSpec("soccer", "uefa.champions", "UEFA Champions League", setOf("champions league", "ucl")),
            LeagueSpec("soccer", "esp.1", "LaLiga", setOf("laliga", "la liga")),
            LeagueSpec("soccer", "ger.1", "Bundesliga", setOf("bundesliga")),
            LeagueSpec("soccer", "ita.1", "Serie A", setOf("serie a")),
            LeagueSpec("soccer", "fra.1", "Ligue 1", setOf("ligue 1")),
            LeagueSpec("racing", "f1", "Formula 1", setOf("formula 1", "formula one", "f1")),
            LeagueSpec("mma", "ufc", "UFC", setOf("ufc")),
            LeagueSpec("tennis", "atp", "ATP", setOf("atp")),
            LeagueSpec("tennis", "wta", "WTA", setOf("wta")),
            LeagueSpec("golf", "pga", "PGA", setOf("pga")),
        )

        fun WORD_BOUNDARY(value: String): Regex = Regex("(?:^|\\b)${Regex.escape(value)}(?:\\b|$)")

        fun normalizeText(value: String): String = value.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()

        fun semanticTokens(value: String): Set<String> = normalizeText(value).split(' ')
            .filter { it.length >= 2 && it !in GENERIC_TOKENS }
            .toSet()

        fun firstString(json: JSONObject?, vararg keys: String): String? {
            if (json == null) return null
            return keys.asSequence()
                .map { key -> json.optString(key).clean(260) }
                .firstOrNull(String::isNotBlank)
        }

        fun String.clean(maxChars: Int): String = replace(Regex("\\s+"), " ").trim().take(maxChars)

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .callTimeout(6, TimeUnit.SECONDS)
            .build()
    }
}

private suspend fun Call.awaitEspnResponse(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, error: IOException) {
            continuation.resumeWithException(error)
        }

        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response) { _, resource, _ -> resource.close() }
        }
    })
}

package com.ad_glasses.ai.grounding

import android.content.Context
import android.os.SystemClock
import android.util.Log
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException

data class GroundingToolResult(
    val fallbackAnswer: String? = null,
    val contextText: String = "",
    val sources: List<GroundingSource> = emptyList(),
    val tavilyUsed: Boolean = false,
    val weatherUsed: Boolean = false,
    val osmUsed: Boolean = false,
) {
    fun appendAttribution(answer: String): String = buildString {
        append(answer.trim())
        if (sources.isNotEmpty()) {
            append("\n\nSources:\n")
            sources.distinctBy { it.url }.take(4).forEachIndexed { index, source ->
                append("[${index + 1}] ${source.title.ifBlank { source.url }} — ${source.url}\n")
            }
        }
        if (osmUsed) {
            append("\n")
            append(OsmServiceClient.OSM_ATTRIBUTION)
        }
    }.trim()
}

private data class SpatialExecution(
    val answer: String,
    val context: String,
    val point: GeoPoint? = null,
    val coarseArea: String? = null,
    /** Public place names only; never coordinates. Safe as external-search hints for BOTH. */
    val searchHints: List<String> = emptyList(),
)

private data class ExternalExecution(
    val answer: String?,
    val context: String,
    val sources: List<GroundingSource>,
    val tavilyUsed: Boolean = false,
    val weatherUsed: Boolean = false,
    /** If true and this is a plain SEARCH, skip the second AD synthesis call. */
    val directPreferred: Boolean = false,
)

/** Executes the validated semantic plan produced by [GroundingIntentRouter]. */
class AssistantToolService(context: Context) {
    private val appContext = context.applicationContext
    private val locationProvider = AndroidLocationProvider(appContext)
    private val tavily = TavilySearchClient(appContext)
    private val news = GoogleNewsRssClient()
    private val sports = EspnSportsClient()
    private val weather = OpenMeteoWeatherClient()
    private val wikipedia = WikipediaKnowledgeClient()
    private val dictionary = FreeDictionaryClient()
    private val currency = FrankfurterCurrencyClient()
    private val books = OpenLibraryKnowledgeClient()
    private val translation = LocalTranslationClient()
    private val osm = OsmServiceClient { GroundingPrefs.getConfig(appContext) }

    suspend fun execute(route: GroundingRoute): Result<GroundingToolResult> = try {
        val startedAt = SystemClock.elapsedRealtime()
        val needsSpatial = route.intent == GroundingIntent.SPATIAL || route.intent == GroundingIntent.BOTH
        val needsExternal = route.intent == GroundingIntent.SEARCH || route.intent == GroundingIntent.BOTH

        // BOTH is ordered deliberately: spatial resolution comes first so a later web lookup can use
        // public candidate names/coarse area while exact coordinates and street address remain local.
        var spatialError: Throwable? = null
        val spatial = if (needsSpatial) {
            try {
                executeSpatial(route)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                spatialError = error
                null
            }
        } else {
            null
        }

        var externalError: Throwable? = null
        val external = if (needsExternal) {
            try {
                executeExternal(route, spatial)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                externalError = error
                null
            }
        } else {
            null
        }

        when (route.intent) {
            GroundingIntent.SEARCH -> if (external == null) throw externalError
                ?: IllegalStateException("External lookup failed.")
            GroundingIntent.SPATIAL -> if (spatial == null) throw spatialError
                ?: IllegalStateException("Spatial lookup failed.")
            GroundingIntent.BOTH -> if (external == null && spatial == null) {
                throw externalError ?: spatialError ?: IllegalStateException("Both tool lookups failed.")
            }
            GroundingIntent.DIRECT -> Unit
        }

        val directExternalSearch = route.intent == GroundingIntent.SEARCH &&
            !route.synthesize &&
            external?.directPreferred == true &&
            !external.answer.isNullOrBlank()

        val partialContext = buildString {
            if (route.intent == GroundingIntent.BOTH && spatialError != null) {
                appendLine("Spatial lookup failed. Do not claim nearby/location/routing data was retrieved.")
            }
            if (route.intent == GroundingIntent.BOTH && externalError != null) {
                appendLine("External lookup failed. Do not claim current web/public-data facts were retrieved.")
            }
        }.trim()
        val baseContext = if (directExternalSearch) "" else buildSynthesisContext(external, spatial)
        val contextText = listOf(baseContext, partialContext)
            .filter(String::isNotBlank)
            .joinToString("\n")
            .take(MAX_SYNTHESIS_CONTEXT_CHARS)

        val partialFallback = when {
            route.intent != GroundingIntent.BOTH -> null
            spatialError != null && external != null -> "I couldn't complete the requested location lookup."
            externalError != null && spatial != null -> "I couldn't fetch the requested external details."
            else -> null
        }
        val fallbackAnswer = listOfNotNull(
            external?.answer?.trim()?.takeIf(String::isNotBlank),
            spatial?.answer?.trim()?.takeIf(String::isNotBlank),
            partialFallback,
        ).joinToString(" ").trim().takeIf(String::isNotBlank)?.take(MAX_FALLBACK_ANSWER_CHARS)

        Log.i(
            TAG,
            "tools_done intent=${route.intent.name.lowercase()} external=${route.externalTool.name.lowercase()} " +
                "tavily=${external?.tavilyUsed == true} weather=${external?.weatherUsed == true} osm=${spatial != null} " +
                "spatialFailed=${spatialError != null} externalFailed=${externalError != null} " +
                "directExternal=$directExternalSearch contextChars=${contextText.length} " +
                "elapsedMs=${SystemClock.elapsedRealtime() - startedAt}",
        )
        Result.success(
            GroundingToolResult(
                fallbackAnswer = fallbackAnswer,
                contextText = contextText,
                sources = external?.sources.orEmpty(),
                tavilyUsed = external?.tavilyUsed == true,
                weatherUsed = external?.weatherUsed == true,
                osmUsed = spatial != null,
            ),
        )
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Log.w(TAG, "tools_failed type=${error::class.java.simpleName}")
        Result.failure(error)
    }

    private suspend fun executeExternal(
        route: GroundingRoute,
        spatial: SpatialExecution?,
    ): ExternalExecution = when (route.externalTool) {
        ExternalTool.TAVILY -> executeTavily(route, spatial)
        ExternalTool.NEWS -> executeNews(route, spatial)
        ExternalTool.SPORTS -> executeSports(route, spatial)
        ExternalTool.WEATHER -> executeWeather(route, spatial)
        ExternalTool.WIKIPEDIA -> executeWikipedia(route, spatial)
        ExternalTool.DICTIONARY -> executeDictionary(route, spatial)
        ExternalTool.CURRENCY -> executeCurrency(route, spatial)
        ExternalTool.BOOKS -> executeBooks(route, spatial)
        ExternalTool.TRANSLATION -> executeTranslation(route)
    }

    private suspend fun executeTavily(
        route: GroundingRoute,
        spatial: SpatialExecution?,
    ): ExternalExecution {
        check(tavily.isConfigured()) { "Tavily search is disabled or has no API key." }
        val baseQuery = route.searchQuery?.trim().orEmpty()
        require(baseQuery.isNotBlank()) { "The routed Tavily query is blank." }
        val query = buildString {
            append(baseQuery)
            spatial?.searchHints?.take(MAX_SPATIAL_SEARCH_HINTS)?.takeIf { it.isNotEmpty() }?.let { hints ->
                append(". Relevant OpenStreetMap candidates: ")
                append(hints.joinToString("; "))
            }
            spatial?.coarseArea?.takeIf { it.isNotBlank() }?.let { area ->
                append(". User area: $area")
            }
        }.take(MAX_TAVILY_QUERY_CHARS)

        val first = tavily.search(
            query = query,
            depth = TavilySearchDepth.FAST,
            maxResults = PRIMARY_TAVILY_RESULTS,
            topic = route.tavilyTopic,
            timeRange = route.tavilyTimeRange,
            includeAnswer = true,
            includeDomains = route.sourceDomains,
        ).getOrThrow()

        val chosen = if (first.results.isEmpty()) {
            tavily.search(
                query = query,
                depth = TavilySearchDepth.FAST,
                maxResults = FALLBACK_TAVILY_RESULTS,
                topic = TavilySearchTopic.GENERAL,
                timeRange = route.tavilyTimeRange,
                includeAnswer = true,
                includeDomains = route.sourceDomains,
            ).getOrNull() ?: first
        } else {
            first
        }
        if (chosen.results.isEmpty()) {
            throw IllegalStateException("Tavily returned no supporting search results after the bounded retry.")
        }
        val relevant = selectRelevantResults(chosen.results)
        val sources = relevant.map { GroundingSource(it.title, it.url) }
        val snippetBudget = if (chosen.answer.isNullOrBlank()) {
            TAVILY_RESCUE_SNIPPET_CONTEXT_CHARS
        } else {
            TAVILY_SNIPPET_CONTEXT_CHARS
        }
        val context = buildString {
            chosen.answer?.takeIf { it.isNotBlank() }?.let {
                appendLine("Tavily LLM answer: ${it.take(TAVILY_ANSWER_CONTEXT_CHARS)}")
            }
            appendLine("Tavily supporting evidence:")
            relevant.forEachIndexed { index, item ->
                append("[${index + 1}] ${item.title.take(160)}")
                if (item.content.isNotBlank()) append(": ${item.content.take(snippetBudget)}")
                appendLine()
            }
        }.trim().take(MAX_EXTERNAL_CONTEXT_CHARS)
        return ExternalExecution(
            answer = chosen.answer,
            context = context,
            sources = sources,
            tavilyUsed = true,
            directPreferred = !chosen.answer.isNullOrBlank(),
        )
    }

    private fun selectRelevantResults(results: List<TavilySearchResult>): List<TavilySearchResult> {
        val ranked = results.sortedByDescending(TavilySearchResult::score)
        if (ranked.isEmpty()) return emptyList()
        val bestScore = ranked.first().score
        if (bestScore <= 0.0) return ranked.take(MAX_SOURCES)
        val cutoff = maxOf(MIN_ABSOLUTE_RESULT_SCORE, bestScore * RELATIVE_RESULT_SCORE_RATIO)
        return ranked.filter { it.score >= cutoff }.take(MAX_SOURCES).ifEmpty { ranked.take(1) }
    }

    private suspend fun executeNews(route: GroundingRoute, spatial: SpatialExecution?): ExternalExecution {
        val query = route.searchQuery?.trim()?.takeIf(String::isNotBlank)
        val rss = news.lookup(query).getOrNull()
        if (rss != null) return rss.toExternalExecution()
        val fallbackQuery = query ?: "top news today"
        return fallbackStructuredToTavily(
            route = route,
            spatial = spatial,
            query = fallbackQuery,
            topic = TavilySearchTopic.NEWS,
            label = "google_news_rss",
            timeRange = TavilyTimeRange.DAY,
        )
    }

    private suspend fun executeSports(route: GroundingRoute, spatial: SpatialExecution?): ExternalExecution {
        val query = route.searchQuery?.trim()?.takeIf(String::isNotBlank)
        if (query == null) {
            val headlines = sports.lookup(null).getOrNull()
            if (headlines != null) return headlines.toExternalExecution()
        }

        // ESPN's public RSS is excellent for broad headlines but is not a universal live-score API.
        // A specific sports question therefore searches ESPN itself through Tavily. No sport names or
        // leagues are hardcoded here; the router supplies the standalone semantic query.
        if (tavily.isConfigured() && query != null) {
            return executeTavily(
                route.copy(
                    externalTool = ExternalTool.TAVILY,
                    searchQuery = query,
                    tavilyTopic = TavilySearchTopic.NEWS,
                    tavilyTimeRange = TavilyTimeRange.DAY,
                    sourceDomains = ESPN_DOMAINS,
                ),
                spatial,
            )
        }

        val rssFallback = sports.lookup(query).getOrNull()
        if (rssFallback != null) return rssFallback.toExternalExecution()
        throw IllegalStateException("ESPN sports lookup failed and ESPN web search is unavailable.")
    }

    private suspend fun executeWeather(
        route: GroundingRoute,
        spatial: SpatialExecution?,
    ): ExternalExecution {
        val (point, label) = resolveWeatherPoint(route, spatial)
        val weatherResult = weather.forecast(point)
        val snapshot = weatherResult.getOrNull()
        if (snapshot != null) {
            val fallback = buildString {
                append(snapshot.fallbackAnswer(route.weatherHorizon))
                label?.takeIf { it.isNotBlank() }?.let { append(" For $it.") }
            }.trim()
            return ExternalExecution(
                answer = fallback,
                context = buildString {
                    label?.let { appendLine("Weather location: $it.") }
                    append(snapshot.contextText(route.weatherHorizon))
                }.take(MAX_EXTERNAL_CONTEXT_CHARS),
                sources = listOf(GroundingSource("Weather data by Open-Meteo", OpenMeteoWeatherClient.SOURCE_URL)),
                weatherUsed = true,
            )
        }

        val failure = weatherResult.exceptionOrNull() ?: IllegalStateException("Open-Meteo weather request failed.")
        if (!tavily.isConfigured()) throw failure
        val fallbackQuery = buildString {
            append(route.searchQuery?.trim()?.takeIf { it.isNotBlank() } ?: "weather forecast")
            label?.takeIf { it.isNotBlank() }?.let { append(". Location: $it") }
        }.take(MAX_TAVILY_QUERY_CHARS)
        Log.w(TAG, "weather_fallback_to_tavily type=${failure::class.java.simpleName}")
        return executeTavily(
            route.copy(
                externalTool = ExternalTool.TAVILY,
                searchQuery = fallbackQuery,
                tavilyTopic = TavilySearchTopic.GENERAL,
                tavilyTimeRange = route.tavilyTimeRange ?: TavilyTimeRange.DAY,
            ),
            spatial = null,
        )
    }

    private suspend fun executeWikipedia(route: GroundingRoute, spatial: SpatialExecution?): ExternalExecution {
        val query = route.searchQuery?.trim().orEmpty()
        val result = wikipedia.lookup(query, route.sourceLanguage ?: "en").getOrNull()
        if (result != null) return result.toExternalExecution()
        return fallbackStructuredToTavily(
            route = route,
            spatial = spatial,
            query = query,
            topic = TavilySearchTopic.GENERAL,
            label = "wikipedia",
        )
    }

    private suspend fun executeDictionary(route: GroundingRoute, spatial: SpatialExecution?): ExternalExecution {
        val query = route.searchQuery?.trim().orEmpty()
        val result = dictionary.lookup(query, route.sourceLanguage ?: "en").getOrNull()
        if (result != null) return result.toExternalExecution()
        return fallbackStructuredToTavily(
            route = route,
            spatial = spatial,
            query = "$query definition pronunciation",
            topic = TavilySearchTopic.GENERAL,
            label = "dictionary",
        )
    }

    private suspend fun executeCurrency(route: GroundingRoute, spatial: SpatialExecution?): ExternalExecution {
        val amount = route.currencyAmount ?: error("Currency amount is missing.")
        val base = route.baseCurrency ?: error("Currency base is missing.")
        val quote = route.quoteCurrency ?: error("Currency quote is missing.")
        val result = currency.convert(amount, base, quote).getOrNull()
        if (result != null) return result.toExternalExecution()
        return fallbackStructuredToTavily(
            route = route,
            spatial = spatial,
            query = "$amount $base to $quote current exchange rate",
            topic = TavilySearchTopic.FINANCE,
            label = "currency",
        )
    }

    private suspend fun executeBooks(route: GroundingRoute, spatial: SpatialExecution?): ExternalExecution {
        val query = route.searchQuery?.trim().orEmpty()
        val result = books.lookup(query).getOrNull()
        if (result != null) return result.toExternalExecution()
        return fallbackStructuredToTavily(
            route = route,
            spatial = spatial,
            query = query,
            topic = TavilySearchTopic.GENERAL,
            label = "books",
        )
    }

    private suspend fun executeTranslation(route: GroundingRoute): ExternalExecution {
        val text = route.translationText ?: error("Translation text is missing.")
        val target = route.targetLanguage ?: error("Translation target language is missing.")
        val result = translation.translate(text, route.sourceLanguage, target).getOrThrow()
        return result.toExternalExecution(directPreferred = true)
    }

    private suspend fun fallbackStructuredToTavily(
        route: GroundingRoute,
        spatial: SpatialExecution?,
        query: String,
        topic: TavilySearchTopic,
        label: String,
        timeRange: TavilyTimeRange? = route.tavilyTimeRange,
    ): ExternalExecution {
        check(tavily.isConfigured()) { "$label lookup failed and Tavily fallback is unavailable." }
        Log.w(TAG, "${label}_fallback_to_tavily")
        return executeTavily(
            route.copy(
                externalTool = ExternalTool.TAVILY,
                searchQuery = query,
                tavilyTopic = topic,
                tavilyTimeRange = timeRange,
                sourceDomains = emptyList(),
            ),
            spatial,
        )
    }

    private fun StructuredKnowledgeResult.toExternalExecution(directPreferred: Boolean = false): ExternalExecution =
        ExternalExecution(
            answer = answer,
            context = context.take(MAX_EXTERNAL_CONTEXT_CHARS),
            sources = sources,
            directPreferred = directPreferred,
        )

    private suspend fun resolveWeatherPoint(
        route: GroundingRoute,
        spatial: SpatialExecution?,
    ): Pair<GeoPoint, String?> {
        spatial?.point?.let { return it to spatial.coarseArea }
        if (route.useCurrentLocation) {
            val fix = requireCurrentFix()
            val address = osm.reverse(fix.point).getOrNull()
            return fix.point to coarseArea(address)
        }
        val reference = route.referencePlace?.trim()?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Weather location is missing.")
        val place = osm.geocode(reference, null).getOrThrow()
            ?: throw IllegalStateException("OpenStreetMap could not resolve the requested weather location.")
        return place.point to place.name
    }

    private suspend fun executeSpatial(plan: GroundingRoute): SpatialExecution =
        when (plan.spatialAction ?: error("Spatial action is missing.")) {
            SpatialAction.LOCATION -> location(plan)
            SpatialAction.NEARBY -> nearby(plan)
            SpatialAction.ROUTE -> executeRoute(plan)
        }

    private suspend fun location(plan: GroundingRoute): SpatialExecution {
        if (!plan.useCurrentLocation && !plan.referencePlace.isNullOrBlank()) {
            val place = osm.geocode(plan.referencePlace, null).getOrThrow()
                ?: throw IllegalStateException("OpenStreetMap could not resolve the requested location.")
            return SpatialExecution(
                answer = "${plan.referencePlace} resolves to ${place.name}.",
                context = "OpenStreetMap resolved location: ${place.name}; ${place.category}.",
                point = place.point,
                coarseArea = plan.referencePlace,
                searchHints = listOf(place.name),
            )
        }

        val fix = requireCurrentFix()
        val address = osm.reverse(fix.point).getOrNull()
        val display = address?.displayName?.takeIf { it.isNotBlank() }
            ?: String.format(Locale.US, "%.5f, %.5f", fix.point.latitude, fix.point.longitude)
        val area = coarseArea(address)
        val preciseLocationAnswer = plan.intent == GroundingIntent.SPATIAL
        val answer = if (preciseLocationAnswer) {
            buildString {
                append("Your current location is $display")
                fix.accuracyMeters?.let { append(", with GPS accuracy about ${it.roundToInt()} metres") }
                append('.')
            }
        } else {
            "Your current area is ${area ?: address?.country ?: "available locally"}."
        }
        val context = if (preciseLocationAnswer) {
            "Current location from Android GPS/OpenStreetMap: $display."
        } else {
            "Approximate current area from Android GPS/OpenStreetMap: ${area ?: address?.country ?: "unavailable"}. Exact coordinates and street address are intentionally omitted."
        }
        return SpatialExecution(answer = answer, context = context, point = fix.point, coarseArea = area)
    }

    private suspend fun nearby(plan: GroundingRoute): SpatialExecution {
        val radius = (plan.radiusMeters ?: DEFAULT_NEARBY_RADIUS_METERS).coerceIn(50, 5_000)
        val center = resolveNearbyCenter(plan)
        val query = plan.spatialQuery?.trim().orEmpty()

        val overpassPlaces = if (plan.osmFilters.isNotEmpty()) {
            osm.nearby(
                origin = center.first,
                filters = plan.osmFilters,
                radiusMeters = radius,
                limit = MAX_NEARBY_RESULTS,
            ).getOrNull().orEmpty()
        } else {
            emptyList()
        }
        val places = if (overpassPlaces.isNotEmpty()) {
            overpassPlaces
        } else if (query.isNotBlank()) {
            val fallback = osm.geocode(query, center.first).getOrNull()
            listOfNotNull(fallback?.takeIf { it.distanceMeters <= radius })
        } else {
            emptyList()
        }

        val target = query.ifBlank { "matching places" }
        val answer = if (places.isNotEmpty()) {
            buildString {
                append("I found ${places.size} $target match")
                if (places.size != 1) append("es")
                append(" within about ${formatDistance(radius)}")
                append(": ")
                append(
                    places.take(MAX_SPOKEN_NEARBY_RESULTS).joinToString("; ") { place ->
                        "${place.name}, about ${formatDistance(place.distanceMeters)} away"
                    },
                )
                append('.')
            }
        } else {
            "I couldn't find a matching $target within about ${formatDistance(radius)} of ${center.second}."
        }
        val context = if (places.isNotEmpty()) {
            buildString {
                appendLine("OpenStreetMap nearby matches within $radius m of ${center.second}:")
                places.take(MAX_NEARBY_RESULTS).forEachIndexed { index, place ->
                    appendLine("[${index + 1}] ${place.name}; ${place.category}; ${place.distanceMeters} m away.")
                }
            }.trim().take(MAX_SPATIAL_CONTEXT_CHARS)
        } else {
            "OpenStreetMap returned no matching '$target' within $radius m of ${center.second}. Do not invent a nearby match."
        }
        val area = if (plan.useCurrentLocation) {
            osm.reverse(center.first).getOrNull()?.let(::coarseArea)
        } else {
            plan.referencePlace
        }
        return SpatialExecution(
            answer = answer,
            context = context,
            point = places.firstOrNull()?.point ?: center.first,
            coarseArea = area,
            searchHints = places.map(OsmPlace::name).distinct().take(MAX_SPATIAL_SEARCH_HINTS),
        )
    }

    private suspend fun executeRoute(plan: GroundingRoute): SpatialExecution {
        val originPoint: GeoPoint
        val originLabel: String
        val explicitOrigin = plan.routeOrigin?.trim().orEmpty()
        val originUsesCurrent = plan.useCurrentLocation || explicitOrigin.isBlank() ||
            explicitOrigin.equals("current_location", ignoreCase = true) ||
            explicitOrigin.equals("current location", ignoreCase = true)
        if (originUsesCurrent) {
            val fix = requireCurrentFix()
            originPoint = fix.point
            originLabel = "your current location"
        } else {
            val origin = osm.geocode(explicitOrigin, null).getOrThrow()
                ?: throw IllegalStateException("OpenStreetMap could not resolve the route origin.")
            originPoint = origin.point
            originLabel = origin.name
        }

        val destinationQuery = plan.routeDestination?.takeIf { it.isNotBlank() }
            ?: plan.spatialQuery?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Route destination is blank.")
        val destination = osm.geocode(destinationQuery, originPoint).getOrThrow()
            ?: throw IllegalStateException("OpenStreetMap could not resolve the route destination.")
        val route = osm.route(originPoint, destination.point, plan.routeMode).getOrThrow()
        val mode = plan.routeMode.name.lowercase(Locale.US)
        val answer = buildString {
            append("The $mode route from $originLabel to ${destination.name} is ${formatDistance(route.distanceMeters)}, about ${formatDuration(route.durationSeconds)}")
            route.steps.firstOrNull()?.let { append(". Start by ${it.instruction.lowercaseFirst()}") }
            append('.')
        }
        val context = buildString {
            append("OSRM $mode route from $originLabel to ${destination.name}: ${route.distanceMeters} m, ${route.durationSeconds} sec.")
            route.steps.take(6).forEachIndexed { index, step ->
                append(" Step ${index + 1}: ${step.instruction}; ${step.distanceMeters} m.")
            }
        }.take(MAX_SPATIAL_CONTEXT_CHARS)
        val area = if (originUsesCurrent) osm.reverse(originPoint).getOrNull()?.let(::coarseArea) else null
        return SpatialExecution(
            answer = answer,
            context = context,
            point = destination.point,
            coarseArea = area,
            searchHints = listOf(destination.name),
        )
    }

    private suspend fun resolveNearbyCenter(plan: GroundingRoute): Pair<GeoPoint, String> {
        val reference = plan.referencePlace?.trim()?.takeIf { it.isNotBlank() }
        if (reference != null && !plan.useCurrentLocation) {
            val place = osm.geocode(reference, null).getOrThrow()
                ?: throw IllegalStateException("OpenStreetMap could not resolve the nearby-search reference place.")
            return place.point to place.name
        }
        val fix = requireCurrentFix()
        return fix.point to "your current location"
    }

    private suspend fun requireCurrentFix(): GeoFix =
        locationProvider.currentFix()
            ?: throw IllegalStateException("Android location permission or a current location fix is unavailable.")

    private fun buildSynthesisContext(
        external: ExternalExecution?,
        spatial: SpatialExecution?,
    ): String = buildString {
        external?.context?.takeIf { it.isNotBlank() }?.let { appendLine(it) }
        spatial?.let {
            appendLine("OpenStreetMap/OSRM facts: ${it.context.take(MAX_SPATIAL_CONTEXT_CHARS)}")
        }
    }.trim().take(MAX_SYNTHESIS_CONTEXT_CHARS)

    private fun coarseArea(address: OsmAddress?): String? {
        if (address == null) return null
        val parts = listOf(address.neighbourhood, address.city, address.state, address.country)
            .mapNotNull { it?.replace(Regex("\\s+"), " ")?.trim()?.takeIf(String::isNotBlank) }
            .distinct()
        return parts.takeIf { it.isNotEmpty() }?.joinToString(", ")?.take(220)
    }

    private fun formatDistance(meters: Int): String =
        if (meters < 1_000) "$meters metres" else String.format(Locale.US, "%.1f km", meters / 1_000.0)

    private fun formatDuration(seconds: Int): String = when {
        seconds < 90 -> "${seconds.coerceAtLeast(0)} seconds"
        seconds < 3_600 -> "${(seconds / 60.0).roundToInt()} minutes"
        else -> String.format(Locale.US, "%.1f hours", seconds / 3_600.0)
    }

    private fun String.lowercaseFirst(): String =
        replaceFirstChar { if (it.isUpperCase()) it.lowercase() else it.toString() }

    private companion object {
        const val TAG = "AssistantGrounding"
        val ESPN_DOMAINS = listOf("espn.com", "espn.in")
        const val DEFAULT_NEARBY_RADIUS_METERS = 1_000
        const val PRIMARY_TAVILY_RESULTS = 3
        const val FALLBACK_TAVILY_RESULTS = 5
        const val MAX_SOURCES = 4
        const val MAX_NEARBY_RESULTS = 6
        const val MAX_SPOKEN_NEARBY_RESULTS = 3
        const val MAX_SPATIAL_SEARCH_HINTS = 4
        const val MAX_FALLBACK_ANSWER_CHARS = 2_200
        const val MAX_TAVILY_QUERY_CHARS = 1_200
        const val TAVILY_ANSWER_CONTEXT_CHARS = 1_000
        const val TAVILY_SNIPPET_CONTEXT_CHARS = 320
        const val TAVILY_RESCUE_SNIPPET_CONTEXT_CHARS = 850
        const val MAX_EXTERNAL_CONTEXT_CHARS = 1_700
        const val MAX_SPATIAL_CONTEXT_CHARS = 1_200
        const val MAX_SYNTHESIS_CONTEXT_CHARS = 3_000
        const val MIN_ABSOLUTE_RESULT_SCORE = 0.15
        const val RELATIVE_RESULT_SCORE_RATIO = 0.55
    }
}

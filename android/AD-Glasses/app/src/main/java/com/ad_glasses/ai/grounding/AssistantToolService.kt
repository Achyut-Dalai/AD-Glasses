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
)

private data class ExternalExecution(
    val answer: String?,
    val context: String,
    val sources: List<GroundingSource>,
    val tavilyUsed: Boolean = false,
    val weatherUsed: Boolean = false,
)

/** Executes the small, validated plan produced by [GroundingIntentRouter]. */
class AssistantToolService(context: Context) {
    private val appContext = context.applicationContext
    private val locationProvider = AndroidLocationProvider(appContext)
    private val tavily = TavilySearchClient(appContext)
    private val weather = OpenMeteoWeatherClient()
    private val osm = OsmServiceClient { GroundingPrefs.getConfig(appContext) }

    suspend fun execute(route: GroundingRoute): Result<GroundingToolResult> = try {
        val startedAt = SystemClock.elapsedRealtime()
        val needsSpatial = route.intent == GroundingIntent.SPATIAL || route.intent == GroundingIntent.BOTH
        val needsExternal = route.intent == GroundingIntent.SEARCH || route.intent == GroundingIntent.BOTH

        // BOTH is intentionally ordered. Spatial resolution happens first so Tavily can receive at
        // most a coarse area label rather than GPS coordinates or a precise street address.
        val spatial = if (needsSpatial) executeSpatial(route) else null
        val external = if (needsExternal) {
            when (route.externalTool) {
                ExternalTool.TAVILY -> executeTavily(route, spatial)
                ExternalTool.WEATHER -> executeWeather(route, spatial)
            }
        } else {
            null
        }

        val contextText = buildSynthesisContext(external, spatial)
        val fallbackAnswer = listOfNotNull(
            external?.answer?.trim()?.takeIf(String::isNotBlank),
            spatial?.answer?.trim()?.takeIf(String::isNotBlank),
        ).joinToString(" ").trim().takeIf(String::isNotBlank)?.take(MAX_FALLBACK_ANSWER_CHARS)
        Log.i(
            TAG,
            "tools_done intent=${route.intent.name.lowercase()} external=${route.externalTool.name.lowercase()} " +
                "tavily=${external?.tavilyUsed == true} weather=${external?.weatherUsed == true} osm=${spatial != null} " +
                "contextChars=${contextText.length} elapsedMs=${SystemClock.elapsedRealtime() - startedAt}",
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

    private suspend fun executeTavily(
        route: GroundingRoute,
        spatial: SpatialExecution?,
    ): ExternalExecution {
        check(tavily.isConfigured()) { "Tavily search is disabled or has no API key." }
        val baseQuery = route.searchQuery?.trim().orEmpty()
        require(baseQuery.isNotBlank()) { "The routed Tavily query is blank." }
        val query = spatial?.coarseArea?.let { area -> "$baseQuery. User area: $area" } ?: baseQuery

        val first = tavily.search(
            query = query,
            depth = TavilySearchDepth.FAST,
            maxResults = PRIMARY_TAVILY_RESULTS,
            topic = route.tavilyTopic,
            timeRange = route.tavilyTimeRange,
            includeAnswer = true,
            includeDomains = route.sourceDomains,
        ).getOrThrow()

        val chosen = if (first.answer.isNullOrBlank() || first.results.isEmpty()) {
            val retryTopic = if (route.tavilyTopic == TavilySearchTopic.GENERAL) {
                TavilySearchTopic.GENERAL
            } else {
                TavilySearchTopic.GENERAL
            }
            val retry = tavily.search(
                query = query,
                depth = TavilySearchDepth.FAST,
                maxResults = FALLBACK_TAVILY_RESULTS,
                topic = retryTopic,
                timeRange = route.tavilyTimeRange,
                includeAnswer = true,
                includeDomains = route.sourceDomains,
            ).getOrNull()
            when {
                retry != null && retry.results.isNotEmpty() && !retry.answer.isNullOrBlank() -> retry
                first.results.isNotEmpty() -> first
                retry != null && retry.results.isNotEmpty() -> retry
                else -> first
            }
        } else {
            first
        }

        if (chosen.results.isEmpty()) {
            throw IllegalStateException("Tavily returned no supporting search results after the bounded retry.")
        }
        val sources = chosen.results.take(MAX_SOURCES).map { GroundingSource(it.title, it.url) }
        val context = buildString {
            chosen.answer?.takeIf { it.isNotBlank() }?.let { appendLine("Tavily LLM answer: ${it.take(TAVILY_ANSWER_CONTEXT_CHARS)}") }
            appendLine("Tavily supporting evidence:")
            chosen.results.take(MAX_SOURCES).forEachIndexed { index, item ->
                append("[${index + 1}] ${item.title.take(160)}")
                if (item.content.isNotBlank()) append(": ${item.content.take(TAVILY_SNIPPET_CONTEXT_CHARS)}")
                appendLine()
            }
        }.trim()
        return ExternalExecution(
            answer = chosen.answer,
            context = context,
            sources = sources,
            tavilyUsed = true,
        )
    }

    private suspend fun executeWeather(
        route: GroundingRoute,
        spatial: SpatialExecution?,
    ): ExternalExecution {
        val (point, label) = resolveWeatherPoint(route, spatial)
        val snapshot = weather.forecast(point).getOrThrow()
        val fallback = buildString {
            append(snapshot.fallbackAnswer(route.weatherHorizon))
            label?.takeIf { it.isNotBlank() }?.let { append(" For $it.") }
        }.trim()
        return ExternalExecution(
            answer = fallback,
            context = buildString {
                label?.let { appendLine("Weather location: $it.") }
                append(snapshot.contextText(route.weatherHorizon))
            }.take(MAX_WEATHER_CONTEXT_CHARS),
            sources = listOf(GroundingSource("Open-Meteo weather", OpenMeteoWeatherClient.SOURCE_URL)),
            weatherUsed = true,
        )
    }

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
        require(query.isNotBlank()) { "Nearby-place query is blank." }
        val place = osm.geocode(query, center.first).getOrThrow()
        val match = place?.takeIf { it.distanceMeters <= radius }
        val answer = if (match != null) {
            "${match.name} is about ${formatDistance(match.distanceMeters)} from ${center.second}."
        } else {
            "I couldn't find a matching $query within about ${formatDistance(radius)} of ${center.second}."
        }
        val context = if (match != null) {
            "OpenStreetMap nearby match: ${match.name}; ${match.category}; about ${match.distanceMeters} m from ${center.second}."
        } else {
            "OpenStreetMap returned no matching '$query' within $radius m of ${center.second}. Do not invent a nearby match."
        }
        val area = if (plan.useCurrentLocation) {
            osm.reverse(center.first).getOrNull()?.let(::coarseArea)
        } else {
            plan.referencePlace
        }
        return SpatialExecution(answer = answer, context = context, point = match?.point ?: center.first, coarseArea = area)
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
        return SpatialExecution(answer = answer, context = context, point = destination.point, coarseArea = area)
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
        external?.context?.takeIf { it.isNotBlank() }?.let {
            appendLine(it)
        }
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
        const val DEFAULT_NEARBY_RADIUS_METERS = 1_000
        const val PRIMARY_TAVILY_RESULTS = 3
        const val FALLBACK_TAVILY_RESULTS = 5
        const val MAX_SOURCES = 4
        const val MAX_FALLBACK_ANSWER_CHARS = 2_200
        const val TAVILY_ANSWER_CONTEXT_CHARS = 1_000
        const val TAVILY_SNIPPET_CONTEXT_CHARS = 320
        const val MAX_WEATHER_CONTEXT_CHARS = 1_500
        const val MAX_SPATIAL_CONTEXT_CHARS = 1_200
        const val MAX_SYNTHESIS_CONTEXT_CHARS = 2_800
    }
}

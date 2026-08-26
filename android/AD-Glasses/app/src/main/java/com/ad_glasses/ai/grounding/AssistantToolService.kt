package com.ad_glasses.ai.grounding

import android.content.Context
import android.os.SystemClock
import android.util.Log
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException

data class GroundingToolResult(
    val directAnswer: String? = null,
    val contextText: String = "",
    val sources: List<GroundingSource> = emptyList(),
    val tavilyUsed: Boolean = false,
    val osmUsed: Boolean = false,
) {
    fun appendAttribution(answer: String): String = buildString {
        append(answer.trim())
        if (sources.isNotEmpty()) {
            append("\n\nSources:\n")
            sources.distinctBy { it.url }.take(3).forEachIndexed { index, source ->
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
    val coarseArea: String? = null,
)

/** Executes the small, validated plan produced by [GroundingIntentRouter]. */
class AssistantToolService(context: Context) {
    private val appContext = context.applicationContext
    private val locationProvider = AndroidLocationProvider(appContext)
    private val tavily = TavilySearchClient(appContext)
    private val osm = OsmServiceClient { GroundingPrefs.getConfig(appContext) }

    fun isTavilyConfigured(): Boolean = tavily.isConfigured()

    suspend fun execute(route: GroundingRoute): Result<GroundingToolResult> = try {
        val startedAt = SystemClock.elapsedRealtime()
        val needsSpatial = route.intent == GroundingIntent.SPATIAL || route.intent == GroundingIntent.BOTH
        val needsSearch = route.intent == GroundingIntent.SEARCH || route.intent == GroundingIntent.BOTH

        val spatial = if (needsSpatial) executeSpatial(route) else null
        val search = if (needsSearch) {
            check(tavily.isConfigured()) { "Tavily search is disabled or has no API key." }
            val baseQuery = route.searchQuery?.trim().orEmpty()
            require(baseQuery.isNotBlank()) { "The routed Tavily query is blank." }
            val query = spatial?.coarseArea?.let { area ->
                "$baseQuery. User area: $area"
            } ?: baseQuery
            tavily.search(
                query = query,
                depth = TavilySearchDepth.FAST,
                maxResults = 3,
                topic = route.tavilyTopic,
                timeRange = route.tavilyTimeRange,
                includeAnswer = true,
            ).getOrThrow()
        } else {
            null
        }

        if (search != null && (search.answer.isNullOrBlank() || search.results.isEmpty())) {
            throw IllegalStateException("Tavily did not return both an answer and supporting sources.")
        }

        val sources = search?.results
            ?.take(3)
            ?.map { GroundingSource(it.title, it.url) }
            .orEmpty()
        val contextText = buildSynthesisContext(search, spatial)
        val directAnswer = when {
            route.synthesize -> null
            route.intent == GroundingIntent.SEARCH -> search?.answer
            route.intent == GroundingIntent.SPATIAL -> spatial?.answer
            else -> null
        }
        Log.i(
            TAG,
            "tools_done intent=${route.intent.name.lowercase()} tavily=${search != null} osm=${spatial != null} " +
                "contextChars=${contextText.length} elapsedMs=${SystemClock.elapsedRealtime() - startedAt}",
        )
        Result.success(
            GroundingToolResult(
                directAnswer = directAnswer?.trim()?.take(MAX_DIRECT_ANSWER_CHARS),
                contextText = contextText,
                sources = sources,
                tavilyUsed = search != null,
                osmUsed = spatial != null,
            ),
        )
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Log.w(TAG, "tools_failed type=${error::class.java.simpleName}")
        Result.failure(error)
    }

    private suspend fun executeSpatial(route: GroundingRoute): SpatialExecution {
        return when (route.spatialAction ?: error("Spatial action is missing.")) {
            SpatialAction.LOCATION -> currentLocation()
            SpatialAction.NEARBY -> nearby(route)
            SpatialAction.ROUTE -> route(route)
        }
    }

    private suspend fun currentLocation(): SpatialExecution {
        val fix = requireCurrentFix()
        val address = osm.reverse(fix.point).getOrNull()
        val display = address?.displayName?.takeIf { it.isNotBlank() }
            ?: String.format(Locale.US, "%.5f, %.5f", fix.point.latitude, fix.point.longitude)
        val answer = buildString {
            append("Your current location is $display")
            fix.accuracyMeters?.let { append(", with GPS accuracy about ${it.roundToInt()} metres") }
            append('.')
        }
        return SpatialExecution(
            answer = answer,
            context = "Current location from Android GPS/OpenStreetMap: $display.",
            coarseArea = coarseArea(address),
        )
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
        return SpatialExecution(answer = answer, context = context, coarseArea = area)
    }

    private suspend fun route(plan: GroundingRoute): SpatialExecution {
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
        return SpatialExecution(answer = answer, context = context, coarseArea = area)
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
        search: TavilySearchResponse?,
        spatial: SpatialExecution?,
    ): String = buildString {
        if (search != null) {
            appendLine("Tavily LLM answer: ${search.answer.orEmpty().take(TAVILY_ANSWER_CONTEXT_CHARS)}")
            appendLine("Tavily supporting evidence:")
            search.results.take(3).forEachIndexed { index, item ->
                append("[${index + 1}] ${item.title.take(160)}")
                if (item.content.isNotBlank()) append(": ${item.content.take(TAVILY_SNIPPET_CONTEXT_CHARS)}")
                appendLine()
            }
        }
        if (spatial != null) {
            appendLine("OpenStreetMap/OSRM facts: ${spatial.context.take(MAX_SPATIAL_CONTEXT_CHARS)}")
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
        replaceFirstChar { if (it.isUpperCase()) it.lowercaseChar() else it }

    private companion object {
        const val TAG = "AssistantGrounding"
        const val DEFAULT_NEARBY_RADIUS_METERS = 1_000
        const val MAX_DIRECT_ANSWER_CHARS = 2_000
        const val TAVILY_ANSWER_CONTEXT_CHARS = 1_000
        const val TAVILY_SNIPPET_CONTEXT_CHARS = 320
        const val MAX_SPATIAL_CONTEXT_CHARS = 1_200
        const val MAX_SYNTHESIS_CONTEXT_CHARS = 2_400
    }
}

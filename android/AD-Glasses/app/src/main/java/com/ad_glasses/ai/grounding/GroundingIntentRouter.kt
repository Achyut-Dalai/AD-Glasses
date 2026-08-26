package com.ad_glasses.ai.grounding

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.ad_glasses.ai.router.AgentInferencePurpose
import com.ad_glasses.ai.router.AgentInferenceRouter
import com.ad_glasses.ai.router.CloudGenerationMode
import com.ad_glasses.shared.settings.AgentProviderType
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

enum class GroundingIntent {
    DIRECT,
    SEARCH,
    SPATIAL,
    BOTH,
}

enum class SpatialAction {
    NEARBY,
    ROUTE,
    LOCATION,
}

data class GroundingRoute(
    val intent: GroundingIntent,
    val searchQuery: String? = null,
    val tavilyTopic: TavilySearchTopic = TavilySearchTopic.GENERAL,
    val tavilyTimeRange: TavilyTimeRange? = null,
    val synthesize: Boolean = false,
    val spatialAction: SpatialAction? = null,
    val spatialQuery: String? = null,
    val radiusMeters: Int? = null,
    val useCurrentLocation: Boolean = true,
    val referencePlace: String? = null,
    val routeOrigin: String? = null,
    val routeDestination: String? = null,
    val routeMode: RouteMode = RouteMode.DRIVING,
)

/**
 * Provider-agnostic semantic router for public-web and spatial tools.
 *
 * The configured AD text model receives only the current utterance. Conversation history is never
 * sent to this classifier. The router decides whether the turn is DIRECT, SEARCH, SPATIAL, or BOTH
 * and extracts only the small set of parameters the host application can safely execute.
 */
class GroundingIntentRouter(context: Context) {
    private val appContext = context.applicationContext

    suspend fun route(
        prompt: String,
        sessionId: String,
        providerType: AgentProviderType,
        explicitWebRequest: Boolean? = null,
    ): Result<GroundingRoute> = try {
        val cleanPrompt = prompt.replace(Regex("\\s+"), " ").trim().take(MAX_PROMPT_CHARS)
        require(cleanPrompt.isNotBlank()) { "Grounding router prompt cannot be blank." }

        val startedAt = SystemClock.elapsedRealtime()
        val raw = AgentInferenceRouter.complete(
            context = appContext,
            purpose = AgentInferencePurpose.CLASSIFICATION,
            sessionId = "$sessionId-grounding-router",
            systemPrompt = ROUTER_SYSTEM_PROMPT,
            userPrompt = cleanPrompt,
            conversationMessages = emptyList(),
            providerType = providerType,
            onToken = null,
            webRequested = false,
            maxTokens = ROUTER_MAX_TOKENS,
            lowLatency = false,
            generationMode = CloudGenerationMode.CONCISE_CONVERSATION,
        )
        val parsed = parse(raw, cleanPrompt)
            ?: throw IllegalStateException("Grounding router returned an invalid classification.")
        val effective = applyExplicitWebPreference(parsed, cleanPrompt, explicitWebRequest)
        Log.i(
            TAG,
            "route_done intent=${effective.intent.name.lowercase()} topic=${effective.tavilyTopic.wire} " +
                "freshness=${effective.tavilyTimeRange?.wire ?: "none"} synthesize=${effective.synthesize} " +
                "spatial=${effective.spatialAction?.name?.lowercase() ?: "none"} elapsedMs=${SystemClock.elapsedRealtime() - startedAt}",
        )
        Result.success(effective)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Log.w(TAG, "route_failed type=${error::class.java.simpleName}")
        Result.failure(error)
    }

    internal fun parse(raw: String, originalPrompt: String): GroundingRoute? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val root = runCatching { JSONObject(raw.substring(start, end + 1)) }.getOrNull() ?: return null

        val intent = runCatching {
            GroundingIntent.valueOf(root.optString("intent").trim().uppercase())
        }.getOrNull() ?: return null

        val topic = when (root.optNullableString("topic")?.lowercase()) {
            "news" -> TavilySearchTopic.NEWS
            "finance" -> TavilySearchTopic.FINANCE
            else -> TavilySearchTopic.GENERAL
        }
        val timeRange = when (root.optNullableString("time_range")?.lowercase()) {
            "day", "d" -> TavilyTimeRange.DAY
            "week", "w" -> TavilyTimeRange.WEEK
            "month", "m" -> TavilyTimeRange.MONTH
            "year", "y" -> TavilyTimeRange.YEAR
            else -> null
        }
        val spatialAction = when (root.optNullableString("spatial_action")?.lowercase()) {
            "nearby" -> SpatialAction.NEARBY
            "route" -> SpatialAction.ROUTE
            "location" -> SpatialAction.LOCATION
            else -> null
        }
        val routeMode = when (root.optNullableString("route_mode")?.lowercase()) {
            "walking", "walk", "foot" -> RouteMode.WALKING
            "cycling", "cycle", "bike", "biking" -> RouteMode.CYCLING
            else -> RouteMode.DRIVING
        }
        val rawRadius = if (root.has("radius_meters") && !root.isNull("radius_meters")) {
            root.optDouble("radius_meters", Double.NaN).takeIf { !it.isNaN() }?.toInt()
        } else {
            null
        }
        val radius = rawRadius?.coerceIn(MIN_RADIUS_METERS, MAX_RADIUS_METERS)
        val searchQuery = root.optNullableString("search_query")
            ?.sanitizeQuery()
            ?: originalPrompt.takeIf { intent == GroundingIntent.SEARCH || intent == GroundingIntent.BOTH }
        val spatialQuery = root.optNullableString("spatial_query")?.sanitizeQuery()
        val referencePlace = root.optNullableString("reference_place")?.sanitizeQuery()
        val routeOrigin = root.optNullableString("route_origin")?.sanitizeQuery()
        val routeDestination = root.optNullableString("route_destination")?.sanitizeQuery()
        val synthesize = root.optBoolean("synthesize", intent == GroundingIntent.BOTH)
        val useCurrentLocation = root.optBoolean("use_current_location", true)

        if ((intent == GroundingIntent.SEARCH || intent == GroundingIntent.BOTH) && searchQuery.isNullOrBlank()) {
            return null
        }
        if ((intent == GroundingIntent.SPATIAL || intent == GroundingIntent.BOTH) && spatialAction == null) {
            return null
        }
        if (spatialAction == SpatialAction.NEARBY && spatialQuery.isNullOrBlank()) {
            return null
        }
        if (spatialAction == SpatialAction.ROUTE && routeDestination.isNullOrBlank() && spatialQuery.isNullOrBlank()) {
            return null
        }

        return GroundingRoute(
            intent = intent,
            searchQuery = searchQuery,
            tavilyTopic = topic,
            tavilyTimeRange = timeRange,
            synthesize = synthesize,
            spatialAction = spatialAction,
            spatialQuery = spatialQuery,
            radiusMeters = radius,
            useCurrentLocation = useCurrentLocation,
            referencePlace = referencePlace,
            routeOrigin = routeOrigin,
            routeDestination = routeDestination,
            routeMode = routeMode,
        )
    }

    private fun applyExplicitWebPreference(
        route: GroundingRoute,
        prompt: String,
        explicitWebRequest: Boolean?,
    ): GroundingRoute = when (explicitWebRequest) {
        null -> route
        true -> when (route.intent) {
            GroundingIntent.DIRECT -> route.copy(
                intent = GroundingIntent.SEARCH,
                searchQuery = prompt,
                tavilyTopic = TavilySearchTopic.GENERAL,
            )
            GroundingIntent.SPATIAL -> route.copy(
                intent = GroundingIntent.BOTH,
                searchQuery = prompt,
                tavilyTopic = TavilySearchTopic.GENERAL,
                synthesize = true,
            )
            GroundingIntent.SEARCH,
            GroundingIntent.BOTH -> route
        }
        false -> when (route.intent) {
            GroundingIntent.SEARCH -> GroundingRoute(intent = GroundingIntent.DIRECT)
            GroundingIntent.BOTH -> route.copy(
                intent = GroundingIntent.SPATIAL,
                searchQuery = null,
                tavilyTimeRange = null,
                synthesize = route.synthesize,
            )
            GroundingIntent.DIRECT,
            GroundingIntent.SPATIAL -> route
        }
    }

    private fun JSONObject.optNullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).replace(Regex("\\s+"), " ").trim().takeIf { it.isNotBlank() && it != "null" }
    }

    private fun String.sanitizeQuery(): String =
        replace(Regex("\\s+"), " ").trim().take(MAX_QUERY_CHARS)

    private companion object {
        const val TAG = "AssistantGroundingRouter"
        const val MAX_PROMPT_CHARS = 1_300
        const val MAX_QUERY_CHARS = 600
        const val ROUTER_MAX_TOKENS = 192
        const val MIN_RADIUS_METERS = 50
        const val MAX_RADIUS_METERS = 5_000

        const val ROUTER_SYSTEM_PROMPT =
            "Classify ONLY the current user utterance. Do not use or assume conversation history. " +
                "Return exactly one JSON object and no other text. intent must be DIRECT, SEARCH, SPATIAL, or BOTH. " +
                "DIRECT: stable knowledge, coding, definitions, writing, reasoning, or casual conversation that does not require current external facts or location. " +
                "SEARCH: public-web/current/external facts such as live scores, current events, prices, weather, current office-holders, schedules, availability, recent releases, or verification. " +
                "SPATIAL: current location, nearby places, geocoding, distance, or route/directions. BOTH: the request genuinely needs both web facts and spatial/location data. " +
                "For SEARCH/BOTH set search_query to a concise standalone query that preserves names/dates and repairs obvious speech-transcription errors only when confident. " +
                "Set topic to general, news, or finance. Use news for real-time sports/current events, finance for market/asset data, otherwise general. " +
                "Set time_range to day, week, month, year, or null. Use day for live/current/today; do not force a freshness window for explicit historical dates unless publication recency is requested. " +
                "Set synthesize=true only when the user asks for explanation, comparison, reasoning, recommendation, implications, combining tool data, or BOTH; otherwise false so a tool answer can be returned directly. " +
                "For SPATIAL/BOTH set spatial_action to nearby, route, or location. Set spatial_query to the business/place/category to find for nearby, radius_meters to the requested radius in meters or null, use_current_location true only when the request depends on the user's current position, reference_place for an explicit nearby anchor, route_origin and route_destination for routing, and route_mode to driving, walking, or cycling. " +
                "Schema: {\"intent\":\"DIRECT|SEARCH|SPATIAL|BOTH\",\"search_query\":string|null,\"topic\":\"general|news|finance\",\"time_range\":\"day|week|month|year\"|null,\"synthesize\":boolean,\"spatial_action\":\"nearby|route|location\"|null,\"spatial_query\":string|null,\"radius_meters\":number|null,\"use_current_location\":boolean,\"reference_place\":string|null,\"route_origin\":string|null,\"route_destination\":string|null,\"route_mode\":\"driving|walking|cycling\"|null}."
    }
}

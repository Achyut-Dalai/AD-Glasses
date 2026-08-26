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

enum class ExternalTool {
    TAVILY,
    WEATHER,
}

enum class WeatherHorizon {
    CURRENT,
    TODAY,
    TOMORROW,
    WEEK,
}

enum class SpatialAction {
    NEARBY,
    ROUTE,
    LOCATION,
}

data class GroundingRoute(
    val intent: GroundingIntent,
    val externalTool: ExternalTool = ExternalTool.TAVILY,
    val searchQuery: String? = null,
    val tavilyTopic: TavilySearchTopic = TavilySearchTopic.GENERAL,
    val tavilyTimeRange: TavilyTimeRange? = null,
    val sourceDomains: List<String> = emptyList(),
    val weatherHorizon: WeatherHorizon = WeatherHorizon.CURRENT,
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
 * Provider-agnostic semantic router for public-data and spatial tools.
 *
 * The configured AD text model receives only the current utterance. Conversation history is never
 * sent to this classifier. No keyword/phrase trigger decides Search or Maps before this model runs.
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
            "route_done intent=${effective.intent.name.lowercase()} external=${effective.externalTool.name.lowercase()} " +
                "topic=${effective.tavilyTopic.wire} freshness=${effective.tavilyTimeRange?.wire ?: "none"} " +
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

        val intent = when (root.optString("intent").trim().uppercase()) {
            "DIRECT", "ANSWER", "NONE" -> GroundingIntent.DIRECT
            "SEARCH", "WEB", "TAVILY", "EXTERNAL" -> GroundingIntent.SEARCH
            "SPATIAL", "MAP", "MAPS", "OSM" -> GroundingIntent.SPATIAL
            "BOTH" -> GroundingIntent.BOTH
            else -> return null
        }
        val externalTool = when (root.optNullableString("external_tool")?.lowercase()) {
            "weather", "open-meteo", "open_meteo" -> ExternalTool.WEATHER
            else -> ExternalTool.TAVILY
        }
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
        val weatherHorizon = when (root.optNullableString("weather_horizon")?.lowercase()) {
            "today" -> WeatherHorizon.TODAY
            "tomorrow" -> WeatherHorizon.TOMORROW
            "week", "weekly", "7day", "7-day" -> WeatherHorizon.WEEK
            else -> WeatherHorizon.CURRENT
        }
        val spatialAction = when (root.optNullableString("spatial_action")?.lowercase()) {
            "nearby", "find" -> SpatialAction.NEARBY
            "route", "navigate", "directions" -> SpatialAction.ROUTE
            "location", "gps" -> SpatialAction.LOCATION
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
        val sourceDomains = root.optDomains()
        val spatialQuery = root.optNullableString("spatial_query")?.sanitizeQuery()
        val referencePlace = root.optNullableString("reference_place")?.sanitizeQuery()
        val routeOrigin = root.optNullableString("route_origin")?.sanitizeQuery()
        val routeDestination = root.optNullableString("route_destination")?.sanitizeQuery()
        val useCurrentLocation = root.optBoolean("use_current_location", referencePlace == null)

        if ((intent == GroundingIntent.SEARCH || intent == GroundingIntent.BOTH) &&
            externalTool == ExternalTool.TAVILY && searchQuery.isNullOrBlank()
        ) {
            return null
        }
        if ((intent == GroundingIntent.SEARCH || intent == GroundingIntent.BOTH) &&
            externalTool == ExternalTool.WEATHER && !useCurrentLocation && referencePlace.isNullOrBlank()
        ) {
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
            externalTool = externalTool,
            searchQuery = searchQuery,
            tavilyTopic = topic,
            tavilyTimeRange = timeRange,
            sourceDomains = sourceDomains,
            weatherHorizon = weatherHorizon,
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
                externalTool = ExternalTool.TAVILY,
                searchQuery = prompt,
                tavilyTopic = TavilySearchTopic.GENERAL,
            )
            GroundingIntent.SPATIAL -> route.copy(
                intent = GroundingIntent.BOTH,
                externalTool = ExternalTool.TAVILY,
                searchQuery = prompt,
                tavilyTopic = TavilySearchTopic.GENERAL,
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
                sourceDomains = emptyList(),
            )
            GroundingIntent.DIRECT,
            GroundingIntent.SPATIAL -> route
        }
    }

    private fun JSONObject.optNullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).replace(Regex("\\s+"), " ").trim().takeIf { it.isNotBlank() && it != "null" }
    }

    private fun JSONObject.optDomains(): List<String> {
        val array = optJSONArray("source_domains") ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                sanitizeDomain(array.optString(index))?.let(::add)
            }
        }.distinct().take(MAX_SOURCE_DOMAINS)
    }

    private fun sanitizeDomain(raw: String): String? {
        val host = raw.trim()
            .lowercase()
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore('/')
            .substringBefore(':')
            .trim('.')
        return host.takeIf { DOMAIN.matches(it) }
    }

    private fun String.sanitizeQuery(): String =
        replace(Regex("\\s+"), " ").trim().take(MAX_QUERY_CHARS)

    private companion object {
        const val TAG = "AssistantGroundingRouter"
        const val MAX_PROMPT_CHARS = 1_300
        const val MAX_QUERY_CHARS = 600
        const val ROUTER_MAX_TOKENS = 160
        const val MIN_RADIUS_METERS = 50
        const val MAX_RADIUS_METERS = 5_000
        const val MAX_SOURCE_DOMAINS = 4
        val DOMAIN = Regex("(?i)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}")

        const val ROUTER_SYSTEM_PROMPT =
            "Classify only this utterance; never use or assume history. Return one compact JSON object, no prose. " +
                "intent: DIRECT for stable knowledge/reasoning; SEARCH for current/external data; SPATIAL for nearby/location/routes; BOTH only when the user needs an external-data answer and a spatial result together. " +
                "No keyword is a command by itself. Infer the meaning of the whole utterance. If a factual answer could have changed or you are unsure DIRECT is safe, choose SEARCH. " +
                "For SEARCH/BOTH choose external_tool=weather for weather/forecast conditions, otherwise tavily. Weather needs use_current_location=true or reference_place, plus weather_horizon=current|today|tomorrow|week. " +
                "For Tavily, search_query should be standalone and may repair obvious ASR errors; topic is only general, news, or finance. Use news for current events and live sports, finance for markets, otherwise general. time_range is day/week/month/year when useful. " +
                "Only set source_domains when the user explicitly asks to use/check a named website or domain; never invent a preferred publisher. " +
                "For SPATIAL/BOTH set spatial_action=nearby|route|location. nearby needs spatial_query and optional radius_meters/reference_place/use_current_location. route needs route_destination and optional route_origin/route_mode. Convert spoken distances to meters. " +
                "Omit irrelevant/null fields. Keys: intent, external_tool, search_query, topic, time_range, source_domains, weather_horizon, spatial_action, spatial_query, radius_meters, use_current_location, reference_place, route_origin, route_destination, route_mode."
    }
}

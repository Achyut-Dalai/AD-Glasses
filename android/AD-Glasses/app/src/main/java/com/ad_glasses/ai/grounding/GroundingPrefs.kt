package com.ad_glasses.ai.grounding

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.net.URI
import org.json.JSONArray
import org.json.JSONObject

data class GroundingServiceConfig(
    val tavilyEnabled: Boolean,
    val nominatimBaseUrl: String,
    val overpassEndpoint: String,
    val osrmBaseUrl: String,
)

data class GtfsRealtimeFeedConfig(
    val id: String,
    val label: String,
    val url: String,
    val headerName: String? = null,
    val headerValue: String? = null,
)

/** Keystore-backed configuration for grounding, transport, and OSM-family services. */
object GroundingPrefs {
    const val DEFAULT_NOMINATIM_BASE_URL = "https://nominatim.openstreetmap.org"
    const val DEFAULT_OVERPASS_ENDPOINT = "https://overpass-api.de/api/interpreter"
    // This root exposes separate /routed-car, /routed-foot and /routed-bike OSRM instances.
    const val DEFAULT_OSRM_BASE_URL = "https://routing.openstreetmap.de"
    const val DEFAULT_RAIL_RAPIDAPI_HOST = "irctc1.p.rapidapi.com"
    const val DEFAULT_AVIATIONSTACK_BASE_URL = "https://api.aviationstack.com/v1"

    private const val PREFS = "assistant_grounding_secure"
    private const val KEY_TAVILY_API_KEY = "tavily_api_key"
    private const val KEY_TAVILY_ENABLED = "tavily_enabled"
    private const val KEY_NOMINATIM_BASE_URL = "nominatim_base_url"
    private const val KEY_OVERPASS_ENDPOINT = "overpass_endpoint"
    private const val KEY_OSRM_BASE_URL = "osrm_base_url"
    private const val KEY_RAIL_RAPIDAPI_KEY = "rail_rapidapi_key"
    private const val KEY_RAIL_RAPIDAPI_HOST = "rail_rapidapi_host"
    private const val KEY_AVIATIONSTACK_KEY = "aviationstack_access_key"
    private const val KEY_AVIATIONSTACK_BASE_URL = "aviationstack_base_url"
    private const val KEY_GTFS_REALTIME_FEEDS = "gtfs_realtime_feeds"
    private val SAFE_HEADER_NAME = Regex("[A-Za-z0-9-]{1,64}")
    private val SAFE_HOST = Regex("(?=.{1,253}$)(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\\.)+[A-Za-z]{2,63}")

    private fun secure(context: Context): SharedPreferences {
        val appContext = context.applicationContext
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            appContext,
            PREFS,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun hasTavilyApiKey(context: Context): Boolean = getTavilyApiKey(context).isNotBlank()

    /** Network code only. Never surface the returned value in UI. */
    fun getTavilyApiKey(context: Context): String =
        secure(context).getString(KEY_TAVILY_API_KEY, "").orEmpty().trim()

    fun replaceTavilyApiKey(context: Context, replacement: String) {
        val clean = replacement.trim().removePrefix("Bearer ").trim()
        require(clean.isNotBlank()) { "Tavily API key cannot be blank." }
        secure(context).edit().putString(KEY_TAVILY_API_KEY, clean).apply()
    }

    fun clearTavilyApiKey(context: Context) {
        secure(context).edit().remove(KEY_TAVILY_API_KEY).apply()
    }

    fun setTavilyEnabled(context: Context, enabled: Boolean) {
        secure(context).edit().putBoolean(KEY_TAVILY_ENABLED, enabled).apply()
    }

    fun hasRailRapidApiKey(context: Context): Boolean = getRailRapidApiKey(context).isNotBlank()

    /** RapidAPI secret used only on the network request path. */
    fun getRailRapidApiKey(context: Context): String =
        secure(context).getString(KEY_RAIL_RAPIDAPI_KEY, "").orEmpty().trim()

    fun replaceRailRapidApiKey(context: Context, replacement: String) {
        val clean = cleanSecret(replacement)
        require(clean.isNotBlank()) { "Rail RapidAPI key cannot be blank." }
        secure(context).edit().putString(KEY_RAIL_RAPIDAPI_KEY, clean).apply()
    }

    fun clearRailRapidApiKey(context: Context) {
        secure(context).edit().remove(KEY_RAIL_RAPIDAPI_KEY).apply()
    }

    fun getRailRapidApiHost(context: Context): String = validateHost(
        secure(context).getString(KEY_RAIL_RAPIDAPI_HOST, null) ?: DEFAULT_RAIL_RAPIDAPI_HOST,
    )

    fun saveRailRapidApiHost(context: Context, host: String) {
        secure(context).edit().putString(KEY_RAIL_RAPIDAPI_HOST, validateHost(host)).apply()
    }

    fun hasAviationStackKey(context: Context): Boolean = getAviationStackKey(context).isNotBlank()

    /** AviationStack access key used only on the network request path. */
    fun getAviationStackKey(context: Context): String =
        secure(context).getString(KEY_AVIATIONSTACK_KEY, "").orEmpty().trim()

    fun replaceAviationStackKey(context: Context, replacement: String) {
        val clean = cleanSecret(replacement)
        require(clean.isNotBlank()) { "AviationStack access key cannot be blank." }
        secure(context).edit().putString(KEY_AVIATIONSTACK_KEY, clean).apply()
    }

    fun clearAviationStackKey(context: Context) {
        secure(context).edit().remove(KEY_AVIATIONSTACK_KEY).apply()
    }

    fun getAviationStackBaseUrl(context: Context): String = endpoint(
        secure(context).getString(KEY_AVIATIONSTACK_BASE_URL, null),
        DEFAULT_AVIATIONSTACK_BASE_URL,
        allowPath = true,
    )

    fun saveAviationStackBaseUrl(context: Context, baseUrl: String) {
        val clean = endpoint(baseUrl, DEFAULT_AVIATIONSTACK_BASE_URL, allowPath = true)
        secure(context).edit().putString(KEY_AVIATIONSTACK_BASE_URL, clean).apply()
    }

    /**
     * Realtime feeds are agency-specific. URLs and optional feed auth headers are encrypted at rest.
     * A URL may contain a query token because several public agencies distribute feeds that way.
     */
    fun saveGtfsRealtimeFeeds(context: Context, feeds: List<GtfsRealtimeFeedConfig>) {
        val normalized = feeds.asSequence()
            .map(::validateGtfsFeed)
            .distinctBy(GtfsRealtimeFeedConfig::id)
            .take(24)
            .toList()
        val array = JSONArray()
        normalized.forEach { feed ->
            array.put(
                JSONObject()
                    .put("id", feed.id)
                    .put("label", feed.label)
                    .put("url", feed.url)
                    .also { json ->
                        feed.headerName?.let { json.put("header_name", it) }
                        feed.headerValue?.let { json.put("header_value", it) }
                    },
            )
        }
        secure(context).edit().putString(KEY_GTFS_REALTIME_FEEDS, array.toString()).apply()
    }

    fun getGtfsRealtimeFeeds(context: Context): List<GtfsRealtimeFeedConfig> {
        val raw = secure(context).getString(KEY_GTFS_REALTIME_FEEDS, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val candidate = GtfsRealtimeFeedConfig(
                        id = item.optString("id"),
                        label = item.optString("label"),
                        url = item.optString("url"),
                        headerName = item.optString("header_name").takeIf(String::isNotBlank),
                        headerValue = item.optString("header_value").takeIf(String::isNotBlank),
                    )
                    runCatching { validateGtfsFeed(candidate) }.getOrNull()?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
    }

    fun getConfig(context: Context): GroundingServiceConfig {
        val prefs = secure(context)
        return GroundingServiceConfig(
            tavilyEnabled = prefs.getBoolean(KEY_TAVILY_ENABLED, true),
            nominatimBaseUrl = endpoint(
                prefs.getString(KEY_NOMINATIM_BASE_URL, null),
                DEFAULT_NOMINATIM_BASE_URL,
                allowPath = false,
            ),
            overpassEndpoint = endpoint(
                prefs.getString(KEY_OVERPASS_ENDPOINT, null),
                DEFAULT_OVERPASS_ENDPOINT,
                allowPath = true,
            ),
            osrmBaseUrl = endpoint(
                prefs.getString(KEY_OSRM_BASE_URL, null),
                DEFAULT_OSRM_BASE_URL,
                allowPath = false,
            ),
        )
    }

    fun saveEndpoints(
        context: Context,
        nominatimBaseUrl: String,
        overpassEndpoint: String,
        osrmBaseUrl: String,
    ) {
        val nominatim = endpoint(nominatimBaseUrl, DEFAULT_NOMINATIM_BASE_URL, allowPath = false)
        val overpass = endpoint(overpassEndpoint, DEFAULT_OVERPASS_ENDPOINT, allowPath = true)
        val osrm = endpoint(osrmBaseUrl, DEFAULT_OSRM_BASE_URL, allowPath = false)
        secure(context).edit()
            .putString(KEY_NOMINATIM_BASE_URL, nominatim)
            .putString(KEY_OVERPASS_ENDPOINT, overpass)
            .putString(KEY_OSRM_BASE_URL, osrm)
            .apply()
    }

    internal fun validatedEndpoint(value: String?, fallback: String, allowPath: Boolean): String =
        endpoint(value, fallback, allowPath)

    internal fun validatedHttpsUrl(value: String): String = httpsUrl(value)

    internal fun validatedRapidApiHost(value: String): String = validateHost(value)

    private fun validateGtfsFeed(feed: GtfsRealtimeFeedConfig): GtfsRealtimeFeedConfig {
        val id = feed.id.replace(Regex("[^A-Za-z0-9_.-]"), "-").trim('-').take(64)
        require(id.isNotBlank()) { "GTFS realtime feed id cannot be blank." }
        val label = feed.label.replace(Regex("\\s+"), " ").trim().take(120)
        require(label.isNotBlank()) { "GTFS realtime feed label cannot be blank." }
        val headerName = feed.headerName?.trim()?.takeIf(String::isNotBlank)?.also {
            require(SAFE_HEADER_NAME.matches(it)) { "GTFS realtime header name is invalid." }
        }
        val headerValue = feed.headerValue?.trim()?.takeIf(String::isNotBlank)?.also {
            require(it.length <= 512 && '\r' !in it && '\n' !in it) { "GTFS realtime header value is invalid." }
        }
        require((headerName == null) == (headerValue == null)) {
            "GTFS realtime feed auth requires both header name and value."
        }
        return GtfsRealtimeFeedConfig(
            id = id,
            label = label,
            url = httpsUrl(feed.url),
            headerName = headerName,
            headerValue = headerValue,
        )
    }

    private fun cleanSecret(value: String): String = value
        .trim()
        .removePrefix("Bearer ")
        .trim()
        .also { require(it.length <= 1_024 && '\r' !in it && '\n' !in it) { "Provider secret is invalid." } }

    private fun validateHost(value: String): String {
        val clean = value.trim().lowercase().removePrefix("https://").removePrefix("http://").trimEnd('/')
        require(SAFE_HOST.matches(clean)) { "RapidAPI host must be a DNS host name without a path." }
        return clean
    }

    private fun httpsUrl(value: String): String {
        val raw = value.trim()
        val parsed = runCatching { URI(raw) }.getOrElse {
            throw IllegalArgumentException("Provider URL is not a valid URI.", it)
        }
        require(parsed.scheme.equals("https", ignoreCase = true)) { "Provider URLs must use HTTPS." }
        require(!parsed.host.isNullOrBlank()) { "Provider URL must include a valid host." }
        require(parsed.userInfo == null) { "Provider URLs cannot contain embedded credentials." }
        require(parsed.fragment == null) { "Provider URLs cannot contain fragments." }
        require(parsed.rawPath?.contains("..") != true) { "Provider URL path cannot contain '..'." }
        return parsed.toASCIIString()
    }

    private fun endpoint(value: String?, fallback: String, allowPath: Boolean): String {
        val raw = value?.trim().orEmpty().ifBlank { fallback }
        val parsed = runCatching { URI(raw) }.getOrElse {
            throw IllegalArgumentException("Grounding service endpoint is not a valid URI.", it)
        }
        require(parsed.scheme.equals("https", ignoreCase = true)) {
            "Grounding service endpoints must use HTTPS."
        }
        require(!parsed.host.isNullOrBlank()) { "Grounding service endpoint must include a valid host." }
        require(parsed.userInfo == null) { "Grounding service endpoints cannot contain embedded credentials." }
        require(parsed.query == null) { "Grounding service endpoints cannot contain a query string." }
        require(parsed.fragment == null) { "Grounding service endpoints cannot contain a fragment." }
        require(parsed.rawPath?.contains("..") != true) { "Grounding service endpoint path cannot contain '..'." }
        if (!allowPath) {
            require(parsed.path.isNullOrBlank() || parsed.path == "/") {
                "Use only the HTTPS service base URL here."
            }
        }

        val normalizedPath = when {
            !allowPath -> ""
            parsed.rawPath.isNullOrBlank() || parsed.rawPath == "/" -> ""
            else -> parsed.rawPath.trimEnd('/')
        }
        val port = if (parsed.port >= 0) ":${parsed.port}" else ""
        return "https://${parsed.host}$port$normalizedPath"
    }
}

package com.ad_glasses.ai.grounding

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

data class GroundingServiceConfig(
    val tavilyEnabled: Boolean,
    val nominatimBaseUrl: String,
    val overpassEndpoint: String,
    val osrmBaseUrl: String,
)

/** Keystore-backed configuration for Tavily and OSM-family services. */
object GroundingPrefs {
    const val DEFAULT_NOMINATIM_BASE_URL = "https://nominatim.openstreetmap.org"
    const val DEFAULT_OVERPASS_ENDPOINT = "https://overpass-api.de/api/interpreter"
    const val DEFAULT_OSRM_BASE_URL = "https://router.project-osrm.org"

    private const val PREFS = "assistant_grounding_secure"
    private const val KEY_TAVILY_API_KEY = "tavily_api_key"
    private const val KEY_TAVILY_ENABLED = "tavily_enabled"
    private const val KEY_NOMINATIM_BASE_URL = "nominatim_base_url"
    private const val KEY_OVERPASS_ENDPOINT = "overpass_endpoint"
    private const val KEY_OSRM_BASE_URL = "osrm_base_url"

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

    private fun endpoint(value: String?, fallback: String, allowPath: Boolean): String {
        val clean = value?.trim()?.trimEnd('/').orEmpty().ifBlank { fallback }
        require(clean.startsWith("https://")) { "Grounding service endpoints must use HTTPS." }
        if (!allowPath) {
            val suffix = clean.removePrefix("https://")
            require('/' !in suffix) { "Use only the HTTPS service base URL here." }
        }
        return clean
    }
}

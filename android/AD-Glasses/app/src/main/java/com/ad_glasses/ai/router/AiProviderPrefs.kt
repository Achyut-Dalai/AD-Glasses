package com.ad_glasses.ai.router

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/** AD Glasses now has one assistant inference class: user-configured Cloud AI. */
enum class AiProviderType(val wire: String, val label: String) {
    CLOUD_API("cloud_api", "Cloud AI");

    companion object {
        /** Every retired route, including local-model wires, migrates forward to Cloud AI. */
        fun fromWire(value: String?): AiProviderType = CLOUD_API
    }
}

enum class CloudWebMode(val wire: String, val label: String) {
    OFF("off", "Off"),
    AUTO("auto", "Auto");

    companion object {
        fun fromWire(value: String?): CloudWebMode =
            entries.firstOrNull { it.wire == value?.trim()?.lowercase() } ?: OFF
    }
}

enum class ApiProvider(
    val wire: String,
    val label: String,
    val defaultBaseUrl: String,
    val defaultModel: String,
    val realtimeCapable: Boolean,
    val nativeWebCapable: Boolean,
) {
    OPENAI(
        "openai",
        "OpenAI",
        "https://api.openai.com/v1",
        "gpt-5",
        true,
        true,
    ),
    GOOGLE(
        "google",
        "Google Gemini",
        "https://generativelanguage.googleapis.com/v1beta/openai",
        "gemini-3.7-flash",
        true,
        true,
    ),
    DEEPSEEK(
        "deepseek",
        "DeepSeek",
        "https://api.deepseek.com",
        "deepseek-v4-flash",
        false,
        false,
    ),
    OPENROUTER(
        "openrouter",
        "OpenRouter",
        "https://openrouter.ai/api/v1",
        "openai/gpt-5.3-chat",
        false,
        true,
    ),
    CUSTOM(
        "custom",
        "OpenAI-compatible",
        "",
        "",
        false,
        false,
    );

    companion object {
        fun fromWire(value: String?): ApiProvider =
            entries.firstOrNull { it.wire == value?.trim()?.lowercase() } ?: OPENAI
    }
}

data class CloudAiProfile(
    val id: String,
    val name: String,
    val provider: ApiProvider,
    val baseUrl: String,
    val model: String,
    val webMode: CloudWebMode = CloudWebMode.OFF,
) {
    val webAvailable: Boolean
        get() = provider.nativeWebCapable && webMode == CloudWebMode.AUTO
}

/**
 * Keystore-backed Cloud AI profile store.
 *
 * Profile metadata and API keys live in EncryptedSharedPreferences. The network layer can obtain
 * a secret for a concrete profile, but product UI is intentionally given only [hasApiKey]. A
 * saved key is therefore never pre-filled back into a text field.
 */
object AiProviderPrefs {
    private const val LEGACY_PREFS_NAME = "ai_provider_prefs"
    private const val LEGACY_SECRET_PREFS_NAME = "ai_api_secrets"
    private const val SECURE_PREFS_NAME = "cloud_ai_profiles_secure"

    private const val KEY_PROFILE_IDS = "profile_ids"
    private const val KEY_ACTIVE_PROFILE_ID = "active_profile_id"
    private const val KEY_LEGACY_MIGRATED = "legacy_migrated_v1"
    private const val KEY_RELAY_BASE_URL = "relay_base_url"

    private const val PROFILE_PREFIX = "profile_"
    private const val SECRET_PREFIX = "secret_"

    private fun masterKey(context: Context): MasterKey =
        MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

    private fun encryptedPrefs(context: Context, name: String): SharedPreferences =
        EncryptedSharedPreferences.create(
            context.applicationContext,
            name,
            masterKey(context),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

    private fun secure(context: Context): SharedPreferences = encryptedPrefs(context, SECURE_PREFS_NAME)

    private fun legacyPlain(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun listProfiles(context: Context): List<CloudAiProfile> {
        ensureLegacyMigrated(context)
        val prefs = secure(context)
        return profileIds(prefs).mapNotNull { id -> readProfile(prefs, id) }
    }

    @Synchronized
    fun getProfile(context: Context, id: String?): CloudAiProfile? {
        ensureLegacyMigrated(context)
        val cleanId = id?.trim().orEmpty()
        if (cleanId.isBlank()) return null
        return readProfile(secure(context), cleanId)
    }

    @Synchronized
    fun getActiveProfile(context: Context): CloudAiProfile? {
        ensureLegacyMigrated(context)
        val prefs = secure(context)
        val activeId = prefs.getString(KEY_ACTIVE_PROFILE_ID, null)
        val active = readProfile(prefs, activeId.orEmpty())
        if (active != null) return active
        val first = profileIds(prefs).firstNotNullOfOrNull { readProfile(prefs, it) }
        if (first != null) prefs.edit().putString(KEY_ACTIVE_PROFILE_ID, first.id).commit()
        return first
    }

    @Synchronized
    fun saveProfile(
        context: Context,
        profile: CloudAiProfile,
        apiKeyReplacement: String? = null,
        makeActive: Boolean = false,
    ): CloudAiProfile {
        ensureLegacyMigrated(context)
        val prefs = secure(context)
        val saved = normalizeProfile(profile)
        require(saved.name.isNotBlank()) { "Profile name is required." }
        require(saved.baseUrl.startsWith("https://")) { "API base URL must use HTTPS." }
        require(saved.model.isNotBlank()) { "Model is required." }

        val existing = readProfile(prefs, saved.id)
        val replacement = apiKeyReplacement?.trim().orEmpty()
        if (existing == null && replacement.isBlank()) {
            require(hasApiKeyInternal(prefs, saved.id)) { "API key is required for a new profile." }
        }

        val ids = profileIds(prefs).toMutableList()
        if (saved.id !in ids) ids += saved.id
        prefs.edit()
            .putString(profileKey(saved.id), profileToJson(saved).toString())
            .putString(KEY_PROFILE_IDS, JSONArray(ids).toString())
            .apply {
                if (replacement.isNotBlank()) putString(secretKey(saved.id), replacement)
                if (makeActive || getString(KEY_ACTIVE_PROFILE_ID, null).isNullOrBlank()) {
                    putString(KEY_ACTIVE_PROFILE_ID, saved.id)
                }
            }
            .commit()
        return saved
    }

    fun newProfile(provider: ApiProvider, existingCount: Int = 0): CloudAiProfile = CloudAiProfile(
        id = UUID.randomUUID().toString(),
        name = if (existingCount == 0) provider.label else "${provider.label} ${existingCount + 1}",
        provider = provider,
        baseUrl = provider.defaultBaseUrl,
        model = provider.defaultModel,
        webMode = if (provider.nativeWebCapable) CloudWebMode.AUTO else CloudWebMode.OFF,
    )

    @Synchronized
    fun setActiveProfile(context: Context, profileId: String) {
        ensureLegacyMigrated(context)
        val prefs = secure(context)
        require(readProfile(prefs, profileId) != null) { "Unknown Cloud AI profile." }
        prefs.edit().putString(KEY_ACTIVE_PROFILE_ID, profileId).commit()
    }

    @Synchronized
    fun deleteProfile(context: Context, profileId: String): Boolean {
        ensureLegacyMigrated(context)
        val prefs = secure(context)
        if (readProfile(prefs, profileId) == null) return false
        val remaining = profileIds(prefs).filterNot { it == profileId }
        val current = prefs.getString(KEY_ACTIVE_PROFILE_ID, null)
        prefs.edit()
            .remove(profileKey(profileId))
            .remove(secretKey(profileId))
            .putString(KEY_PROFILE_IDS, JSONArray(remaining).toString())
            .apply {
                if (current == profileId) {
                    if (remaining.isEmpty()) remove(KEY_ACTIVE_PROFILE_ID)
                    else putString(KEY_ACTIVE_PROFILE_ID, remaining.first())
                }
            }
            .commit()
        return true
    }

    fun hasApiKey(context: Context, profileId: String): Boolean {
        ensureLegacyMigrated(context)
        return hasApiKeyInternal(secure(context), profileId)
    }

    fun isApiConfigured(context: Context): Boolean =
        getActiveProfile(context)?.let { profile ->
            profile.model.isNotBlank() && profile.baseUrl.isNotBlank() && hasApiKey(context, profile.id)
        } == true

    fun isApiConfigured(context: Context, provider: ApiProvider): Boolean =
        listProfiles(context).any { profile ->
            profile.provider == provider && hasApiKey(context, profile.id) && profile.model.isNotBlank()
        }

    /** Network-layer secret accessor. Never bind this return value into product UI state. */
    internal fun apiKeyForRequest(context: Context, profileId: String): String =
        secure(context).getString(secretKey(profileId), "")?.trim().orEmpty()

    internal fun activeProfileWithKey(context: Context): Pair<CloudAiProfile, String> {
        val profile = getActiveProfile(context)
            ?: throw IllegalStateException("No Cloud AI profile is configured.")
        val key = apiKeyForRequest(context, profile.id)
        if (key.isBlank()) throw IllegalStateException("${profile.name} does not have an API key saved.")
        return profile to key
    }

    /** Realtime authorization remains independent from provider REST profiles. */
    fun getRelayBaseUrl(context: Context): String =
        legacyPlain(context).getString(KEY_RELAY_BASE_URL, "")?.trim().orEmpty().trimEnd('/')

    fun setRelayBaseUrl(context: Context, value: String) {
        legacyPlain(context).edit().putString(KEY_RELAY_BASE_URL, value.trim().trimEnd('/')).apply()
    }

    fun isRelayConfigured(context: Context): Boolean = getRelayBaseUrl(context).isNotBlank()

    // Forward-compatible wrappers for callers being migrated away from the former single-provider model.
    fun getProvider(context: Context): AiProviderType = AiProviderType.CLOUD_API
    fun setProvider(context: Context, provider: AiProviderType) = Unit
    fun getApiProvider(context: Context): ApiProvider = getActiveProfile(context)?.provider ?: ApiProvider.OPENAI
    fun setApiProvider(context: Context, provider: ApiProvider) {
        val matching = listProfiles(context).firstOrNull { it.provider == provider }
        if (matching != null) setActiveProfile(context, matching.id)
    }
    internal fun getApiKey(context: Context, provider: ApiProvider = getApiProvider(context)): String =
        listProfiles(context).firstOrNull { it.provider == provider }?.let { apiKeyForRequest(context, it.id) }.orEmpty()
    internal fun setApiKey(context: Context, provider: ApiProvider, value: String) {
        val current = listProfiles(context).firstOrNull { it.provider == provider }
            ?: newProfile(provider, listProfiles(context).count { it.provider == provider })
        saveProfile(context, current, apiKeyReplacement = value, makeActive = true)
    }
    fun getModel(context: Context, provider: ApiProvider = getApiProvider(context)): String =
        listProfiles(context).firstOrNull { it.provider == provider }?.model
            ?: provider.defaultModel
    fun setModel(context: Context, provider: ApiProvider, value: String) {
        val current = listProfiles(context).firstOrNull { it.provider == provider } ?: return
        saveProfile(context, current.copy(model = value.trim()))
    }

    @Synchronized
    private fun ensureLegacyMigrated(context: Context) {
        val target = secure(context)
        if (target.getBoolean(KEY_LEGACY_MIGRATED, false)) return

        if (profileIds(target).isEmpty()) {
            val old = legacyPlain(context)
            val provider = ApiProvider.fromWire(old.getString("api_provider", ApiProvider.OPENAI.wire))
            val model = old.getString("model_${provider.wire}", provider.defaultModel)?.trim().orEmpty()
                .ifBlank { provider.defaultModel }
            val oldSecretPrefs = runCatching { encryptedPrefs(context, LEGACY_SECRET_PREFS_NAME) }.getOrNull()
            val oldKey = oldSecretPrefs?.getString("api_key_${provider.wire}", "")?.trim().orEmpty()
            if (oldKey.isNotBlank()) {
                val migrated = normalizeProfile(
                    newProfile(provider).copy(
                        name = provider.label,
                        model = model,
                    ),
                )
                target.edit()
                    .putString(KEY_PROFILE_IDS, JSONArray(listOf(migrated.id)).toString())
                    .putString(KEY_ACTIVE_PROFILE_ID, migrated.id)
                    .putString(profileKey(migrated.id), profileToJson(migrated).toString())
                    .putString(secretKey(migrated.id), oldKey)
                    .commit()
                oldSecretPrefs.edit().remove("api_key_${provider.wire}").commit()
            }
        }
        target.edit().putBoolean(KEY_LEGACY_MIGRATED, true).commit()
    }

    private fun normalizeProfile(profile: CloudAiProfile): CloudAiProfile = profile.copy(
        id = profile.id.trim().ifBlank { UUID.randomUUID().toString() },
        name = profile.name.trim(),
        baseUrl = profile.baseUrl.trim().trimEnd('/'),
        model = profile.model.trim(),
        webMode = if (profile.provider.nativeWebCapable) CloudWebMode.AUTO else CloudWebMode.OFF,
    )

    private fun profileIds(prefs: SharedPreferences): List<String> = runCatching {
        val array = JSONArray(prefs.getString(KEY_PROFILE_IDS, "[]") ?: "[]")
        buildList {
            for (index in 0 until array.length()) {
                array.optString(index).trim().takeIf { it.isNotBlank() }?.let(::add)
            }
        }.distinct()
    }.getOrDefault(emptyList())

    private fun readProfile(prefs: SharedPreferences, id: String): CloudAiProfile? {
        if (id.isBlank()) return null
        val raw = prefs.getString(profileKey(id), null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            normalizeProfile(
                CloudAiProfile(
                    id = id,
                    name = json.optString("name"),
                    provider = ApiProvider.fromWire(json.optString("provider")),
                    baseUrl = json.optString("base_url"),
                    model = json.optString("model"),
                    webMode = CloudWebMode.fromWire(json.optString("web_mode")),
                ),
            )
        }.getOrNull()
    }

    private fun profileToJson(profile: CloudAiProfile): JSONObject = JSONObject()
        .put("name", profile.name)
        .put("provider", profile.provider.wire)
        .put("base_url", profile.baseUrl)
        .put("model", profile.model)
        .put("web_mode", profile.webMode.wire)

    private fun hasApiKeyInternal(prefs: SharedPreferences, profileId: String): Boolean =
        prefs.getString(secretKey(profileId), "")?.trim().isNullOrBlank().not()

    private fun profileKey(id: String) = "$PROFILE_PREFIX$id"
    private fun secretKey(id: String) = "$SECRET_PREFIX$id"
}

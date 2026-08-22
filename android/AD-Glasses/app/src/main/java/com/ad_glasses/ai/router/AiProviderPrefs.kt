package com.ad_glasses.ai.router

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

enum class AiProviderType(val wire: String, val label: String) {
    /** AD-owned cloud inference. */
    CLOUD_API("cloud_api", "Cloud AI"),
    /** On-device model. */
    LOCAL_MODELS("local_models", "Local AI");

    companion object {
        fun fromWire(value: String?): AiProviderType =
            entries.firstOrNull { it.wire == value?.trim()?.lowercase() } ?: CLOUD_API
    }
}

enum class ApiProvider(
    val wire: String,
    val label: String,
    val baseUrl: String,
    val defaultModel: String,
    val realtimeCapable: Boolean,
) {
    OPENAI("openai", "OpenAI", "https://api.openai.com/v1", "gpt-5", true),
    GOOGLE("google", "Google Gemini", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-3.7-flash", true),
    DEEPSEEK("deepseek", "DeepSeek", "https://api.deepseek.com", "deepseek-v4-flash", false),
    OPENROUTER("openrouter", "OpenRouter", "https://openrouter.ai/api/v1", "openai/gpt-5.3-chat", false);

    companion object {
        fun fromWire(value: String?): ApiProvider =
            entries.firstOrNull { it.wire == value } ?: OPENAI
    }
}

/** Cloud/local AI preferences owned by AD Glasses. */
object AiProviderPrefs {
    private const val PREFS_NAME = "ai_provider_prefs"
    private const val SECRET_PREFS_NAME = "ai_api_secrets"
    private const val KEY_PROVIDER = "provider"
    private const val KEY_API_PROVIDER = "api_provider"
    private const val KEY_RELAY_BASE_URL = "relay_base_url"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun secretPrefs(context: Context): SharedPreferences {
        val appContext = context.applicationContext
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            appContext,
            SECRET_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun getProvider(context: Context): AiProviderType =
        AiProviderType.fromWire(prefs(context).getString(KEY_PROVIDER, AiProviderType.CLOUD_API.wire))

    fun setProvider(context: Context, provider: AiProviderType) {
        prefs(context).edit().putString(KEY_PROVIDER, provider.wire).apply()
    }

    fun getApiProvider(context: Context): ApiProvider =
        ApiProvider.fromWire(prefs(context).getString(KEY_API_PROVIDER, ApiProvider.OPENAI.wire))

    fun setApiProvider(context: Context, provider: ApiProvider) {
        prefs(context).edit().putString(KEY_API_PROVIDER, provider.wire).apply()
    }

    fun getApiKey(context: Context, provider: ApiProvider = getApiProvider(context)): String =
        secretPrefs(context).getString(apiKeyKey(provider), "")?.trim().orEmpty()

    fun setApiKey(context: Context, provider: ApiProvider, value: String) {
        val clean = value.trim()
        secretPrefs(context).edit().apply {
            if (clean.isBlank()) remove(apiKeyKey(provider)) else putString(apiKeyKey(provider), clean)
        }.apply()
    }

    fun getModel(context: Context, provider: ApiProvider = getApiProvider(context)): String =
        prefs(context).getString(modelKey(provider), provider.defaultModel)?.trim().orEmpty()
            .ifBlank { provider.defaultModel }

    fun setModel(context: Context, provider: ApiProvider, value: String) {
        prefs(context).edit().putString(modelKey(provider), value.trim()).apply()
    }

    fun isApiConfigured(context: Context, provider: ApiProvider = getApiProvider(context)): Boolean =
        getApiKey(context, provider).isNotBlank() && getModel(context, provider).isNotBlank()

    /** Optional AD-owned relay for short-lived realtime session credentials such as Gemini Live. */
    fun getRelayBaseUrl(context: Context): String =
        prefs(context).getString(KEY_RELAY_BASE_URL, "")?.trim().orEmpty().trimEnd('/')

    fun setRelayBaseUrl(context: Context, value: String) {
        prefs(context).edit().putString(KEY_RELAY_BASE_URL, value.trim().trimEnd('/')).apply()
    }

    fun isRelayConfigured(context: Context): Boolean = getRelayBaseUrl(context).isNotBlank()

    private fun apiKeyKey(provider: ApiProvider) = "api_key_${provider.wire}"
    private fun modelKey(provider: ApiProvider) = "model_${provider.wire}"
}

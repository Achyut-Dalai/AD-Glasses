package com.fersaiyan.cyanbridge.ai.router

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.fersaiyan.cyanbridge.BuildConfig

enum class AiProviderType(val wire: String, val label: String) {
    API_TOKEN("api_token", "API token"),
    LOCAL_MODELS("local_models", "Local AI");

    companion object {
        fun fromWire(value: String?): AiProviderType = when (value?.trim()?.lowercase()) {
            LOCAL_MODELS.wire -> LOCAL_MODELS
            // One-way migration from every retired remote/demo route. They are no longer callable.
            "cli_relay", "company_backend", "mock", API_TOKEN.wire -> API_TOKEN
            else -> API_TOKEN
        }
    }
}

enum class ApiProvider(
    val wire: String,
    val label: String,
    val baseUrl: String,
    val defaultModel: String,
) {
    OPENAI("openai", "OpenAI", "https://api.openai.com/v1", "gpt-5"),
    GOOGLE("google", "Google Gemini", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-3.7-flash"),
    DEEPSEEK("deepseek", "DeepSeek", "https://api.deepseek.com", "deepseek-v4-flash"),
    OPENROUTER("openrouter", "OpenRouter", "https://openrouter.ai/api/v1", "openai/gpt-5.3-chat");

    companion object {
        fun fromWire(value: String?): ApiProvider =
            entries.firstOrNull { it.wire == value } ?: OPENAI
    }
}

/** The only remote AI configuration: direct provider API keys plus a selected model. */
object AiProviderPrefs {
    private const val PREFS_NAME = "ai_provider_prefs"
    private const val SECRET_PREFS_NAME = "ai_api_secrets"
    private const val KEY_PROVIDER = "provider"
    private const val KEY_API_PROVIDER = "api_provider"

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
        AiProviderType.fromWire(prefs(context).getString(KEY_PROVIDER, AiProviderType.API_TOKEN.wire))

    fun setProvider(context: Context, provider: AiProviderType) {
        prefs(context).edit().putString(KEY_PROVIDER, provider.wire).apply()
    }

    fun getApiProvider(context: Context): ApiProvider =
        ApiProvider.fromWire(prefs(context).getString(KEY_API_PROVIDER, ApiProvider.OPENAI.wire))

    fun setApiProvider(context: Context, provider: ApiProvider) {
        prefs(context).edit().putString(KEY_API_PROVIDER, provider.wire).apply()
    }

    fun getApiKey(context: Context, provider: ApiProvider = getApiProvider(context)): String {
        val stored = secretPrefs(context).getString(apiKeyKey(provider), "")?.trim().orEmpty()
        if (stored.isNotBlank()) return stored
        return if (provider == ApiProvider.OPENAI) BuildConfig.OPENAI_API_KEY.trim() else ""
    }

    fun setApiKey(context: Context, provider: ApiProvider, value: String) {
        secretPrefs(context).edit().putString(apiKeyKey(provider), value.trim()).apply()
    }

    fun getModel(context: Context, provider: ApiProvider = getApiProvider(context)): String =
        prefs(context).getString(modelKey(provider), provider.defaultModel)?.trim().orEmpty()
            .ifBlank { provider.defaultModel }

    fun setModel(context: Context, provider: ApiProvider, value: String) {
        prefs(context).edit().putString(modelKey(provider), value.trim()).apply()
    }

    fun isApiConfigured(context: Context, provider: ApiProvider = getApiProvider(context)): Boolean =
        getApiKey(context, provider).isNotBlank() && getModel(context, provider).isNotBlank()

    private fun apiKeyKey(provider: ApiProvider) = "api_key_${provider.wire}"
    private fun modelKey(provider: ApiProvider) = "model_${provider.wire}"
}

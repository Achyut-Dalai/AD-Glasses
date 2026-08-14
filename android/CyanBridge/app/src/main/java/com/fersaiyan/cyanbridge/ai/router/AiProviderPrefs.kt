package com.fersaiyan.cyanbridge.ai.router

import android.content.Context

enum class AiProviderType(val wire: String, val label: String) {
    MOCK("mock", "Mock (local demo)"),
    COMPANY_BACKEND("company_backend", "Company Backend (stub)"),
    CLI_RELAY("cli_relay", "CLI Relay (Codex/Gemini)"),
    LOCAL_MODELS("local_models", "Local Models (on-device)");

    companion object {
        fun fromWire(value: String?): AiProviderType =
            entries.firstOrNull { it.wire == value } ?: MOCK
    }
}

enum class CliRelayBackend(val wire: String, val label: String) {
    GEMINI("gemini", "Gemini CLI"),
    CODEX("codex", "Codex CLI");

    companion object {
        fun fromWire(value: String?): CliRelayBackend =
            entries.firstOrNull { it.wire == value } ?: GEMINI
    }
}

object AiProviderPrefs {
    private const val PREFS_NAME = "ai_provider_prefs"
    private const val KEY_PROVIDER = "provider"
    private const val KEY_RELAY_BASE_URL = "relay_base_url"
    private const val KEY_RELAY_BACKEND = "relay_backend"
    private val AUTHOR_RELAY_URLS = setOf(
        "https://carelens-wine.vercel.app",
        "https://cyanbridge.vercel.app",
    )

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getProvider(context: Context): AiProviderType =
        AiProviderType.fromWire(prefs(context).getString(KEY_PROVIDER, AiProviderType.CLI_RELAY.wire))

    fun setProvider(context: Context, provider: AiProviderType) {
        prefs(context).edit().putString(KEY_PROVIDER, provider.wire).apply()
    }

    fun getRelayBaseUrl(context: Context): String =
        prefs(context).getString(KEY_RELAY_BASE_URL, "")
            ?.trim()
            .orEmpty()
            .let { current ->
                if (current.trimEnd('/') in AUTHOR_RELAY_URLS) "" else current
            }

    fun setRelayBaseUrl(context: Context, value: String) {
        prefs(context).edit().putString(KEY_RELAY_BASE_URL, value.trim().trimEnd('/')).apply()
    }

    fun isRelayConfigured(context: Context): Boolean = getRelayBaseUrl(context).isNotBlank()

    fun getRelayBackend(context: Context): CliRelayBackend =
        CliRelayBackend.fromWire(prefs(context).getString(KEY_RELAY_BACKEND, CliRelayBackend.GEMINI.wire))

    fun setRelayBackend(context: Context, backend: CliRelayBackend) {
        prefs(context).edit().putString(KEY_RELAY_BACKEND, backend.wire).apply()
    }
}

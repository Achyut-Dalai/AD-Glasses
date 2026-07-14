package com.fersaiyan.cyanbridge.localmodels.remote

import android.content.Context

/**
 * Stores configuration for a remote OpenAI-compatible inference server
 * (Ollama, llama.cpp server, vLLM, text-generation-inference, etc.).
 *
 * Users can point this at any server on their LAN or Tailnet that exposes
 * the POST /v1/chat/completions endpoint.
 */
object RemoteOpenAiPrefs {
    private const val PREFS = "remote_openai_prefs"
    private const val KEY_BASE_URL = "base_url"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_MODEL = "model"
    private const val KEY_ENABLED = "enabled"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Base URL, e.g. "http://192.168.1.50:11434/v1" or "http://100.64.0.1:8080/v1". */
    fun getBaseUrl(context: Context): String {
        return prefs(context).getString(KEY_BASE_URL, "")?.trim().orEmpty()
    }

    fun setBaseUrl(context: Context, url: String) {
        prefs(context).edit().putString(KEY_BASE_URL, url.trim()).apply()
    }

    /** Optional API key (some servers like OpenAI-compatible proxies require one). */
    fun getApiKey(context: Context): String {
        return prefs(context).getString(KEY_API_KEY, "")?.trim().orEmpty()
    }

    fun setApiKey(context: Context, key: String) {
        prefs(context).edit().putString(KEY_API_KEY, key.trim()).apply()
    }

    /** Model name to send in the request, e.g. "llama3", "qwen2.5:7b", "gpt-3.5-turbo". */
    fun getModel(context: Context): String {
        return prefs(context).getString(KEY_MODEL, "")?.trim().orEmpty()
    }

    fun setModel(context: Context, model: String) {
        prefs(context).edit().putString(KEY_MODEL, model.trim()).apply()
    }

    /** Whether the remote server is enabled as the active local-model backend. */
    fun isEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_ENABLED, false)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /** Returns true if we have at least a base URL and model configured. */
    fun isConfigured(context: Context): Boolean {
        return getBaseUrl(context).isNotBlank() && getModel(context).isNotBlank()
    }
}

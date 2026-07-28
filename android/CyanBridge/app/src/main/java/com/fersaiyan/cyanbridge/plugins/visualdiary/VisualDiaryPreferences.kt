package com.fersaiyan.cyanbridge.plugins.visualdiary

import android.content.Context

object VisualDiaryPreferences {
    private const val PREFS = "visual_diary_prefs"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_INTERVAL_MINUTES = "interval_minutes"
    private const val KEY_CUSTOM_PROMPT = "custom_prompt"
    private const val KEY_LAST_ERROR = "last_error"
    private const val MAX_PROMPT_CHARS = 1_500

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun getIntervalMinutes(context: Context): Int =
        prefs(context).getInt(KEY_INTERVAL_MINUTES, 15).coerceIn(1, 240)

    fun setIntervalMinutes(context: Context, minutes: Int) {
        prefs(context).edit()
            .putInt(KEY_INTERVAL_MINUTES, minutes.coerceIn(1, 240))
            .apply()
    }

    fun getCustomPrompt(context: Context): String =
        prefs(context).getString(KEY_CUSTOM_PROMPT, "").orEmpty().take(MAX_PROMPT_CHARS)

    fun setCustomPrompt(context: Context, prompt: String) {
        prefs(context).edit()
            .putString(KEY_CUSTOM_PROMPT, prompt.trim().take(MAX_PROMPT_CHARS))
            .apply()
    }

    fun getLastError(context: Context): String =
        prefs(context).getString(KEY_LAST_ERROR, "").orEmpty()

    fun setLastError(context: Context, error: String) {
        prefs(context).edit().putString(KEY_LAST_ERROR, error.trim().take(500)).apply()
    }

    fun clearLastError(context: Context) {
        prefs(context).edit().remove(KEY_LAST_ERROR).apply()
    }
}

package com.achyut.adglasses.plugins.errandbrain

import android.content.Context

object ErrandBrainPreferences {
    private const val PREFS = "errand_brain_prefs"

    private const val KEY_ENABLED = "enabled"
    private const val KEY_AUTO_CREATE_TASKS = "auto_create_tasks"
    private const val KEY_VOICE_COMMANDS = "voice_commands"
    private const val KEY_REMINDER_ENABLED = "reminder_enabled"
    private const val KEY_REMINDER_INTERVAL_MINUTES = "reminder_interval_minutes"
    private const val KEY_DEFAULT_PRIORITY = "default_priority"
    private const val KEY_DEFAULT_CATEGORY = "default_category"
    private const val KEY_CLOUD_API_MODEL_ID = "cloud_model_id"
    private const val KEY_MAX_HISTORY = "max_history"
    private const val KEY_CUSTOM_PROMPT = "custom_prompt"
    private const val MAX_CUSTOM_PROMPT_CHARS = 1_000

    private const val DEFAULT_ENABLED = false
    private const val DEFAULT_AUTO_CREATE_TASKS = true
    private const val DEFAULT_VOICE_COMMANDS = true
    private const val DEFAULT_REMINDER_ENABLED = true
    private const val DEFAULT_REMINDER_INTERVAL_MINUTES = 30
    private const val DEFAULT_DEFAULT_PRIORITY = "medium"
    private const val DEFAULT_DEFAULT_CATEGORY = "personal"
    private const val DEFAULT_CLOUD_API_MODEL_ID = "deepseek/deepseek-v4-flash"
    private const val DEFAULT_MAX_HISTORY = 200

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, DEFAULT_ENABLED)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun isAutoCreateTasks(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_CREATE_TASKS, DEFAULT_AUTO_CREATE_TASKS)

    fun setAutoCreateTasks(context: Context, autoCreate: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_CREATE_TASKS, autoCreate).apply()
    }

    fun isVoiceCommands(context: Context): Boolean =
        prefs(context).getBoolean(KEY_VOICE_COMMANDS, DEFAULT_VOICE_COMMANDS)

    fun setVoiceCommands(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_VOICE_COMMANDS, enabled).apply()
    }

    fun isReminderEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_REMINDER_ENABLED, DEFAULT_REMINDER_ENABLED)

    fun setReminderEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_REMINDER_ENABLED, enabled).apply()
    }

    fun getReminderIntervalMinutes(context: Context): Int =
        prefs(context).getInt(KEY_REMINDER_INTERVAL_MINUTES, DEFAULT_REMINDER_INTERVAL_MINUTES)
            .coerceIn(5, 120)

    fun setReminderIntervalMinutes(context: Context, minutes: Int) {
        prefs(context).edit().putInt(KEY_REMINDER_INTERVAL_MINUTES, minutes.coerceIn(5, 120)).apply()
    }

    fun getDefaultPriority(context: Context): String =
        prefs(context).getString(KEY_DEFAULT_PRIORITY, DEFAULT_DEFAULT_PRIORITY).orEmpty()

    fun setDefaultPriority(context: Context, priority: String) {
        prefs(context).edit().putString(KEY_DEFAULT_PRIORITY, priority).apply()
    }

    fun getDefaultCategory(context: Context): String =
        prefs(context).getString(KEY_DEFAULT_CATEGORY, DEFAULT_DEFAULT_CATEGORY).orEmpty()

    fun setDefaultCategory(context: Context, category: String) {
        prefs(context).edit().putString(KEY_DEFAULT_CATEGORY, category).apply()
    }

    fun getCloudModelId(context: Context): String =
        prefs(context).getString(KEY_CLOUD_API_MODEL_ID, DEFAULT_CLOUD_API_MODEL_ID).orEmpty()

    fun setCloudModelId(context: Context, modelId: String) {
        prefs(context).edit().putString(KEY_CLOUD_API_MODEL_ID, modelId).apply()
    }

    fun getMaxHistory(context: Context): Int =
        prefs(context).getInt(KEY_MAX_HISTORY, DEFAULT_MAX_HISTORY).coerceIn(50, 1000)

    fun setMaxHistory(context: Context, count: Int) {
        prefs(context).edit().putInt(KEY_MAX_HISTORY, count.coerceIn(50, 1000)).apply()
    }

    fun getCustomPrompt(context: Context): String =
        prefs(context).getString(KEY_CUSTOM_PROMPT, "").orEmpty()
            .take(MAX_CUSTOM_PROMPT_CHARS)

    fun setCustomPrompt(context: Context, prompt: String) {
        prefs(context).edit().putString(KEY_CUSTOM_PROMPT, prompt.trim().take(MAX_CUSTOM_PROMPT_CHARS)).apply()
    }
}

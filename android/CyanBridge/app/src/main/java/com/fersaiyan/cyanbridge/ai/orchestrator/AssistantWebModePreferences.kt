package com.fersaiyan.cyanbridge.ai.orchestrator

import android.content.Context

enum class AssistantWebMode {
    AUTO,
    ON,
}

object AssistantWebModePreferences {
    private const val PREFS = "assistant_web_mode"
    private const val KEY_MODE = "mode"

    fun get(context: Context): AssistantWebMode {
        val stored = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_MODE, AssistantWebMode.AUTO.name)
        return runCatching { AssistantWebMode.valueOf(stored ?: AssistantWebMode.AUTO.name) }
            .getOrDefault(AssistantWebMode.AUTO)
    }

    fun set(context: Context, mode: AssistantWebMode) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, mode.name)
            .apply()
    }

    /** null keeps the existing automatic freshness policy; true forces web grounding. */
    fun explicitOverride(context: Context): Boolean? = when (get(context)) {
        AssistantWebMode.AUTO -> null
        AssistantWebMode.ON -> true
    }
}

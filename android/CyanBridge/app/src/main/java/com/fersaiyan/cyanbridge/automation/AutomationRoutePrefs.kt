package com.fersaiyan.cyanbridge.automation

import android.content.Context

enum class AutomationExecutor {
    ANDROID,
    TASKER,
}

/** Automation routing is independent from the selected AI provider. */
object AutomationRoutePrefs {
    private const val PREFS = "automation_routing"
    private const val KEY_EXECUTOR = "executor"

    fun getExecutor(context: Context): AutomationExecutor {
        val raw = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_EXECUTOR, null)
        return AutomationExecutor.entries.firstOrNull { it.name == raw } ?: AutomationExecutor.ANDROID
    }

    fun setExecutor(context: Context, executor: AutomationExecutor) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_EXECUTOR, executor.name)
            .apply()
    }
}

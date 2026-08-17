package com.fersaiyan.cyanbridge.automation

import android.content.Context

enum class AutomationExecutor {
    TASKER,
    ACCESSIBILITY,
}

/**
 * Automation routing is independent from the selected AI provider.
 *
 * Background execution is the product default. Accessibility is an explicit fallback for
 * actions Android or Tasker cannot complete without a visible UI.
 */
object AutomationRoutePrefs {
    private const val PREFS = "automation_routing"
    private const val KEY_EXECUTOR = "executor"

    fun getExecutor(context: Context): AutomationExecutor {
        val preferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = preferences.getString(KEY_EXECUTOR, null)?.trim()?.uppercase()
        val executor = when (raw) {
            AutomationExecutor.TASKER.name -> AutomationExecutor.TASKER
            AutomationExecutor.ACCESSIBILITY.name,
            "ANDROID" -> AutomationExecutor.ACCESSIBILITY
            null,
            "" -> AutomationExecutor.TASKER
            else -> AutomationExecutor.TASKER
        }
        if (raw != executor.name) {
            preferences.edit().putString(KEY_EXECUTOR, executor.name).apply()
        }
        return executor
    }

    fun setExecutor(context: Context, executor: AutomationExecutor) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_EXECUTOR, executor.name)
            .apply()
    }
}

package com.ad_glasses.agent

import android.content.Context
import com.ad_glasses.shared.settings.AgentProviderType

object LocalAgentPrefs {
    private const val PREFS = "local_agent_prefs"
    private const val KEY_PROVIDER_TYPE = "provider_type"
    private const val KEY_REQUIRE_CONFIRMATION = "require_confirmation"
    private const val KEY_MAX_STEPS = "max_steps"
    private const val KEY_AUTOMATION_ENABLED = "automation_enabled"
    private const val KEY_AUTO_CAPTURE_ENABLED = "auto_capture_enabled"
    private const val KEY_CAPTURE_INTERVAL_MIN = "capture_interval_min"
    private const val KEY_CAPTURE_BLACKLIST = "capture_blacklist"
    private const val KEY_HIDE_SYSTEM_APPS = "hide_system_apps"
    private const val KEY_DAILY_FACTS_REMINDER_ENABLED = "daily_facts_reminder_enabled"
    private const val KEY_DAILY_SUMMARY_AUTO_REFRESH_HOURS = "daily_summary_auto_refresh_hours"

    /** Any retired local-model planning value migrates forward to Cloud AI. */
    fun getProviderType(context: Context): AgentProviderType {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.contains(KEY_PROVIDER_TYPE)) prefs.edit().remove(KEY_PROVIDER_TYPE).apply()
        return AgentProviderType.CLOUD_AI
    }

    fun setProviderType(context: Context, type: AgentProviderType) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_PROVIDER_TYPE).apply()
    }

    /** Accessibility/UI automation is not an AI invocation method. */
    fun isLocalAgentAutomationEnabled(context: Context): Boolean = false

    fun setLocalAgentAutomationEnabled(context: Context, enabled: Boolean) {
        if (enabled) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().remove(KEY_AUTOMATION_ENABLED).apply()
        }
    }

    fun isRequireConfirmationEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_REQUIRE_CONFIRMATION, true)

    fun setRequireConfirmationEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_REQUIRE_CONFIRMATION, enabled).apply()
    }

    fun getMaxSteps(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_MAX_STEPS, 8).coerceIn(1, 200)

    fun setMaxSteps(context: Context, steps: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_MAX_STEPS, steps.coerceIn(1, 200)).apply()
    }

    fun isAutoCaptureEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_AUTO_CAPTURE_ENABLED, false)

    fun setAutoCaptureEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_AUTO_CAPTURE_ENABLED, enabled).apply()
    }

    fun getCaptureIntervalMin(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_CAPTURE_INTERVAL_MIN, 10).coerceIn(1, 24 * 60)

    fun setCaptureIntervalMin(context: Context, minutes: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_CAPTURE_INTERVAL_MIN, minutes.coerceIn(1, 24 * 60)).apply()
    }

    fun getCaptureBlacklistPackages(context: Context): Set<String> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(KEY_CAPTURE_BLACKLIST, null) ?: emptySet()
        return raw.map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
    }

    fun setCaptureBlacklistPackages(context: Context, packages: Set<String>) {
        val clean = packages.map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet(KEY_CAPTURE_BLACKLIST, HashSet(clean)).commit()
    }

    fun isHideSystemAppsEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_HIDE_SYSTEM_APPS, true)

    fun setHideSystemAppsEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_HIDE_SYSTEM_APPS, enabled).apply()
    }

    fun isDailyFactsReminderEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_DAILY_FACTS_REMINDER_ENABLED, true)

    fun setDailyFactsReminderEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_DAILY_FACTS_REMINDER_ENABLED, enabled).apply()
    }

    fun getDailySummaryAutoRefreshHours(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_DAILY_SUMMARY_AUTO_REFRESH_HOURS, 3).coerceIn(1, 24)

    fun setDailySummaryAutoRefreshHours(context: Context, hours: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_DAILY_SUMMARY_AUTO_REFRESH_HOURS, hours.coerceIn(1, 24)).apply()
    }
}

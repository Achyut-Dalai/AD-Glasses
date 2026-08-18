package com.fersaiyan.cyanbridge.ui.adglasses

import android.content.Context

internal object ADWelcomePreferences {
    private const val PREFS = "ad_glasses_onboarding"
    private const val KEY_COMPLETE = "welcome_complete"

    fun isComplete(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_COMPLETE, false)

    fun markComplete(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_COMPLETE, true)
            .apply()
    }
}

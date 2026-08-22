package com.ad_glasses.ui.adglasses

import android.content.Context

internal object ADWelcomePreferences {
    private const val PREFS = "ad_glasses_welcome"
    private const val KEY_COMPLETE = "complete"

    fun isComplete(context: Context): Boolean = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getBoolean(KEY_COMPLETE, false)

    fun markComplete(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_COMPLETE, true)
            .apply()
    }
}

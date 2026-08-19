package com.fersaiyan.cyanbridge.ui.adglasses

import android.content.Context

/** AD Glasses intentionally ships with one light product theme. */
enum class ADThemeStyle(val label: String) {
    OPTICAL_FROST("Optical Frost"),
}

internal object ADThemePreferences {
    private const val PREFS = "ad_glasses_theme"
    private const val KEY_STYLE = "style"

    fun get(context: Context): ADThemeStyle {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_STYLE, ADThemeStyle.OPTICAL_FROST.name)
        return when (stored) {
            ADThemeStyle.OPTICAL_FROST.name,
            "MONOCHROME",
            "MONO",
            "VIBE" -> ADThemeStyle.OPTICAL_FROST
            else -> ADThemeStyle.OPTICAL_FROST
        }
    }

    fun set(context: Context, style: ADThemeStyle) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_STYLE, style.name)
            .apply()
    }
}

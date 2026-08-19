package com.fersaiyan.cyanbridge.ui.adglasses

import android.content.Context

/** AD Glasses intentionally ships with one light product theme. */
enum class ADThemeStyle(val label: String) {
    MONOCHROME("Optical Frost"),
}

internal object ADThemePreferences {
    private const val PREFS = "ad_glasses_theme"
    private const val KEY_STYLE = "style"

    fun get(context: Context): ADThemeStyle {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_STYLE, ADThemeStyle.MONOCHROME.name)
        return when (stored) {
            ADThemeStyle.MONOCHROME.name,
            "OPTICAL_FROST",
            "MONO",
            "VIBE" -> ADThemeStyle.MONOCHROME
            else -> ADThemeStyle.MONOCHROME
        }
    }

    fun set(context: Context, style: ADThemeStyle) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_STYLE, style.name)
            .apply()
    }
}

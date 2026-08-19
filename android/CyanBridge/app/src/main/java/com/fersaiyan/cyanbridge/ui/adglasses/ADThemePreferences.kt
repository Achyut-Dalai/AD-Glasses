package com.fersaiyan.cyanbridge.ui.adglasses

import android.content.Context

enum class ADThemeStyle(val label: String) {
    COLOR("Color"),
    MONOCHROME("Monochrome"),
}

internal object ADThemePreferences {
    private const val PREFS = "ad_glasses_theme"
    private const val KEY_STYLE = "style"
    private const val KEY_DARK = "dark_mode"

    fun get(context: Context): ADThemeStyle {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_STYLE, ADThemeStyle.COLOR.name)
        return when (stored) {
            ADThemeStyle.COLOR.name -> ADThemeStyle.COLOR
            ADThemeStyle.MONOCHROME.name -> ADThemeStyle.MONOCHROME
            "MONO" -> ADThemeStyle.MONOCHROME
            "VIBE" -> ADThemeStyle.COLOR
            else -> ADThemeStyle.COLOR
        }
    }

    fun set(context: Context, style: ADThemeStyle) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_STYLE, style.name)
            .apply()
    }

    fun isDark(context: Context): Boolean = context
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getBoolean(KEY_DARK, false)

    fun setDark(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DARK, enabled)
            .apply()
    }
}

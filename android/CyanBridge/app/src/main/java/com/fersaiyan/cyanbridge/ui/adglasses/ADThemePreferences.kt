package com.fersaiyan.cyanbridge.ui.adglasses

import android.content.Context

enum class ADThemeStyle(val label: String) {
    MONOCHROME("Monochrome"),
    DARK_MONOCHROME("Dark Monochrome"),
}

internal object ADThemePreferences {
    private const val PREFS = "ad_glasses_theme"
    private const val KEY_STYLE = "style"

    fun get(context: Context): ADThemeStyle {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_STYLE, ADThemeStyle.MONOCHROME.name)

        // Retired light-theme names always migrate to Monochrome.
        return when (stored) {
            "MONO", "VIBE" -> ADThemeStyle.MONOCHROME
            else -> runCatching { ADThemeStyle.valueOf(stored ?: ADThemeStyle.MONOCHROME.name) }
                .getOrDefault(ADThemeStyle.MONOCHROME)
        }
    }

    fun set(context: Context, style: ADThemeStyle) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_STYLE, style.name)
            .apply()
    }
}

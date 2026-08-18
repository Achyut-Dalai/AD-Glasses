package com.fersaiyan.cyanbridge.ui.adglasses

import android.content.Context

enum class ADThemeStyle(val label: String) {
    MONOCHROME("Monochrome"),
}

internal object ADThemePreferences {
    private const val PREFS = "ad_glasses_theme"
    private const val KEY_STYLE = "style"

    fun get(context: Context): ADThemeStyle {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_STYLE, ADThemeStyle.MONOCHROME.name)

        // Vibe and the old MONO enum are intentionally retired. Existing installs migrate
        // to the single supported light Monochrome product theme until Dark Monochrome lands.
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

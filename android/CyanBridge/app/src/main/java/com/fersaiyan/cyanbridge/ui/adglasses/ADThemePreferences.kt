package com.fersaiyan.cyanbridge.ui.adglasses

import android.content.Context

enum class ADThemeStyle(val label: String) {
    MONO("Mono"),
    VIBE("Vibe"),
}

internal object ADThemePreferences {
    private const val PREFS = "ad_glasses_theme"
    private const val KEY_STYLE = "style"

    fun get(context: Context): ADThemeStyle {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_STYLE, ADThemeStyle.MONO.name)
        return runCatching { ADThemeStyle.valueOf(stored ?: ADThemeStyle.MONO.name) }
            .getOrDefault(ADThemeStyle.MONO)
    }

    fun set(context: Context, style: ADThemeStyle) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_STYLE, style.name)
            .apply()
    }
}

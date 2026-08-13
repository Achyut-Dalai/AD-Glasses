package com.achyut.adglasses.ui.appearance

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.achyut.adglasses.shared.appearance.AccentProfiles
import com.achyut.adglasses.shared.appearance.AppearanceSettings
import com.achyut.adglasses.shared.appearance.ThemeMode

class AppearancePreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): AppearanceSettings {
        val themeMode = runCatching {
            ThemeMode.valueOf(preferences.getString(KEY_THEME_MODE, null) ?: ThemeMode.SYSTEM.name)
        }.getOrDefault(ThemeMode.SYSTEM)
        val accentId = preferences.getString(KEY_ACCENT_PROFILE, AccentProfiles.ADGLASSES_ID)
            ?.takeIf { candidate -> AccentProfiles.all.any { it.id == candidate } }
            ?: AccentProfiles.ADGLASSES_ID
        return AppearanceSettings(
            themeMode = themeMode,
            accentProfileId = accentId,
            useDynamicColor = preferences.getBoolean(KEY_DYNAMIC_COLOR, false),
            highContrast = preferences.getBoolean(KEY_HIGH_CONTRAST, false),
        )
    }

    fun save(settings: AppearanceSettings) {
        preferences.edit()
            .putString(KEY_THEME_MODE, settings.themeMode.name)
            .putString(KEY_ACCENT_PROFILE, settings.accentProfileId)
            .putBoolean(KEY_DYNAMIC_COLOR, settings.useDynamicColor)
            .putBoolean(KEY_HIGH_CONTRAST, settings.highContrast)
            .apply()
    }

    fun reset() = save(AppearanceSettings())

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        preferences.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        preferences.unregisterOnSharedPreferenceChangeListener(listener)
    }

    private companion object {
        const val PREFS_NAME = "appearance_settings"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_ACCENT_PROFILE = "accent_profile"
        const val KEY_DYNAMIC_COLOR = "dynamic_color"
        const val KEY_HIGH_CONTRAST = "high_contrast"
    }
}

@Composable
fun rememberAppearanceSettings(preferences: AppearancePreferences): State<AppearanceSettings> {
    val state = remember(preferences) { mutableStateOf(preferences.load()) }
    DisposableEffect(preferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            state.value = preferences.load()
        }
        preferences.registerListener(listener)
        onDispose { preferences.unregisterListener(listener) }
    }
    return state
}

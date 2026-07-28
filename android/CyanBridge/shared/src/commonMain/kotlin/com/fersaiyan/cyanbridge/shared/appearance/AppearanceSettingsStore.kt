package com.fersaiyan.cyanbridge.shared.appearance

import com.fersaiyan.cyanbridge.shared.platform.PlatformPreferences

const val APPEARANCE_PREFERENCES_NAME = "cyanbridge_appearance"

/**
 * Persists the platform-neutral appearance choices. Platforms can disable
 * dynamic color when their UI toolkit does not provide it.
 */
class AppearanceSettingsStore(
    private val preferences: PlatformPreferences,
    private val dynamicColorAvailable: Boolean = true,
) {
    fun load(): AppearanceSettings {
        val themeMode = runCatching {
            ThemeMode.valueOf(preferences.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name))
        }.getOrDefault(ThemeMode.SYSTEM)
        return AppearanceSettings(
            themeMode = themeMode,
            accentProfileId = AccentProfiles.find(
                preferences.getString(KEY_ACCENT_PROFILE, AccentProfiles.CYAN_ID),
            ).id,
            useDynamicColor = preferences.getBoolean(KEY_DYNAMIC_COLOR, false) && dynamicColorAvailable,
            highContrast = preferences.getBoolean(KEY_HIGH_CONTRAST, false),
        )
    }

    fun save(settings: AppearanceSettings) {
        val normalized = settings.copy(
            accentProfileId = AccentProfiles.find(settings.accentProfileId).id,
            useDynamicColor = settings.useDynamicColor && dynamicColorAvailable,
        )
        preferences.putString(KEY_THEME_MODE, normalized.themeMode.name)
        preferences.putString(KEY_ACCENT_PROFILE, normalized.accentProfileId)
        preferences.putBoolean(KEY_DYNAMIC_COLOR, normalized.useDynamicColor)
        preferences.putBoolean(KEY_HIGH_CONTRAST, normalized.highContrast)
    }

    fun reset() = save(AppearanceSettings())

    private companion object {
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_ACCENT_PROFILE = "accent_profile"
        const val KEY_DYNAMIC_COLOR = "dynamic_color"
        const val KEY_HIGH_CONTRAST = "high_contrast"
    }
}

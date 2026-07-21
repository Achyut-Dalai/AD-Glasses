package com.fersaiyan.cyanbridge.shared.ui.theme

import androidx.compose.ui.graphics.Color
import com.fersaiyan.cyanbridge.shared.appearance.APPEARANCE_PREFERENCES_NAME
import com.fersaiyan.cyanbridge.shared.appearance.AccentProfiles
import com.fersaiyan.cyanbridge.shared.appearance.AppearanceSettings
import com.fersaiyan.cyanbridge.shared.appearance.AppearanceSettingsStore
import com.fersaiyan.cyanbridge.shared.appearance.ThemeMode
import com.fersaiyan.cyanbridge.shared.platform.createPlatformPreferences
import kotlin.test.Test
import kotlin.test.assertEquals

class CyanBridgeThemeTest {
    @Test
    fun schemeUsesProfileSecondaryAndTertiaryColorsInBothModes() {
        val profile = AccentProfiles.find("rose")
        val light = cyanBridgeColorScheme(profile, darkTheme = false, highContrast = false)
        val dark = cyanBridgeColorScheme(profile, darkTheme = true, highContrast = false)

        assertEquals(Color(profile.lightSecondaryArgb), light.secondary)
        assertEquals(Color(profile.lightSecondaryContainerArgb), light.secondaryContainer)
        assertEquals(Color(profile.lightTertiaryArgb), light.tertiary)
        assertEquals(Color(profile.lightTertiaryContainerArgb), light.tertiaryContainer)
        assertEquals(Color(profile.darkSecondaryArgb), dark.secondary)
        assertEquals(Color(profile.darkSecondaryContainerArgb), dark.secondaryContainer)
        assertEquals(Color(profile.darkTertiaryArgb), dark.tertiary)
        assertEquals(Color(profile.darkTertiaryContainerArgb), dark.tertiaryContainer)
    }

    @Test
    fun highContrastAndThemeModeMappingArePlatformIndependent() {
        val profile = AccentProfiles.find(AccentProfiles.CYAN_ID)
        val highContrastLight = cyanBridgeColorScheme(profile, darkTheme = false, highContrast = true)
        val highContrastDark = cyanBridgeColorScheme(profile, darkTheme = true, highContrast = true)

        assertEquals(Color.Black, highContrastLight.onSurface)
        assertEquals(Color.White, highContrastLight.background)
        assertEquals(Color.Black, highContrastDark.background)
        assertEquals(Color.White, highContrastDark.onSurface)
        assertEquals(false, resolveDarkTheme(AppearanceSettings(ThemeMode.LIGHT), systemInDarkTheme = true))
        assertEquals(true, resolveDarkTheme(AppearanceSettings(ThemeMode.DARK), systemInDarkTheme = false))
        assertEquals(true, resolveDarkTheme(AppearanceSettings(), systemInDarkTheme = true))
    }

    @Test
    fun appearanceStorePersistsChoicesAndDisablesUnsupportedDynamicColor() {
        val preferences = createPlatformPreferences("${APPEARANCE_PREFERENCES_NAME}_test")
        preferences.clear()
        val store = AppearanceSettingsStore(preferences, dynamicColorAvailable = false)
        val requested = AppearanceSettings(
            themeMode = ThemeMode.DARK,
            accentProfileId = "lavender",
            useDynamicColor = true,
            highContrast = true,
        )

        store.save(requested)

        assertEquals(requested.copy(useDynamicColor = false), store.load())
        store.reset()
        assertEquals(AppearanceSettings(), store.load())
    }
}

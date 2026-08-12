package com.achyut.adglasses.shared.ui.theme

import androidx.compose.ui.graphics.Color
import com.achyut.adglasses.shared.appearance.AccentProfiles
import com.achyut.adglasses.shared.appearance.AppearanceSettings
import com.achyut.adglasses.shared.appearance.ThemeMode
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
}

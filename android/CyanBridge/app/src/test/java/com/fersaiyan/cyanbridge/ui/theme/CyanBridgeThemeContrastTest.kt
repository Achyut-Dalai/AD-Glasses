package com.fersaiyan.cyanbridge.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.fersaiyan.cyanbridge.shared.appearance.AccentProfiles
import org.junit.Assert.assertTrue
import org.junit.Test

class CyanBridgeThemeContrastTest {
    @Test
    fun curatedSchemesMeetTextAndControlContrastTargets() {
        AccentProfiles.all.forEach { profile ->
            listOf(false, true).forEach { darkTheme ->
                listOf(false, true).forEach { highContrast ->
                    val scheme = cyanBridgeColorScheme(profile, darkTheme, highContrast)
                    val name = "${profile.label}, dark=$darkTheme, highContrast=$highContrast"

                    assertContrast(name, "background text", scheme.onBackground, scheme.background, 4.5)
                    assertContrast(name, "surface text", scheme.onSurface, scheme.surface, 4.5)
                    assertContrast(name, "variant text", scheme.onSurfaceVariant, scheme.surfaceVariant, 4.5)
                    assertContrast(name, "primary action", scheme.onPrimary, scheme.primary, 4.5)
                    assertContrast(name, "primary container", scheme.onPrimaryContainer, scheme.primaryContainer, 4.5)
                    assertContrast(name, "control outline", scheme.outline, scheme.background, 3.0)
                }
            }
        }
    }

    private fun assertContrast(
        schemeName: String,
        pairName: String,
        foreground: Color,
        background: Color,
        minimum: Double,
    ) {
        val ratio = contrastRatio(foreground, background)
        assertTrue(
            "$schemeName $pairName contrast was $ratio, expected at least $minimum",
            ratio >= minimum,
        )
    }

    private fun contrastRatio(first: Color, second: Color): Double {
        val lighter = maxOf(first.luminance(), second.luminance()).toDouble()
        val darker = minOf(first.luminance(), second.luminance()).toDouble()
        return (lighter + 0.05) / (darker + 0.05)
    }
}

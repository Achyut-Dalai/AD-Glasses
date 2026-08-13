package com.achyut.adglasses.shared.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.achyut.adglasses.shared.appearance.AccentProfile
import com.achyut.adglasses.shared.appearance.AccentProfiles
import com.achyut.adglasses.shared.appearance.AppearanceSettings
import com.achyut.adglasses.shared.appearance.ThemeMode

private val LightBackground = Color(0xFFF8FAFB)
private val LightSurface = Color(0xFFFFFFFF)
private val LightOnSurface = Color(0xFF171C1E)
private val LightOnSurfaceVariant = Color(0xFF40484B)
private val DarkBackground = Color(0xFF0D1114)
private val DarkSurface = Color(0xFF151A1D)
private val DarkOnSurface = Color(0xFFE1E3E4)
private val DarkOnSurfaceVariant = Color(0xFFC1C7C9)

fun resolveDarkTheme(settings: AppearanceSettings, systemInDarkTheme: Boolean): Boolean = when (settings.themeMode) {
    ThemeMode.SYSTEM -> systemInDarkTheme
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/** Builds the curated scheme shared by Android, iOS, and every shared screen. */
fun adGlassesColorScheme(
    profile: AccentProfile,
    darkTheme: Boolean,
    highContrast: Boolean,
): ColorScheme {
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = Color(profile.darkPrimaryArgb),
            onPrimary = Color(0xFF002023),
            primaryContainer = Color(profile.darkContainerArgb),
            onPrimaryContainer = Color.White,
            secondary = Color(profile.darkSecondaryArgb),
            onSecondary = Color(0xFF002023),
            secondaryContainer = Color(profile.darkSecondaryContainerArgb),
            onSecondaryContainer = Color.White,
            tertiary = Color(profile.darkTertiaryArgb),
            onTertiary = Color(0xFF002023),
            tertiaryContainer = Color(profile.darkTertiaryContainerArgb),
            onTertiaryContainer = Color.White,
            background = DarkBackground,
            onBackground = DarkOnSurface,
            surface = DarkSurface,
            onSurface = DarkOnSurface,
            surfaceVariant = Color(0xFF202629),
            onSurfaceVariant = DarkOnSurfaceVariant,
            outline = Color(0xFF899294),
            outlineVariant = Color(0xFF3F484A),
            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690005),
        )
    } else {
        lightColorScheme(
            primary = Color(profile.lightPrimaryArgb),
            onPrimary = Color.White,
            primaryContainer = Color(profile.lightContainerArgb),
            onPrimaryContainer = Color(0xFF001F24),
            secondary = Color(profile.lightSecondaryArgb),
            onSecondary = Color.White,
            secondaryContainer = Color(profile.lightSecondaryContainerArgb),
            onSecondaryContainer = Color(0xFF001F24),
            tertiary = Color(profile.lightTertiaryArgb),
            onTertiary = Color.White,
            tertiaryContainer = Color(profile.lightTertiaryContainerArgb),
            onTertiaryContainer = Color(0xFF001F24),
            background = LightBackground,
            onBackground = LightOnSurface,
            surface = LightSurface,
            onSurface = LightOnSurface,
            surfaceVariant = Color(0xFFE7EBEC),
            onSurfaceVariant = LightOnSurfaceVariant,
            outline = Color(0xFF70797B),
            outlineVariant = Color(0xFFBFC8CA),
            error = Color(0xFFBA1A1A),
            onError = Color.White,
        )
    }
    return if (highContrast) highContrastColorScheme(scheme, darkTheme) else scheme
}

fun highContrastColorScheme(scheme: ColorScheme, darkTheme: Boolean): ColorScheme = if (darkTheme) {
    scheme.copy(
        background = Color.Black,
        surface = Color.Black,
        onBackground = Color.White,
        onSurface = Color.White,
        onSurfaceVariant = Color.White,
        outline = Color.White,
    )
} else {
    scheme.copy(
        background = Color.White,
        surface = Color.White,
        onBackground = Color.Black,
        onSurface = Color.Black,
        onSurfaceVariant = Color.Black,
        outline = Color.Black,
    )
}

/** Theme wrapper for shared and iOS Compose content, where dynamic color is unavailable. */
@Composable
fun AdGlassesMaterialTheme(
    settings: AppearanceSettings = AppearanceSettings(),
    content: @Composable () -> Unit,
) {
    val darkTheme = resolveDarkTheme(settings, isSystemInDarkTheme())
    MaterialTheme(
        colorScheme = adGlassesColorScheme(
            profile = AccentProfiles.find(settings.accentProfileId),
            darkTheme = darkTheme,
            highContrast = settings.highContrast,
        ),
        typography = Typography(),
        content = content,
    )
}

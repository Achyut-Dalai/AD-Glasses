package com.fersaiyan.cyanbridge.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.fersaiyan.cyanbridge.shared.appearance.AccentProfile
import com.fersaiyan.cyanbridge.shared.appearance.AccentProfiles
import com.fersaiyan.cyanbridge.shared.appearance.AppearanceSettings
import com.fersaiyan.cyanbridge.shared.appearance.ThemeMode

private val LightBackground = Color(0xFFF8FAFB)
private val LightSurface = Color(0xFFFFFFFF)
private val LightOnSurface = Color(0xFF171C1E)
private val LightOnSurfaceVariant = Color(0xFF40484B)
private val DarkBackground = Color(0xFF0D1114)
private val DarkSurface = Color(0xFF151A1D)
private val DarkOnSurface = Color(0xFFE1E3E4)
private val DarkOnSurfaceVariant = Color(0xFFC1C7C9)

fun cyanBridgeColorScheme(
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
            secondary = Color(profile.darkPrimaryArgb),
            onSecondary = Color(0xFF002023),
            secondaryContainer = Color(profile.darkContainerArgb),
            onSecondaryContainer = Color.White,
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
            secondary = Color(profile.lightPrimaryArgb),
            onSecondary = Color.White,
            secondaryContainer = Color(profile.lightContainerArgb),
            onSecondaryContainer = Color(0xFF001F24),
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
    if (!highContrast) return scheme
    return if (darkTheme) {
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
}

@Composable
fun CyanBridgeTheme(
    settings: AppearanceSettings = AppearanceSettings(),
    content: @Composable () -> Unit,
) {
    val darkTheme = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val dynamicColor = settings.useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val baseScheme = when {
        dynamicColor && darkTheme -> dynamicDarkColorScheme(context)
        dynamicColor -> dynamicLightColorScheme(context)
        else -> cyanBridgeColorScheme(
            profile = AccentProfiles.find(settings.accentProfileId),
            darkTheme = darkTheme,
            highContrast = settings.highContrast,
        )
    }
    val colorScheme = if (dynamicColor && settings.highContrast) {
        if (darkTheme) {
            baseScheme.copy(
                background = Color.Black,
                surface = Color.Black,
                onBackground = Color.White,
                onSurface = Color.White,
                onSurfaceVariant = Color.White,
                outline = Color.White,
            )
        } else {
            baseScheme.copy(
                background = Color.White,
                surface = Color.White,
                onBackground = Color.Black,
                onSurface = Color.Black,
                onSurfaceVariant = Color.Black,
                outline = Color.Black,
            )
        }
    } else {
        baseScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            context.findActivity()?.window?.let { window ->
                window.statusBarColor = colorScheme.background.toArgb()
                window.navigationBarColor = colorScheme.surface.toArgb()
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content,
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

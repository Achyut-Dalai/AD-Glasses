package com.achyut.adglasses.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.achyut.adglasses.shared.appearance.AccentProfile
import com.achyut.adglasses.shared.appearance.AccentProfiles
import com.achyut.adglasses.shared.appearance.AppearanceSettings
import com.achyut.adglasses.shared.ui.theme.highContrastColorScheme
import com.achyut.adglasses.shared.ui.theme.resolveDarkTheme
import com.achyut.adglasses.shared.ui.theme.cyanBridgeColorScheme as sharedCyanBridgeColorScheme

/** Kept as the Android-facing entry point for existing theme callers. */
fun cyanBridgeColorScheme(
    profile: AccentProfile,
    darkTheme: Boolean,
    highContrast: Boolean,
): ColorScheme = sharedCyanBridgeColorScheme(profile, darkTheme, highContrast)

@Composable
fun CyanBridgeTheme(
    settings: AppearanceSettings = AppearanceSettings(),
    content: @Composable () -> Unit,
) {
    val darkTheme = resolveDarkTheme(settings, isSystemInDarkTheme())
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
        highContrastColorScheme(baseScheme, darkTheme)
    } else baseScheme

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

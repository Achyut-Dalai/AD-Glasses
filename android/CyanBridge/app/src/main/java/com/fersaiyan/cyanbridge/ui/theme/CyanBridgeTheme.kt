package com.fersaiyan.cyanbridge.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
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
import com.fersaiyan.cyanbridge.shared.ui.theme.cyanBridgeColorScheme as sharedCyanBridgeColorScheme

/** Kept as the Android-facing entry point for existing theme callers. */
fun cyanBridgeColorScheme(
    profile: AccentProfile,
    darkTheme: Boolean,
    highContrast: Boolean,
): ColorScheme = sharedCyanBridgeColorScheme(profile, darkTheme, highContrast)

@Composable
fun CyanBridgeTheme(
    @Suppress("UNUSED_PARAMETER")
    settings: AppearanceSettings = AppearanceSettings(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = ADLegacyColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            context.findActivity()?.window?.let { window ->
                window.statusBarColor = colorScheme.background.toArgb()
                window.navigationBarColor = colorScheme.surface.toArgb()
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = true
                    isAppearanceLightNavigationBars = true
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

/**
 * Older Compose destinations still host working media, chat, and plugin logic.
 * Keep them visually inside the same light, neutral AD Glasses product until
 * each destination is replaced by the new shell.
 */
private val ADLegacyColorScheme = lightColorScheme(
    primary = Color(0xFF2C2C2E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEAEAED),
    onPrimaryContainer = Color(0xFF1D1D1F),
    secondary = Color(0xFF48484A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDEDEF),
    onSecondaryContainer = Color(0xFF1D1D1F),
    tertiary = Color(0xFF636366),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEDEDEF),
    onTertiaryContainer = Color(0xFF1D1D1F),
    background = Color(0xFFF5F5F7),
    onBackground = Color(0xFF1D1D1F),
    surface = Color.White,
    onSurface = Color(0xFF1D1D1F),
    surfaceVariant = Color(0xFFEDEDEF),
    onSurfaceVariant = Color(0xFF6E6E73),
    outline = Color(0xFFAEAEB2),
    outlineVariant = Color(0xFFD1D1D6),
    error = Color(0xFFFF3B30),
    onError = Color.White,
)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

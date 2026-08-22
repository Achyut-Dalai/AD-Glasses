package com.ad_glasses.ui.theme

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.ad_glasses.shared.appearance.AccentProfile
import com.ad_glasses.shared.appearance.AppearanceSettings
import com.ad_glasses.shared.ui.theme.ADGlassesColorScheme as sharedADGlassesColorScheme

/** Kept as the Android-facing entry point for existing Compose configuration callers. */
fun ADGlassesColorScheme(
    profile: AccentProfile,
    darkTheme: Boolean,
    highContrast: Boolean,
): ColorScheme = sharedADGlassesColorScheme(profile, darkTheme, highContrast)

@Composable
fun ADGlassesTheme(
    @Suppress("UNUSED_PARAMETER")
    settings: AppearanceSettings = AppearanceSettings(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = ADCompatibilityColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            context.findActivity()?.window?.let { window ->
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

/** Neutral AD Glasses styling for secondary Compose configuration surfaces. */
private val ADCompatibilityColorScheme = lightColorScheme(
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

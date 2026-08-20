package com.fersaiyan.cyanbridge.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.fersaiyan.cyanbridge.shared.appearance.AccentProfile
import com.fersaiyan.cyanbridge.shared.appearance.AppearanceSettings
import com.fersaiyan.cyanbridge.shared.ui.theme.cyanBridgeColorScheme as sharedCyanBridgeColorScheme

/** Kept as the Android-facing entry point for existing Compose configuration callers. */
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
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            context.findActivity()?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = false
                    isAppearanceLightNavigationBars = false
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = ADCompatibilityColorScheme,
        typography = ADCompatibilityTypography,
        content = content,
    )
}

/**
 * Neutral dark compatibility styling for secondary Compose surfaces that have not yet moved
 * into the AD product shell. Keeping these colors close to ADGlassesTheme prevents dark text
 * from leaking onto black product backgrounds during mixed-screen transitions.
 */
private val ADCompatibilityColorScheme = darkColorScheme(
    primary = Color(0xFFF4F4F2),
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF202020),
    onPrimaryContainer = Color(0xFFF4F4F2),
    secondary = Color(0xFFD9D9D5),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF171717),
    onSecondaryContainer = Color(0xFFF4F4F2),
    tertiary = Color(0xFFCA343A),
    onTertiary = Color(0xFFEADADA),
    tertiaryContainer = Color(0xFF2B1517),
    onTertiaryContainer = Color(0xFFEADADA),
    background = Color(0xFF171717),
    onBackground = Color(0xFFF4F4F2),
    surface = Color(0xFF090909),
    onSurface = Color(0xFFF4F4F2),
    surfaceVariant = Color(0xFF171717),
    onSurfaceVariant = Color(0xFFD9D9D5),
    outline = Color(0xFF343434),
    outlineVariant = Color(0xFF252525),
    error = Color(0xFFE4847F),
    onError = Color.Black,
)

private val ADCompatibilityTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp,
        lineHeight = 21.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        lineHeight = 19.sp,
        fontWeight = FontWeight.Medium,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 12.5.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Normal,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 10.5.sp,
        lineHeight = 15.sp,
        fontWeight = FontWeight.Medium,
    ),
)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

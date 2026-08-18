package com.fersaiyan.cyanbridge.ui.adglasses

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

internal object ADColors {
    val Ink = Color(0xFF1D1D1F)
    val Muted = Color(0xFF6E6E73)
    // Existing product chrome remains monochrome until each screen is intentionally migrated.
    // Semantic success/warning/error colours remain reserved for actual state feedback.
    val Blue = Color(0xFF2C2C2E)
    val BlueDeep = Color(0xFF111113)
    val BlueSoft = Color(0xFFEAEAED)
    val Background = Color(0xFFF5F5F7)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceSubtle = Color(0xFFEDEDEF)
    val Glass = Color(0xF7FFFFFF)
    val Outline = Color(0xFFC6C6C8)
    val Separator = Color(0x3C3C434A)
    val Success = Color(0xFF248A3D)
    val SuccessSoft = Color(0xFFEAF7ED)
    val Warning = Color(0xFFC93400)
    val WarningSoft = Color(0xFFFFF3E8)
    val Error = Color(0xFFFF3B30)
    val ErrorSoft = Color(0xFFFFEBEA)
}

private val MonoColorScheme = lightColorScheme(
    primary = Color(0xFF202124),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8E8EA),
    onPrimaryContainer = Color(0xFF171719),
    secondary = Color(0xFF55565A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDEDEF),
    onSecondaryContainer = Color(0xFF202124),
    tertiary = Color(0xFF6A6B70),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF1F1F3),
    onTertiaryContainer = Color(0xFF202124),
    background = Color(0xFFF5F5F7),
    onBackground = Color(0xFF1D1D1F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1D1D1F),
    surfaceVariant = Color(0xFFEDEDEF),
    onSurfaceVariant = Color(0xFF626368),
    outline = Color(0xFFC6C6C8),
    outlineVariant = Color(0xFFE0E0E3),
    error = ADColors.Error,
    onError = Color.White,
    errorContainer = ADColors.ErrorSoft,
    onErrorContainer = ADColors.Error,
)

/**
 * Vibe is an editorial, solid-colour theme: warm paper + deep teal + vermilion.
 * It deliberately avoids neon gradients and generic blue/purple "AI" styling.
 */
private val VibeColorScheme = lightColorScheme(
    primary = Color(0xFF006B6B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB8F1ED),
    onPrimaryContainer = Color(0xFF003737),
    secondary = Color(0xFFD65332),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDBD0),
    onSecondaryContainer = Color(0xFF571808),
    tertiary = Color(0xFF7A6300),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE36E),
    onTertiaryContainer = Color(0xFF302600),
    background = Color(0xFFF8F3E8),
    onBackground = Color(0xFF20201D),
    surface = Color(0xFFFFFBF3),
    onSurface = Color(0xFF20201D),
    surfaceVariant = Color(0xFFEDE6D8),
    onSurfaceVariant = Color(0xFF625E55),
    outline = Color(0xFF8E887D),
    outlineVariant = Color(0xFFD6CFC1),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val ADTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.7).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.55).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.25).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 21.sp,
        lineHeight = 27.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.1).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 19.sp,
        lineHeight = 25.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 12.5.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Normal,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.SemiBold,
    ),
)

private val MonoShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

private val VibeShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(32.dp),
    extraLarge = RoundedCornerShape(44.dp),
)

@Composable
fun ADGlassesTheme(
    style: ADThemeStyle = ADThemeStyle.MONO,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    val context = LocalContext.current
    val scheme = if (style == ADThemeStyle.VIBE) VibeColorScheme else MonoColorScheme
    val shapes = if (style == ADThemeStyle.VIBE) VibeShapes else MonoShapes

    if (!view.isInEditMode) {
        SideEffect {
            context.findActivity()?.window?.let { window ->
                // System-bar colours are intentionally not set: targetSdk 35 is edge-to-edge.
                // Only icon contrast is controlled through the non-deprecated insets controller.
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = true
                    isAppearanceLightNavigationBars = true
                }
            }
        }
    }

    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        shapes = shapes,
        typography = ADTypography,
        content = content,
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

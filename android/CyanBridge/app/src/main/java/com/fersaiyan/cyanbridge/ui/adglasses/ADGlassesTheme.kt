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

/**
 * Optical Frost is intentionally light-only: cool glass neutrals, precise graphite type,
 * and a restrained cyan accent that belongs to AD Glasses rather than generic Material UI.
 */
internal object ADColors {
    val Ink = Color(0xFF152126)
    val Muted = Color(0xFF64757B)
    val Graphite = Color(0xFF172126)
    val GraphiteSoft = Color(0xFF253238)

    val Cyan = Color(0xFF087F8C)
    val CyanDeep = Color(0xFF075F69)
    val CyanSoft = Color(0xFFE5F4F5)
    val CyanMist = Color(0xFFF1F8F8)

    // Kept as compatibility aliases while older surfaces are migrated.
    val Blue = Cyan
    val BlueDeep = Graphite
    val BlueSoft = CyanSoft

    val Background = Color(0xFFF6F8F9)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceSubtle = Color(0xFFEDF2F4)
    val SurfaceRaised = Color(0xFFFAFCFC)
    val Glass = Color(0xFAFFFFFF)
    val Outline = Color(0xFFD4E0E3)
    val OutlineStrong = Color(0xFFBAC9CD)
    val Separator = Color(0xFFDFE7E9)

    val Success = Color(0xFF218547)
    val SuccessSoft = Color(0xFFE8F5EC)
    val Warning = Color(0xFFB96516)
    val WarningSoft = Color(0xFFFFF3E6)
    val Error = Color(0xFFD83A34)
    val ErrorSoft = Color(0xFFFFECEA)
}

private val OpticalFrostColorScheme = lightColorScheme(
    primary = ADColors.Cyan,
    onPrimary = Color.White,
    primaryContainer = ADColors.CyanSoft,
    onPrimaryContainer = ADColors.Ink,
    secondary = ADColors.GraphiteSoft,
    onSecondary = Color.White,
    secondaryContainer = ADColors.SurfaceSubtle,
    onSecondaryContainer = ADColors.Ink,
    tertiary = ADColors.CyanDeep,
    onTertiary = Color.White,
    tertiaryContainer = ADColors.CyanMist,
    onTertiaryContainer = ADColors.Ink,
    background = ADColors.Background,
    onBackground = ADColors.Ink,
    surface = ADColors.Surface,
    onSurface = ADColors.Ink,
    surfaceVariant = ADColors.SurfaceSubtle,
    onSurfaceVariant = ADColors.Muted,
    outline = ADColors.OutlineStrong,
    outlineVariant = ADColors.Outline,
    error = ADColors.Error,
    onError = Color.White,
    errorContainer = ADColors.ErrorSoft,
    onErrorContainer = ADColors.Error,
)

private val ADTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 34.sp,
        lineHeight = 39.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.65).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 30.sp,
        lineHeight = 35.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.45).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 24.sp,
        lineHeight = 29.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.05).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 18.sp,
        lineHeight = 24.sp,
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
        fontSize = 14.5.sp,
        lineHeight = 21.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 13.sp,
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
        fontSize = 12.5.sp,
        lineHeight = 17.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
    ),
)

private val OpticalFrostShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun ADGlassesTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    val context = LocalContext.current

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

    MaterialExpressiveTheme(
        colorScheme = OpticalFrostColorScheme,
        motionScheme = MotionScheme.expressive(),
        shapes = OpticalFrostShapes,
        typography = ADTypography,
        content = content,
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

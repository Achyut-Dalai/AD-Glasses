package com.fersaiyan.cyanbridge.ui.adglasses

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

internal object ADColors {
    val Ink = Color(0xFF1D1D1F)
    val Muted = Color(0xFF6E6E73)
    // The interface is intentionally neutral. Colour is reserved for semantic
    // states such as success, warning, recording, and errors.
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

private val ADColorScheme = lightColorScheme(
    primary = ADColors.Blue,
    onPrimary = Color.White,
    primaryContainer = ADColors.BlueSoft,
    onPrimaryContainer = ADColors.Ink,
    secondary = ADColors.Ink,
    onSecondary = Color.White,
    secondaryContainer = ADColors.SurfaceSubtle,
    onSecondaryContainer = ADColors.Ink,
    background = ADColors.Background,
    onBackground = ADColors.Ink,
    surface = ADColors.Surface,
    onSurface = ADColors.Ink,
    surfaceVariant = ADColors.SurfaceSubtle,
    onSurfaceVariant = ADColors.Muted,
    outline = ADColors.Outline,
    outlineVariant = ADColors.Outline,
    error = ADColors.Error,
    onError = Color.White,
    errorContainer = ADColors.ErrorSoft,
    onErrorContainer = ADColors.Error,
)

private val ADTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.8).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.55).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.3).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp,
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
        fontWeight = FontWeight.Medium,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Medium,
    ),
)

@Composable
fun ADGlassesTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    val context = LocalContext.current
    if (!view.isInEditMode) {
        SideEffect {
            context.findActivity()?.window?.let { window ->
                window.statusBarColor = ADColors.Background.toArgb()
                window.navigationBarColor = ADColors.Surface.toArgb()
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = true
                    isAppearanceLightNavigationBars = true
                }
            }
        }
    }
    MaterialTheme(
        colorScheme = ADColorScheme,
        typography = ADTypography,
        content = content,
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

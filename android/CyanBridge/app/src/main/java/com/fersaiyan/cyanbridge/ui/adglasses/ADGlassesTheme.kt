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
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.fersaiyan.cyanbridge.R

internal object ADColors {
    val Ink = Color(0xFF1D1D1F)
    val Muted = Color(0xFF6E6E73)
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

private val MonochromeColorScheme = lightColorScheme(
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

private val GoogleFontsProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private val Inter = GoogleFont("Inter", bestEffort = true)

private val InterFontFamily = FontFamily(
    Font(googleFont = Inter, fontProvider = GoogleFontsProvider, weight = FontWeight.Normal),
    Font(googleFont = Inter, fontProvider = GoogleFontsProvider, weight = FontWeight.Medium),
    Font(googleFont = Inter, fontProvider = GoogleFontsProvider, weight = FontWeight.SemiBold),
    Font(googleFont = Inter, fontProvider = GoogleFontsProvider, weight = FontWeight.Bold),
)

private val ADTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.35).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontSize = 31.sp,
        lineHeight = 37.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.3).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = InterFontFamily,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.25).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontSize = 25.sp,
        lineHeight = 31.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontSize = 20.sp,
        lineHeight = 25.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = (-0.1).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = InterFontFamily,
        fontSize = 18.sp,
        lineHeight = 23.sp,
        fontWeight = FontWeight.Medium,
    ),
    titleLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontSize = 16.5.sp,
        lineHeight = 21.5.sp,
        fontWeight = FontWeight.Medium,
    ),
    titleMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontSize = 14.5.sp,
        lineHeight = 19.5.sp,
        fontWeight = FontWeight.Medium,
    ),
    titleSmall = TextStyle(
        fontFamily = InterFontFamily,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium,
    ),
    bodyLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontSize = 14.5.sp,
        lineHeight = 20.5.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodyMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontSize = 12.25.sp,
        lineHeight = 17.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodySmall = TextStyle(
        fontFamily = InterFontFamily,
        fontSize = 10.75.sp,
        lineHeight = 14.75.sp,
        fontWeight = FontWeight.Normal,
    ),
    labelLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontSize = 12.5.sp,
        lineHeight = 16.5.sp,
        fontWeight = FontWeight.Medium,
    ),
    labelMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontSize = 11.5.sp,
        lineHeight = 15.sp,
        fontWeight = FontWeight.Medium,
    ),
    labelSmall = TextStyle(
        fontFamily = InterFontFamily,
        fontSize = 10.5.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Medium,
    ),
)

private val MonochromeShapes = Shapes(
    extraSmall = RoundedCornerShape(7.dp),
    small = RoundedCornerShape(11.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(23.dp),
    extraLarge = RoundedCornerShape(30.dp),
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
        colorScheme = MonochromeColorScheme,
        motionScheme = MotionScheme.expressive(),
        shapes = MonochromeShapes,
        typography = ADTypography,
        content = content,
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

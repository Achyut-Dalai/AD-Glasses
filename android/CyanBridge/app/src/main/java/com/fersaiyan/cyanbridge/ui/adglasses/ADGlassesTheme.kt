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

/**
 * AD Glasses stays intentionally monochrome. Hierarchy comes from scale, weight, shape,
 * motion and contrast rather than assigning a different accent color to every feature.
 */
internal object ADColors {
    val Ink = Color(0xFF1D1D1F)
    val InkSoft = Color(0xFF343438)
    val Muted = Color(0xFF6E6E73)
    val Blue = Color(0xFF2C2C2E)
    val BlueDeep = Color(0xFF111113)
    val BlueSoft = Color(0xFFEAEAED)
    val Background = Color(0xFFF5F5F7)
    val Canvas = Color(0xFFF8F8FA)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceSubtle = Color(0xFFEDEDEF)
    val SurfacePressed = Color(0xFFE5E5E8)
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
    primary = ADColors.Ink,
    onPrimary = Color.White,
    primaryContainer = ADColors.SurfaceSubtle,
    onPrimaryContainer = ADColors.Ink,
    secondary = ADColors.InkSoft,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF0F0F2),
    onSecondaryContainer = ADColors.Ink,
    tertiary = Color(0xFF55565A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF2F2F4),
    onTertiaryContainer = ADColors.Ink,
    background = ADColors.Background,
    onBackground = ADColors.Ink,
    surface = ADColors.Surface,
    onSurface = ADColors.Ink,
    surfaceVariant = ADColors.SurfaceSubtle,
    onSurfaceVariant = Color(0xFF626368),
    outline = ADColors.Outline,
    outlineVariant = Color(0xFFE2E2E5),
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

/*
 * Ubuntu Sans is the proportional UI family selected from the Nerd Fonts catalogue.
 * JetBrains Mono is reserved for concise technical/status text. Using the upstream Google
 * Fonts builds keeps the APK lean while preserving the type families and their metrics.
 */
private val UbuntuSans = GoogleFont("Ubuntu Sans", bestEffort = true)
private val JetBrainsMono = GoogleFont("JetBrains Mono", bestEffort = true)

internal val ADPrimaryFontFamily = FontFamily(
    Font(googleFont = UbuntuSans, fontProvider = GoogleFontsProvider, weight = FontWeight.Normal),
    Font(googleFont = UbuntuSans, fontProvider = GoogleFontsProvider, weight = FontWeight.Medium),
    Font(googleFont = UbuntuSans, fontProvider = GoogleFontsProvider, weight = FontWeight.SemiBold),
    Font(googleFont = UbuntuSans, fontProvider = GoogleFontsProvider, weight = FontWeight.Bold),
)

internal val ADTechFontFamily = FontFamily(
    Font(googleFont = JetBrainsMono, fontProvider = GoogleFontsProvider, weight = FontWeight.Normal),
    Font(googleFont = JetBrainsMono, fontProvider = GoogleFontsProvider, weight = FontWeight.Medium),
    Font(googleFont = JetBrainsMono, fontProvider = GoogleFontsProvider, weight = FontWeight.SemiBold),
)

private val ADTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = ADPrimaryFontFamily,
        fontSize = 40.sp,
        lineHeight = 44.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.70).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = ADPrimaryFontFamily,
        fontSize = 34.sp,
        lineHeight = 39.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.55).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = ADPrimaryFontFamily,
        fontSize = 30.sp,
        lineHeight = 35.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.42).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = ADPrimaryFontFamily,
        fontSize = 27.sp,
        lineHeight = 33.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.30).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = ADPrimaryFontFamily,
        fontSize = 23.sp,
        lineHeight = 29.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.20).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = ADPrimaryFontFamily,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = (-0.10).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = ADPrimaryFontFamily,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleMedium = TextStyle(
        fontFamily = ADPrimaryFontFamily,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Medium,
    ),
    titleSmall = TextStyle(
        fontFamily = ADPrimaryFontFamily,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
    ),
    bodyLarge = TextStyle(
        fontFamily = ADPrimaryFontFamily,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodyMedium = TextStyle(
        fontFamily = ADPrimaryFontFamily,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodySmall = TextStyle(
        fontFamily = ADPrimaryFontFamily,
        fontSize = 12.5.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Normal,
    ),
    labelLarge = TextStyle(
        fontFamily = ADPrimaryFontFamily,
        fontSize = 14.sp,
        lineHeight = 19.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    labelMedium = TextStyle(
        fontFamily = ADPrimaryFontFamily,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        fontWeight = FontWeight.Medium,
    ),
    labelSmall = TextStyle(
        fontFamily = ADTechFontFamily,
        fontSize = 10.5.sp,
        lineHeight = 15.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.20.sp,
    ),
)

private val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
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
        shapes = ExpressiveShapes,
        typography = ADTypography,
        content = content,
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

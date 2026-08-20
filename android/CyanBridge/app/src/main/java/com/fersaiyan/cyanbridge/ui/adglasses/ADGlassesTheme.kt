package com.fersaiyan.cyanbridge.ui.adglasses

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/**
 * AD Glasses visual system.
 *
 * The product is intentionally dark, grayscale-first and compact. Red is not an accent
 * sprayed across cards; it is a scarce signal for recording, selection and the one action
 * that deserves immediate attention. This borrows the restraint and information density
 * of Nothing OS without copying its proprietary typefaces or assets.
 */
internal object ADColors {
    val Ink = Color(0xFFF3F3F3)
    val InkSoft = Color(0xFFD1D1D1)
    val Muted = Color(0xFF929292)

    val Background = Color(0xFF171717)
    val Canvas = Color(0xFF1B1B1B)
    val Surface = Color(0xFF242424)
    val SurfaceSubtle = Color(0xFF2B2B2B)
    val SurfacePressed = Color(0xFF343434)
    val Glass = Color(0xE61F1F1F)
    val Outline = Color(0xFF3A3A3A)
    val Separator = Color(0xFF303030)

    /** Legacy names kept while the old page implementations are reskinned. */
    val Blue = Color(0xFF343434)
    val BlueDeep = Color(0xFF111111)
    val BlueSoft = Color(0xFF2C2C2C)

    val Red = Color(0xFFD71920)
    val RedSoft = Color(0xFF3A1A1C)
    val Success = Color(0xFF9FCF9D)
    val SuccessSoft = Color(0xFF213021)
    val Warning = Color(0xFFE3B777)
    val WarningSoft = Color(0xFF332719)
    val Error = Color(0xFFFF6B65)
    val ErrorSoft = Color(0xFF3B2020)
}

private val ADColorScheme = darkColorScheme(
    primary = ADColors.Ink,
    onPrimary = Color.Black,
    primaryContainer = ADColors.SurfaceSubtle,
    onPrimaryContainer = ADColors.Ink,
    secondary = ADColors.InkSoft,
    onSecondary = Color.Black,
    secondaryContainer = ADColors.SurfaceSubtle,
    onSecondaryContainer = ADColors.Ink,
    tertiary = ADColors.Red,
    onTertiary = Color.White,
    tertiaryContainer = ADColors.RedSoft,
    onTertiaryContainer = Color.White,
    background = ADColors.Background,
    onBackground = ADColors.Ink,
    surface = ADColors.Surface,
    onSurface = ADColors.Ink,
    surfaceVariant = ADColors.SurfaceSubtle,
    onSurfaceVariant = ADColors.InkSoft,
    outline = ADColors.Outline,
    outlineVariant = ADColors.Separator,
    error = ADColors.Error,
    onError = Color.Black,
    errorContainer = ADColors.ErrorSoft,
    onErrorContainer = ADColors.Error,
)

internal val ADPrimaryFontFamily = FontFamily.SansSerif
internal val ADTechFontFamily = FontFamily.Monospace

private val ADTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = ADPrimaryFontFamily,
        fontSize = 32.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.45).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = ADPrimaryFontFamily,
        fontSize = 28.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.35).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = ADPrimaryFontFamily,
        fontSize = 25.sp,
        lineHeight = 29.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.25).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = ADPrimaryFontFamily,
        fontSize = 22.sp,
        lineHeight = 27.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.20).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = ADPrimaryFontFamily,
        fontSize = 19.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    headlineSmall = TextStyle(
        fontFamily = ADPrimaryFontFamily,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Medium,
    ),
    titleLarge = TextStyle(
        fontFamily = ADPrimaryFontFamily,
        fontSize = 16.sp,
        lineHeight = 21.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleMedium = TextStyle(
        fontFamily = ADPrimaryFontFamily,
        fontSize = 14.sp,
        lineHeight = 19.sp,
        fontWeight = FontWeight.Medium,
    ),
    titleSmall = TextStyle(
        fontFamily = ADPrimaryFontFamily,
        fontSize = 12.5.sp,
        lineHeight = 17.sp,
        fontWeight = FontWeight.Medium,
    ),
    bodyLarge = TextStyle(
        fontFamily = ADPrimaryFontFamily,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodyMedium = TextStyle(
        fontFamily = ADPrimaryFontFamily,
        fontSize = 12.5.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodySmall = TextStyle(
        fontFamily = ADPrimaryFontFamily,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Normal,
    ),
    labelLarge = TextStyle(
        fontFamily = ADPrimaryFontFamily,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    labelMedium = TextStyle(
        fontFamily = ADPrimaryFontFamily,
        fontSize = 10.5.sp,
        lineHeight = 15.sp,
        fontWeight = FontWeight.Medium,
    ),
    labelSmall = TextStyle(
        fontFamily = ADTechFontFamily,
        fontSize = 9.5.sp,
        lineHeight = 13.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.55.sp,
    ),
)

private val ADShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(9.dp),
    medium = RoundedCornerShape(13.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun ADGlassesTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    val context = LocalContext.current

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

    MaterialExpressiveTheme(
        colorScheme = ADColorScheme,
        motionScheme = MotionScheme.expressive(),
        shapes = ADShapes,
        typography = ADTypography,
        content = content,
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

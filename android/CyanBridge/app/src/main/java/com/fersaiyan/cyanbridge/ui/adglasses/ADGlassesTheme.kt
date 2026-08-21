package com.fersaiyan.cyanbridge.ui.adglasses

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
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
 * Wallpaper/canvas can carry a little charcoal texture, but interactive cards should read
 * as true black. Red is a scarce signal, not a large background treatment.
 */
internal object ADColors {
    val Ink = Color(0xFFF4F4F2)
    val InkSoft = Color(0xFFD9D9D5)
    val Muted = Color(0xFFB6B6B0)

    val Background = Color(0xFF171717)
    val Canvas = Color(0xFF141414)
    val Surface = Color(0xFF090909)
    val SurfaceSubtle = Color(0xFF171717)
    val SurfacePressed = Color(0xFF202020)
    val Glass = Color(0xF0080808)
    val Outline = Color(0xFF343434)
    val Separator = Color(0xFF252525)

    /** Legacy names retained for hardware/product screens that still reference them. */
    val Blue = Color(0xFF202020)
    val BlueDeep = Color(0xFF090909)
    val BlueSoft = Color(0xFF181818)

    /** Bright red is for tiny state signals; RedAction is deliberately quieter. */
    val Red = Color(0xFFCA343A)
    val RedAction = Color(0xFF8F252B)
    val RedSoft = Color(0xFF2B1517)
    val RedContent = Color(0xFFEADADA)

    val Success = Color(0xFFA8D2A5)
    val SuccessSoft = Color(0xFF182418)
    val Warning = Color(0xFFE0BA7B)
    val WarningSoft = Color(0xFF2A2116)
    val Error = Color(0xFFE4847F)
    val ErrorSoft = Color(0xFF2E1919)
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
    onTertiary = ADColors.RedContent,
    tertiaryContainer = ADColors.RedSoft,
    onTertiaryContainer = ADColors.RedContent,
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

/** Explicit metadata style. Monospace is opt-in instead of leaking through generic labels. */
internal val ADMetaTextStyle = TextStyle(
    fontFamily = ADTechFontFamily,
    fontSize = 9.5.sp,
    lineHeight = 13.sp,
    fontWeight = FontWeight.Medium,
    letterSpacing = 0.55.sp,
)

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
        fontFamily = ADPrimaryFontFamily,
        fontSize = 9.5.sp,
        lineHeight = 13.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.15.sp,
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
                @Suppress("DEPRECATION")
                run {
                    window.navigationBarColor = android.graphics.Color.BLACK
                    window.statusBarColor = android.graphics.Color.TRANSPARENT
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = false
                }
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

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

private data class ADPalette(
    val ink: Color,
    val muted: Color,
    val background: Color,
    val surface: Color,
    val surfaceSubtle: Color,
    val glass: Color,
    val outline: Color,
    val separator: Color,
    val success: Color,
    val successSoft: Color,
    val warning: Color,
    val warningSoft: Color,
    val error: Color,
    val errorSoft: Color,
    val heroStart: Color,
    val heroMiddle: Color,
    val heroEnd: Color,
    val dark: Boolean,
)

private val LightMonochromePalette = ADPalette(
    ink = Color(0xFF1D1D1F),
    muted = Color(0xFF6E6E73),
    background = Color(0xFFF5F5F7),
    surface = Color(0xFFFFFFFF),
    surfaceSubtle = Color(0xFFEDEDEF),
    glass = Color(0xF7FFFFFF),
    outline = Color(0xFFC6C6C8),
    separator = Color(0x3C3C434A),
    success = Color(0xFF248A3D),
    successSoft = Color(0xFFEAF7ED),
    warning = Color(0xFFC93400),
    warningSoft = Color(0xFFFFF3E8),
    error = Color(0xFFFF3B30),
    errorSoft = Color(0xFFFFEBEA),
    heroStart = Color(0xFFFCFCFD),
    heroMiddle = Color(0xFFF2F3F5),
    heroEnd = Color(0xFFE8EAEE),
    dark = false,
)

private val DarkMonochromePalette = ADPalette(
    ink = Color(0xFFF3F3F5),
    muted = Color(0xFFA9A9AE),
    background = Color(0xFF09090B),
    surface = Color(0xFF151517),
    surfaceSubtle = Color(0xFF232326),
    glass = Color(0xF2161619),
    outline = Color(0xFF3A3A3E),
    separator = Color(0x663A3A3E),
    success = Color(0xFF72D68A),
    successSoft = Color(0xFF15321D),
    warning = Color(0xFFFF9F6E),
    warningSoft = Color(0xFF3B2115),
    error = Color(0xFFFF6961),
    errorSoft = Color(0xFF3C1718),
    heroStart = Color(0xFF202024),
    heroMiddle = Color(0xFF151518),
    heroEnd = Color(0xFF0C0C0F),
    dark = true,
)

/**
 * Shared semantic product colors. AD Glasses owns one activity/theme at a time, so the
 * active palette is selected synchronously by ADGlassesTheme before composing its content.
 */
internal object ADColors {
    private var palette: ADPalette = LightMonochromePalette

    internal fun use(style: ADThemeStyle) {
        palette = if (style == ADThemeStyle.DARK_MONOCHROME) DarkMonochromePalette else LightMonochromePalette
    }

    val Ink get() = palette.ink
    val Muted get() = palette.muted
    val Blue get() = palette.ink
    val BlueDeep get() = if (palette.dark) Color(0xFFF3F3F5) else Color(0xFF111113)
    val BlueSoft get() = palette.surfaceSubtle
    val Background get() = palette.background
    val Surface get() = palette.surface
    val SurfaceSubtle get() = palette.surfaceSubtle
    val Glass get() = palette.glass
    val Outline get() = palette.outline
    val Separator get() = palette.separator
    val Success get() = palette.success
    val SuccessSoft get() = palette.successSoft
    val Warning get() = palette.warning
    val WarningSoft get() = palette.warningSoft
    val Error get() = palette.error
    val ErrorSoft get() = palette.errorSoft
    val HeroStart get() = palette.heroStart
    val HeroMiddle get() = palette.heroMiddle
    val HeroEnd get() = palette.heroEnd
    val IsDark get() = palette.dark
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
    error = Color(0xFFFF3B30),
    onError = Color.White,
    errorContainer = Color(0xFFFFEBEA),
    onErrorContainer = Color(0xFFFF3B30),
)

private val DarkMonochromeColorScheme = darkColorScheme(
    primary = Color(0xFFF3F3F5),
    onPrimary = Color(0xFF111113),
    primaryContainer = Color(0xFF29292D),
    onPrimaryContainer = Color(0xFFF3F3F5),
    secondary = Color(0xFFC7C7CC),
    onSecondary = Color(0xFF18181B),
    secondaryContainer = Color(0xFF242428),
    onSecondaryContainer = Color(0xFFE7E7EA),
    tertiary = Color(0xFFA9A9AE),
    onTertiary = Color(0xFF111113),
    tertiaryContainer = Color(0xFF202024),
    onTertiaryContainer = Color(0xFFE7E7EA),
    background = Color(0xFF09090B),
    onBackground = Color(0xFFF3F3F5),
    surface = Color(0xFF151517),
    onSurface = Color(0xFFF3F3F5),
    surfaceVariant = Color(0xFF232326),
    onSurfaceVariant = Color(0xFFA9A9AE),
    outline = Color(0xFF4A4A4F),
    outlineVariant = Color(0xFF2D2D31),
    error = Color(0xFFFF6961),
    onError = Color(0xFF250404),
    errorContainer = Color(0xFF3C1718),
    onErrorContainer = Color(0xFFFFB4AF),
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

private val MonochromeShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

@Composable
fun ADGlassesTheme(
    style: ADThemeStyle = ADThemeStyle.MONOCHROME,
    content: @Composable () -> Unit,
) {
    ADColors.use(style)
    val view = LocalView.current
    val context = LocalContext.current
    val dark = style == ADThemeStyle.DARK_MONOCHROME

    if (!view.isInEditMode) {
        SideEffect {
            context.findActivity()?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
        }
    }

    MaterialExpressiveTheme(
        colorScheme = if (dark) DarkMonochromeColorScheme else MonochromeColorScheme,
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

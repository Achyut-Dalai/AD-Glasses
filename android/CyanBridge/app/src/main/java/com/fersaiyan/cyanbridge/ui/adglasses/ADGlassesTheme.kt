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

internal object ADColors {
    var Ink = Color(0xFF1D1D1F)
        private set
    var Muted = Color(0xFF6E6E73)
        private set
    var Blue = Color(0xFF5B5FEF)
        private set
    var BlueDeep = Color(0xFF4448C9)
        private set
    var BlueSoft = Color(0xFFEEF0FF)
        private set
    var Background = Color(0xFFF8F8FB)
        private set
    var Surface = Color(0xFFFFFFFF)
        private set
    var SurfaceSubtle = Color(0xFFF0F1F5)
        private set
    var Glass = Color(0xFAFFFFFF)
        private set
    var Outline = Color(0xFFD7D7DF)
        private set
    var Separator = Color(0x1F1D1D1F)
        private set
    var Success = Color(0xFF248A3D)
        private set
    var SuccessSoft = Color(0xFFEAF7ED)
        private set
    var Warning = Color(0xFFB65C00)
        private set
    var WarningSoft = Color(0xFFFFF2E3)
        private set
    var Error = Color(0xFFD92D20)
        private set
    var ErrorSoft = Color(0xFFFFECEA)
        private set

    fun configure(style: ADThemeStyle, darkMode: Boolean) {
        if (darkMode) {
            Ink = Color(0xFFF4F4F7)
            Muted = Color(0xFFA9A9B2)
            Background = Color(0xFF111114)
            Surface = Color(0xFF1A1A1E)
            SurfaceSubtle = Color(0xFF24242A)
            Glass = Color(0xFA1A1A1E)
            Outline = Color(0xFF3B3B44)
            Separator = Color(0x2EFFFFFF)
            Success = Color(0xFF74D78A)
            SuccessSoft = Color(0xFF17351F)
            Warning = Color(0xFFFFB968)
            WarningSoft = Color(0xFF3A2A17)
            Error = Color(0xFFFF8278)
            ErrorSoft = Color(0xFF3B1D1C)

            if (style == ADThemeStyle.MONOCHROME) {
                Blue = Color(0xFF66666F)
                BlueDeep = Color(0xFF505057)
                BlueSoft = Color(0xFF29292F)
            } else {
                Blue = Color(0xFF777BF5)
                BlueDeep = Color(0xFF5C60DC)
                BlueSoft = Color(0xFF292A48)
                SurfaceSubtle = Color(0xFF25243A)
                Outline = Color(0xFF454359)
            }
        } else {
            Ink = Color(0xFF1D1D22)
            Muted = Color(0xFF6B6B75)
            Background = Color(0xFFF8F8FB)
            Surface = Color(0xFFFFFFFF)
            SurfaceSubtle = Color(0xFFF0F1F5)
            Glass = Color(0xFAFFFFFF)
            Outline = Color(0xFFD7D7DF)
            Separator = Color(0x1F1D1D1F)
            Success = Color(0xFF248A3D)
            SuccessSoft = Color(0xFFEAF7ED)
            Warning = Color(0xFFB65C00)
            WarningSoft = Color(0xFFFFF2E3)
            Error = Color(0xFFD92D20)
            ErrorSoft = Color(0xFFFFECEA)

            if (style == ADThemeStyle.MONOCHROME) {
                Blue = Color(0xFF34343A)
                BlueDeep = Color(0xFF1E1E22)
                BlueSoft = Color(0xFFECECF0)
            } else {
                Blue = Color(0xFF5B5FEF)
                BlueDeep = Color(0xFF4448C9)
                BlueSoft = Color(0xFFEEF0FF)
                Background = Color(0xFFFAF9FD)
                SurfaceSubtle = Color(0xFFF2F1FA)
                Outline = Color(0xFFDAD8E7)
            }
        }
    }
}

private val ColorLightScheme = lightColorScheme(
    primary = Color(0xFF5559E6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8E9FF),
    onPrimaryContainer = Color(0xFF24276F),
    secondary = Color(0xFF19786E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDF4EF),
    onSecondaryContainer = Color(0xFF164D47),
    tertiary = Color(0xFF9A6408),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFEDC8),
    onTertiaryContainer = Color(0xFF583900),
    background = Color(0xFFFAF9FD),
    onBackground = Color(0xFF1D1D22),
    surface = Color.White,
    onSurface = Color(0xFF1D1D22),
    surfaceVariant = Color(0xFFF2F1FA),
    onSurfaceVariant = Color(0xFF656570),
    outline = Color(0xFFBFC0CA),
    outlineVariant = Color(0xFFDEDBE8),
    error = Color(0xFFD92D20),
    onError = Color.White,
    errorContainer = Color(0xFFFFECEA),
    onErrorContainer = Color(0xFF8D1C14),
)

private val ColorDarkScheme = darkColorScheme(
    primary = Color(0xFFB8BAFF),
    onPrimary = Color(0xFF24276F),
    primaryContainer = Color(0xFF36396D),
    onPrimaryContainer = Color(0xFFE3E3FF),
    secondary = Color(0xFF8ED8CD),
    onSecondary = Color(0xFF003D36),
    secondaryContainer = Color(0xFF164F48),
    onSecondaryContainer = Color(0xFFB4F3E9),
    tertiary = Color(0xFFFFC56F),
    onTertiary = Color(0xFF523500),
    tertiaryContainer = Color(0xFF6A480E),
    onTertiaryContainer = Color(0xFFFFE2AF),
    background = Color(0xFF111114),
    onBackground = Color(0xFFF4F4F7),
    surface = Color(0xFF1A1A1E),
    onSurface = Color(0xFFF4F4F7),
    surfaceVariant = Color(0xFF25243A),
    onSurfaceVariant = Color(0xFFBCBBC8),
    outline = Color(0xFF888795),
    outlineVariant = Color(0xFF454359),
    error = Color(0xFFFF8278),
    onError = Color(0xFF5D0804),
    errorContainer = Color(0xFF5B211E),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val MonochromeLightScheme = lightColorScheme(
    primary = Color(0xFF29292E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEAEAEE),
    onPrimaryContainer = Color(0xFF1D1D22),
    secondary = Color(0xFF626269),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEFEFF2),
    onSecondaryContainer = Color(0xFF25252A),
    tertiary = Color(0xFF77777E),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF1F1F4),
    onTertiaryContainer = Color(0xFF25252A),
    background = Color(0xFFF8F8FA),
    onBackground = Color(0xFF1D1D22),
    surface = Color.White,
    onSurface = Color(0xFF1D1D22),
    surfaceVariant = Color(0xFFF0F0F3),
    onSurfaceVariant = Color(0xFF67676F),
    outline = Color(0xFFC4C4CB),
    outlineVariant = Color(0xFFDEDEE3),
)

private val MonochromeDarkScheme = darkColorScheme(
    primary = Color(0xFFE0E0E5),
    onPrimary = Color(0xFF2A2A30),
    primaryContainer = Color(0xFF34343A),
    onPrimaryContainer = Color(0xFFF0F0F3),
    secondary = Color(0xFFC4C4CC),
    onSecondary = Color(0xFF303036),
    secondaryContainer = Color(0xFF3A3A41),
    onSecondaryContainer = Color(0xFFE7E7EC),
    tertiary = Color(0xFFB8B8C0),
    onTertiary = Color(0xFF303036),
    tertiaryContainer = Color(0xFF404047),
    onTertiaryContainer = Color(0xFFE7E7EC),
    background = Color(0xFF111114),
    onBackground = Color(0xFFF4F4F7),
    surface = Color(0xFF1A1A1E),
    onSurface = Color(0xFFF4F4F7),
    surfaceVariant = Color(0xFF24242A),
    onSurfaceVariant = Color(0xFFB8B8C1),
    outline = Color(0xFF85858E),
    outlineVariant = Color(0xFF3B3B44),
)

private val ADTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.4).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 27.sp,
        lineHeight = 33.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.3).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = (-0.15).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 19.sp,
        lineHeight = 25.sp,
        fontWeight = FontWeight.Medium,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 18.sp,
        lineHeight = 23.sp,
        fontWeight = FontWeight.Medium,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 15.5.sp,
        lineHeight = 21.sp,
        fontWeight = FontWeight.Medium,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 15.5.sp,
        lineHeight = 23.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 13.5.sp,
        lineHeight = 19.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 12.5.sp,
        lineHeight = 17.sp,
        fontWeight = FontWeight.Normal,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 13.5.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 12.5.sp,
        lineHeight = 17.sp,
        fontWeight = FontWeight.Medium,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
    ),
)

private val ADShapes = Shapes(
    extraSmall = RoundedCornerShape(7.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun ADGlassesTheme(
    style: ADThemeStyle = ADThemeStyle.COLOR,
    darkMode: Boolean = false,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    val context = LocalContext.current
    ADColors.configure(style, darkMode)

    if (!view.isInEditMode) {
        SideEffect {
            context.findActivity()?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkMode
                    isAppearanceLightNavigationBars = !darkMode
                }
            }
        }
    }

    val colors = when {
        style == ADThemeStyle.MONOCHROME && darkMode -> MonochromeDarkScheme
        style == ADThemeStyle.MONOCHROME -> MonochromeLightScheme
        darkMode -> ColorDarkScheme
        else -> ColorLightScheme
    }

    MaterialExpressiveTheme(
        colorScheme = colors,
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

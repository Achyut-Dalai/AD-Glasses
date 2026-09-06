package com.adglasses.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF245FDB),
    onPrimary = Color.White,
    secondary = Color(0xFF4F46E5),
    tertiary = Color(0xFF0F8F84),
    background = Color(0xFFF7F8FB),
    onBackground = Color(0xFF141519),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF141519),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6F7FA),
    surfaceContainer = Color(0xFFEEF0F4),
    surfaceContainerHigh = Color(0xFFE7E9EE),
    surfaceContainerHighest = Color(0xFFDDE0E6),
    onSurfaceVariant = Color(0xFF5F636B),
    outline = Color(0xFFB9BDC6),
    outlineVariant = Color(0xFFD9DCE2),
    error = Color(0xFFD70015),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF78A9FF),
    onPrimary = Color(0xFF071A3D),
    secondary = Color(0xFFA7A2FF),
    tertiary = Color(0xFF66D4C6),
    background = Color(0xFF090A0C),
    onBackground = Color(0xFFF4F5F7),
    surface = Color(0xFF111216),
    onSurface = Color(0xFFF4F5F7),
    surfaceContainerLowest = Color(0xFF090A0C),
    surfaceContainerLow = Color(0xFF121318),
    surfaceContainer = Color(0xFF191B20),
    surfaceContainerHigh = Color(0xFF22242A),
    surfaceContainerHighest = Color(0xFF2B2E35),
    onSurfaceVariant = Color(0xFFB5B8C0),
    outline = Color(0xFF50545D),
    outlineVariant = Color(0xFF30333A),
    error = Color(0xFFFF6B61),
)

private fun adTypography(compactPhone: Boolean): Typography {
    val headlineLarge = if (compactPhone) 27.sp else 29.sp
    val headlineMedium = if (compactPhone) 23.sp else 25.sp
    val headlineSmall = if (compactPhone) 20.sp else 21.sp
    val titleLarge = if (compactPhone) 19.sp else 20.sp
    val titleMedium = if (compactPhone) 16.sp else 17.sp
    val titleSmall = if (compactPhone) 14.sp else 15.sp

    return Typography(
        headlineLarge = TextStyle(
            fontSize = headlineLarge,
            lineHeight = if (compactPhone) 32.sp else 35.sp,
            fontWeight = FontWeight.SemiBold,
        ),
        headlineMedium = TextStyle(
            fontSize = headlineMedium,
            lineHeight = if (compactPhone) 28.sp else 31.sp,
            fontWeight = FontWeight.SemiBold,
        ),
        headlineSmall = TextStyle(
            fontSize = headlineSmall,
            lineHeight = 26.sp,
            fontWeight = FontWeight.SemiBold,
        ),
        titleLarge = TextStyle(
            fontSize = titleLarge,
            lineHeight = 25.sp,
            fontWeight = FontWeight.SemiBold,
        ),
        titleMedium = TextStyle(
            fontSize = titleMedium,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Medium,
        ),
        titleSmall = TextStyle(
            fontSize = titleSmall,
            lineHeight = 19.sp,
            fontWeight = FontWeight.Medium,
        ),
        bodyLarge = TextStyle(
            fontSize = if (compactPhone) 15.sp else 16.sp,
            lineHeight = if (compactPhone) 21.sp else 23.sp,
            fontWeight = FontWeight.Normal,
        ),
        bodyMedium = TextStyle(
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Normal,
        ),
        bodySmall = TextStyle(
            fontSize = 12.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.Normal,
        ),
        labelLarge = TextStyle(
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Medium,
        ),
        labelMedium = TextStyle(
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Medium,
        ),
        labelSmall = TextStyle(
            fontSize = 10.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Medium,
        ),
    )
}

@Composable
fun ADGlassesTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val configuration = LocalConfiguration.current
    val compactPhone = configuration.screenWidthDp < 390
    val scheme = if (dark) DarkColors else LightColors

    MaterialTheme(
        colorScheme = scheme,
        typography = adTypography(compactPhone),
        content = content,
    )
}

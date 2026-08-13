package com.achyut.adglasses.shared.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.achyut.adglasses.shared.appearance.AppearanceSettings

private val VercelDarkColorScheme = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF333333),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF888888),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF333333),
    onSecondaryContainer = Color.White,
    tertiary = Color(0xFFEDEDED),
    onTertiary = Color.Black,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xFF111111),
    onSurface = Color(0xFFEDEDED),
    surfaceVariant = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFFA1A1A1),
    outline = Color(0xFF333333),
    error = Color(0xFFFF4D4D),
)

private val AppleLightColorScheme = lightColorScheme(
    primary = Color.Black,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE5E5E5),
    onPrimaryContainer = Color.Black,
    secondary = Color(0xFF666666),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFFE5E5E5),
    onSecondaryContainer = Color.Black,
    tertiary = Color(0xFF333333),
    onTertiary = Color.White,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color(0xFFF9F9F9),
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFE5E5E5),
    onSurfaceVariant = Color(0xFF666666),
    outline = Color(0xFFD1D1D6),
    error = Color(0xFFFF3B30),
)

private val AdGlassesShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun AdGlassesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) VercelDarkColorScheme else AppleLightColorScheme
    
    MaterialTheme(
        colorScheme = colorScheme,
        shapes = AdGlassesShapes,
        content = content
    )
}

@Composable
fun AdGlassesMaterialTheme(
    settings: AppearanceSettings = AppearanceSettings(),
    content: @Composable () -> Unit,
) {
    val darkTheme = isSystemInDarkTheme()
    val colorScheme = if (darkTheme) VercelDarkColorScheme else AppleLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = AdGlassesShapes,
        typography = Typography(),
        content = content,
    )
}

@Composable
fun CyanBridgeTheme(
    settings: AppearanceSettings = AppearanceSettings(),
    content: @Composable () -> Unit,
) {
    AdGlassesMaterialTheme(settings, content)
}

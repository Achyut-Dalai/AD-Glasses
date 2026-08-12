package com.achyut.adglasses.shared.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val VercelDarkColorScheme = darkColorScheme(
    background = Color(0xFF000000),
    surface = Color(0xFF111111),
    surfaceVariant = Color(0xFF1A1A1A),
    onBackground = Color.White,
    onSurface = Color(0xFFEDEDED),
    primary = Color.White,
    onPrimary = Color.Black,
    outline = Color(0xFF333333)
)

@Composable
fun AdGlassesTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = VercelDarkColorScheme,
        content = content
    )
}

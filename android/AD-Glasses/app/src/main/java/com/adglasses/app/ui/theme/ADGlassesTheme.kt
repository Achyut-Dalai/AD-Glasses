package com.adglasses.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView

private val LightColors = lightColorScheme(
    primary = Color(0xFF4F46E5),
    secondary = Color(0xFF2563EB),
    tertiary = Color(0xFF0891B2),
    surface = Color(0xFFF8F9FC),
    surfaceContainer = Color(0xFFF0F1F6),
    surfaceContainerHigh = Color(0xFFE9EBF2),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB8B4FF),
    secondary = Color(0xFFA9C7FF),
    tertiary = Color(0xFF86D5EA),
    surface = Color(0xFF111318),
    surfaceContainer = Color(0xFF191B21),
    surfaceContainerHigh = Color(0xFF20232A),
)

@Composable
fun ADGlassesTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            (view.context as? Activity)?.window?.let { window ->
                window.statusBarColor = scheme.surface.toArgb()
                window.navigationBarColor = scheme.surface.toArgb()
            }
        }
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

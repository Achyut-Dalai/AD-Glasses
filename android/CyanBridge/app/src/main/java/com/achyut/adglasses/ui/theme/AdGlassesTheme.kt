package com.achyut.adglasses.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.achyut.adglasses.shared.appearance.AppearanceSettings
import com.achyut.adglasses.shared.appearance.ThemeMode
import com.achyut.adglasses.shared.ui.theme.AdGlassesTheme as SharedAdGlassesTheme

@Composable
fun AdGlassesTheme(
    settings: AppearanceSettings = AppearanceSettings(),
    content: @Composable () -> Unit,
) {
    val darkTheme = when (settings.themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        else -> isSystemInDarkTheme()
    }
    
    val view = LocalView.current
    val context = LocalContext.current
    
    SharedAdGlassesTheme(darkTheme = darkTheme) {
        val colorScheme = androidx.compose.material3.MaterialTheme.colorScheme
        
        if (!view.isInEditMode) {
            SideEffect {
                context.findActivity()?.window?.let { window ->
                    window.statusBarColor = colorScheme.background.toArgb()
                    window.navigationBarColor = colorScheme.surface.toArgb()
                    WindowCompat.getInsetsController(window, view).apply {
                        isAppearanceLightStatusBars = !darkTheme
                        isAppearanceLightNavigationBars = !darkTheme
                    }
                }
            }
        }
        
        content()
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

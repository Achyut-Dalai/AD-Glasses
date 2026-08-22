package com.ad_glasses.ui

import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.ad_glasses.ui.appearance.AppearancePreferences
import com.ad_glasses.ui.appearance.rememberAppearanceSettings
import com.ad_glasses.ui.theme.ADGlassesTheme

/** Mount a fully Compose Activity while preserving the user's app appearance settings. */
internal fun AppCompatActivity.setThemedComposeContent(
    content: @Composable () -> Unit,
) {
    val appearancePreferences = AppearancePreferences(this)
    setContent {
        val appearance by rememberAppearanceSettings(appearancePreferences)
        ADGlassesTheme(appearance, content)
    }
}

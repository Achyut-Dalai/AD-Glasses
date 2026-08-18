package com.fersaiyan.cyanbridge.ui

import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.fersaiyan.cyanbridge.ui.appearance.AppearancePreferences
import com.fersaiyan.cyanbridge.ui.appearance.rememberAppearanceSettings
import com.fersaiyan.cyanbridge.ui.theme.CyanBridgeTheme

/** Mount a fully Compose Activity while preserving the user's app appearance settings. */
internal fun AppCompatActivity.setThemedComposeContent(
    content: @Composable () -> Unit,
) {
    val appearancePreferences = AppearancePreferences(this)
    setContent {
        val appearance by rememberAppearanceSettings(appearancePreferences)
        CyanBridgeTheme(appearance, content)
    }
}

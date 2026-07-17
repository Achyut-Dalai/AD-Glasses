package com.fersaiyan.cyanbridge.ui.appearance

import android.os.Build
import com.fersaiyan.cyanbridge.shared.ui.appearance.AppearanceScreen
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import com.fersaiyan.cyanbridge.ui.theme.CyanBridgeTheme

class AppearanceActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preferences = AppearancePreferences(this)
        setContent {
            val settings by rememberAppearanceSettings(preferences)
            CyanBridgeTheme(settings) {
                AppearanceScreen(
                    settings = settings,
                    dynamicColorAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                    onSettingsChange = preferences::save,
                    onReset = preferences::reset,
                    onBack = ::finish,
                )
            }
        }
    }
}

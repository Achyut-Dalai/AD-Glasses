package com.fersaiyan.cyanbridge.shared.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fersaiyan.cyanbridge.shared.appearance.AppearanceSettings
import com.fersaiyan.cyanbridge.shared.navigation.AppDestination
import com.fersaiyan.cyanbridge.shared.ui.appearance.AppearanceScreen

@Composable
fun CyanBridgeApp() {
    var currentDestination by remember { mutableStateOf(AppDestination.CHATS) }
    var showAppearance by remember { mutableStateOf(false) }
    var appearanceSettings by remember { mutableStateOf(AppearanceSettings()) }

    if (showAppearance) {
        AppearanceScreen(
            settings = appearanceSettings,
            dynamicColorAvailable = false,
            onSettingsChange = { appearanceSettings = it },
            onReset = { appearanceSettings = AppearanceSettings() },
            onBack = { showAppearance = false },
        )
    } else {
        CyanBridgeNavShell(
            currentDestination = currentDestination,
            onNavigate = { currentDestination = it },
        ) { destination ->
            when (destination) {
                AppDestination.GLASSES -> PlaceholderScreen("Glasses", "Connect your glasses to see the dashboard.")
                AppDestination.CHATS -> PlaceholderScreen("Chats", "Start a conversation or select an existing chat.")
                AppDestination.MEDIA -> PlaceholderScreen("Media", "Synced photos, videos, and recordings will appear here.")
                AppDestination.PLUGINS -> PlaceholderScreen("Plugins", "Browse and install community plugins.")
                AppDestination.SETTINGS -> PlaceholderScreen("Settings", "Configure your CyanBridge experience.")
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

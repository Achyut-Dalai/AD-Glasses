package com.fersaiyan.cyanbridge.ui.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.fersaiyan.cyanbridge.shared.icons.AppIcon
import com.fersaiyan.cyanbridge.shared.navigation.AppDestination
import com.fersaiyan.cyanbridge.ui.icons.imageVector

@Composable
fun CyanBridgeNavigationBar(
    selectedDestination: AppDestination,
    onDestinationSelected: (AppDestination) -> Unit,
) {
    NavigationBar {
        AppDestination.entries.forEach { destination ->
            NavigationBarItem(
                selected = destination == selectedDestination,
                onClick = { onDestinationSelected(destination) },
                icon = {
                    Icon(
                        imageVector = destination.icon.imageVector(),
                        contentDescription = null,
                    )
                },
                label = { Text(destination.label) },
            )
        }
    }
}

val AppDestination.label: String
    get() = when (this) {
        AppDestination.GLASSES -> "Glasses"
        AppDestination.CHATS -> "Chats"
        AppDestination.MEDIA -> "Media"
        AppDestination.PLUGINS -> "Plugins"
        AppDestination.SETTINGS -> "Settings"
    }

val AppDestination.icon: AppIcon
    get() = when (this) {
        AppDestination.GLASSES -> AppIcon.Glasses
        AppDestination.CHATS -> AppIcon.Chat
        AppDestination.MEDIA -> AppIcon.Recordings
        AppDestination.PLUGINS -> AppIcon.Plugins
        AppDestination.SETTINGS -> AppIcon.Settings
    }

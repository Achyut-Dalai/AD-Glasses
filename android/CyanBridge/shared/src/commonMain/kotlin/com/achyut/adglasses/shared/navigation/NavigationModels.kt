package com.achyut.adglasses.shared.navigation

import com.achyut.adglasses.shared.icons.AppIcon

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

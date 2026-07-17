package com.fersaiyan.cyanbridge.shared.plugins

data class NativePluginCardData(
    val id: String,
    val title: String,
    val description: String,
    val badge: String,
    val enabled: Boolean,
    val hasSettings: Boolean,
)

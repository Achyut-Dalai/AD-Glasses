package com.fersaiyan.cyanbridge.ui.adglasses

/**
 * Native settings destinations exposed by AD Glasses.
 * Kept separate so future settings pages do not accidentally route back into
 * legacy CyanBridge preference screens.
 */
enum class ADNativeSettingsDestination(val title: String) {
    ROUTING("Routing"),
    STORAGE("Storage"),
    LANGUAGE("Language"),
    PERMISSIONS("Permissions"),
    ABOUT("About AD Glasses"),
}

internal fun ADNativeSettingsDestination.subtitle(): String = when (this) {
    ADNativeSettingsDestination.ROUTING -> "Choose how requests are processed"
    ADNativeSettingsDestination.STORAGE -> "Manage local captures and synced media"
    ADNativeSettingsDestination.LANGUAGE -> "App and transcription preferences"
    ADNativeSettingsDestination.PERMISSIONS -> "Camera, microphone and device access"
    ADNativeSettingsDestination.ABOUT -> "Version and product information"
}

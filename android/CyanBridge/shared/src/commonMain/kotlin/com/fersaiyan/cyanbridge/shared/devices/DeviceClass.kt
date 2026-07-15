package com.fersaiyan.cyanbridge.shared.devices

enum class DeviceClass {
    HEY_CYAN,
    META_RAYBAN,
    GENERIC_AUDIO,
    UNKNOWN;

    fun displayName(): String = when (this) {
        HEY_CYAN -> "Camera+Audio glasses"
        META_RAYBAN -> "Meta Rayban"
        GENERIC_AUDIO -> "Audio-only glasses"
        UNKNOWN -> "Unknown"
    }
}

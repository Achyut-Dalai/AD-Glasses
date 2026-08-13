package com.achyut.adglasses.shared.devices

enum class DeviceClass {
    HEY_CYAN,
    META_RAYBAN,
    MEIZU_MYVU,
    EYEVUE,
    GENERIC_AUDIO,
    UNKNOWN;

    fun displayName(): String = when (this) {
        HEY_CYAN -> "HeyCyan"
        META_RAYBAN -> "Meta Rayban"
        MEIZU_MYVU -> "Meizu MYVU / Star Air"
        EYEVUE -> "Eyevue"
        GENERIC_AUDIO -> "Earbuds / Audio-only glasses"
        UNKNOWN -> "Unknown"
    }
}

package com.achyut.adglasses.shared.devices

data class DeviceProfile(
    val macAddress: String,
    val advertisedName: String?,
    val detectedClass: DeviceClass,
    val selectedClass: DeviceClass,
    val userOverridden: Boolean,
)

package com.fersaiyan.cyanbridge.shared.devices

data class ScannedDevice(
    val macAddress: String,
    val advertisedName: String?,
    val rssi: Int,
    val detectedClass: DeviceClass,
    val selectedClass: DeviceClass?,
    val userOverridden: Boolean,
) {
    fun effectiveSelectedClass(): DeviceClass = selectedClass ?: detectedClass
}

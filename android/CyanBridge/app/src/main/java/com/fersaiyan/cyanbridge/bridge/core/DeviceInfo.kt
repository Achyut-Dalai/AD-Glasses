package com.fersaiyan.cyanbridge.bridge.core

/**
 * Information about a discovered or connected glasses device.
 */
data class DeviceInfo(
    val id: String,
    val name: String,
    val address: String,
    val adapterId: String,
    val rssi: Int? = null,
    val firmwareVersion: String? = null,
    val batteryLevel: Int? = null,
)

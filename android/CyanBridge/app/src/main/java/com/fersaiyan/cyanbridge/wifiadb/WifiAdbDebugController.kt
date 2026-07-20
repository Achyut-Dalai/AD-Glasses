package com.fersaiyan.cyanbridge.wifiadb

import android.content.Context
import kotlinx.coroutines.flow.StateFlow

data class WifiAdbDebugRuntimeState(
    val isAvailable: Boolean = false,
    val stateLabel: String = "Idle",
    val detail: String = "",
    val glassesIp: String? = null,
    val relayEndpoints: List<String> = emptyList(),
    val preferredCommand: String = "",
    val canStart: Boolean = true,
    val canStop: Boolean = false,
)

interface WifiAdbDebugController {
    val state: StateFlow<WifiAdbDebugRuntimeState>
    val isActive: Boolean

    fun start(onSessionFinished: () -> Unit)
    fun stop()
    fun onBluetoothDisconnected()
    fun release()
}

interface WifiAdbDebugControllerFactory {
    fun create(context: Context): WifiAdbDebugController
}

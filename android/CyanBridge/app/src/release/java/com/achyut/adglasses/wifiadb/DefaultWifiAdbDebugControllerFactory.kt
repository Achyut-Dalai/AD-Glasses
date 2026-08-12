package com.achyut.adglasses.wifiadb

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object DefaultWifiAdbDebugControllerFactory : WifiAdbDebugControllerFactory {
    override fun create(context: Context): WifiAdbDebugController = InertWifiAdbDebugController
}

private object InertWifiAdbDebugController : WifiAdbDebugController {
    private val unavailable = MutableStateFlow(WifiAdbDebugRuntimeState())

    override val state: StateFlow<WifiAdbDebugRuntimeState> = unavailable
    override val isActive: Boolean = false

    override fun start(onSessionFinished: () -> Unit) = Unit
    override fun stop() = Unit
    override fun onBluetoothDisconnected() = Unit
    override fun release() = Unit
}

package com.myvu.client.service

import android.content.Context

/**
 * Compile-only fallback for checkouts where the MYVU submodule has not been initialized.
 * The real upstream classes replace this source set automatically once the submodule exists.
 */
enum class ConnectionState {
    IDLE,
    CONNECTING,
    BONDING,
    PAIRING,
    SESSION,
    READY,
    FAILED,
}

class ConnectionManager(
    context: Context,
    private val listener: Listener,
) {
    interface Listener {
        fun onStateChanged(connectionState: ConnectionState)
    }

    data class GlassesInfo(
        val name: String? = null,
        val battery: Int? = null,
    )

    @Suppress("UNUSED_VARIABLE")
    private val appContext = context.applicationContext
    private var currentState: ConnectionState = ConnectionState.IDLE

    fun start(macAddress: String) {
        @Suppress("UNUSED_VARIABLE")
        val ignored = macAddress
        currentState = ConnectionState.FAILED
        listener.onStateChanged(currentState)
    }

    fun stop() {
        currentState = ConnectionState.IDLE
        listener.onStateChanged(currentState)
    }

    fun state(): ConnectionState = currentState

    fun glassesInfo(): GlassesInfo? = null

    fun sendTestNotification(title: String, body: String) {
        @Suppress("UNUSED_VARIABLE")
        val ignoredTitle = title
        @Suppress("UNUSED_VARIABLE")
        val ignoredBody = body
    }

    fun syncTime() = Unit

    fun setBrightness(level: Int) {
        @Suppress("UNUSED_VARIABLE")
        val ignored = level
    }

    fun openTeleprompter(text: String, title: String) {
        @Suppress("UNUSED_VARIABLE")
        val ignoredText = text
        @Suppress("UNUSED_VARIABLE")
        val ignoredTitle = title
    }

    fun query(command: String) {
        @Suppress("UNUSED_VARIABLE")
        val ignored = command
    }
}

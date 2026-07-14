package com.fersaiyan.cyanbridge.bridge.core

/**
 * Connection state of the glasses bridge.
 */
sealed class GlassesBridgeState {
    data object Disconnected : GlassesBridgeState()
    data object Scanning : GlassesBridgeState()
    data object Connecting : GlassesBridgeState()
    data object Connected : GlassesBridgeState()
    data class Error(val message: String, val cause: Throwable? = null) : GlassesBridgeState()
}

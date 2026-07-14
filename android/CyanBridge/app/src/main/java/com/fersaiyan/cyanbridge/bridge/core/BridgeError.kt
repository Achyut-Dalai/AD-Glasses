package com.fersaiyan.cyanbridge.bridge.core

/**
 * Typed errors for bridge operations.
 */
sealed class BridgeError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    data class UnsupportedCapability(val capability: GlassesCapability) :
        BridgeError("Capability not supported: $capability")

    data class NotConnected(override val message: String = "Not connected to glasses") :
        BridgeError(message)

    data class ConnectionFailed(override val message: String, override val cause: Throwable? = null) :
        BridgeError(message, cause)

    data class ProtocolError(override val message: String, override val cause: Throwable? = null) :
        BridgeError(message, cause)

    data class Timeout(override val message: String = "Operation timed out") :
        BridgeError(message)
}

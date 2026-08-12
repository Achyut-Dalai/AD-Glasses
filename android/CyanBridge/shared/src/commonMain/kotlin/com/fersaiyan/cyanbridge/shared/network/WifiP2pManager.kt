package com.achyut.adglasses.shared.network

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Cross-platform local Wi-Fi transport abstraction for glasses media transfer.
 * Android uses WifiP2pManager; iOS joins the glasses hotspot with
 * NEHotspotConfiguration. The name is retained for Android API compatibility.
 */
interface WifiP2pManager {
    /** Whether a local Wi-Fi transport is available on this device. */
    val isAvailable: StateFlow<Boolean>

    /**
     * Whether this implementation provides Android-style Wi-Fi Direct peer
     * discovery. iOS hotspot joining deliberately returns false.
     */
    val supportsTrueWifiDirect: Boolean
        get() = false

    /** Current local Wi-Fi connection state. */
    val connectionState: Flow<P2pConnectionState>

    /** The IP address of the connected glasses device, if known. */
    val glassesIpAddress: StateFlow<String?>

    /**
     * Start discovering local Wi-Fi peers where the platform supports it.
     * @return Flow of discovered peers
     */
    fun discoverPeers(): Flow<P2pPeer>

    /** Stop peer discovery. */
    fun stopDiscovery()

    /**
     * Connect to a specific transfer target.
     * @param peerAddress The platform-specific peer or hotspot address
     */
    suspend fun connect(peerAddress: String)

    /** Disconnect from the current P2P connection. */
    suspend fun disconnect()

    /** Check if currently connected to the transfer network. */
    fun isConnected(): Boolean

    /**
     * Set the glasses IP address (from BLE notification).
     * This is used when the glasses report their IP via BLE.
     */
    fun setGlassesIpAddress(ip: String)

    /**
     * Bind the app's network traffic to the P2P network.
     * Important on Samsung devices to ensure HTTP requests route correctly.
     * Returns true if binding succeeded.
     */
    suspend fun bindToP2pNetwork(): Boolean

    /**
     * Cancel any pending P2P connection or group formation.
     */
    fun cancelConnection()
}

/** Local Wi-Fi connection states. */
enum class P2pConnectionState {
    IDLE,
    DISCOVERING,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    ERROR,
}

/**
 * A discovered P2P peer.
 */
data class P2pPeer(
    val address: String,
    val name: String?,
    val isGroupOwner: Boolean,
    val deviceType: String?,
)

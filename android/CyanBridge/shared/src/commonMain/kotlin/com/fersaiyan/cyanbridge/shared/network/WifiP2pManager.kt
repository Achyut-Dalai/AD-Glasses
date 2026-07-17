package com.fersaiyan.cyanbridge.shared.network

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Cross-platform Wi-Fi P2P abstraction for glasses media transfer.
 * Android uses WifiP2pManager; iOS uses NEHotspotConfiguration.
 */
interface WifiP2pManager {
    /** Whether Wi-Fi P2P is available on this device. */
    val isAvailable: StateFlow<Boolean>

    /** Current P2P connection state. */
    val connectionState: Flow<P2pConnectionState>

    /** The IP address of the connected glasses device, if known. */
    val glassesIpAddress: StateFlow<String?>

    /**
     * Start discovering P2P peers.
     * @return Flow of discovered peers
     */
    fun discoverPeers(): Flow<P2pPeer>

    /** Stop peer discovery. */
    fun stopDiscovery()

    /**
     * Connect to a specific peer.
     * @param peerAddress The peer's address (MAC on Android, SSID on iOS)
     */
    suspend fun connect(peerAddress: String)

    /** Disconnect from the current P2P connection. */
    suspend fun disconnect()

    /** Check if currently connected to a P2P group. */
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

/**
 * P2P connection states.
 */
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

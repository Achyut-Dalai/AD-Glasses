package com.fersaiyan.cyanbridge.shared.ble

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Cross-platform BLE abstraction for glasses communication.
 * Android uses the vendor SDK (BleOperateManager, LargeDataHandler).
 * iOS uses CoreBluetooth.
 */
interface BleManager {
    /** Whether Bluetooth is available and enabled on this device. */
    val isBluetoothEnabled: StateFlow<Boolean>

    /** Currently connected device MAC address, or null. */
    val connectedDeviceMac: StateFlow<String?>

    /** Connection state as a flow. */
    val connectionState: Flow<BleConnectionState>

    /**
     * Start scanning for BLE devices.
     * @param timeoutMs Optional scan timeout in milliseconds
     * @return Flow of discovered devices
     */
    fun startScan(timeoutMs: Long? = null): Flow<BleScannedDevice>

    /** Stop an active scan. */
    fun stopScan()

    /**
     * Connect to a device by MAC address (Android) or UUID (iOS).
     * @param identifier MAC address on Android, UUID string on iOS
     */
    suspend fun connect(identifier: String)

    /** Disconnect from the current device. */
    suspend fun disconnect()

    /**
     * Send a command to the glasses via BLE.
     * This maps to LargeDataHandler.glassesControl() on Android.
     * @param command The command bytes to send
     */
    suspend fun sendCommand(command: ByteArray)

    /**
     * Wait until the platform can accept a BLE command on the connected device.
     *
     * Android's vendor adapter can answer immediately once connected. CoreBluetooth
     * may report a connection before service/characteristic discovery has finished.
     */
    suspend fun awaitCommandReady(timeoutMs: Long = 10_000L): Boolean = isConnected()

    /**
     * Register a listener for device notifications.
     * Notifications are BLE characteristic changes from the glasses.
     * @param listener The listener to register
     */
    fun addNotificationListener(listener: BleNotificationListener)

    /**
     * Remove a previously registered notification listener.
     */
    fun removeNotificationListener(listener: BleNotificationListener)

    /**
     * Check if a device is currently connected.
     */
    fun isConnected(): Boolean

    /**
     * Request the device battery level.
     * Returns null if not available.
     */
    suspend fun requestBatteryLevel(): Int?

    /**
     * Request the device firmware version.
     * Returns null if not available.
     */
    suspend fun requestFirmwareVersion(): String?
}

/**
 * Connection state for BLE devices.
 */
enum class BleConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
}

/**
 * A discovered BLE device during scanning.
 */
data class BleScannedDevice(
    val identifier: String,  // MAC on Android, UUID on iOS
    val name: String?,
    val rssi: Int,
    val advertisementData: Map<String, Any> = emptyMap(),
)

/**
 * Listener for BLE notifications from the glasses.
 */
interface BleNotificationListener {
    /**
     * Called when a notification is received from the glasses.
     * @param characteristicId The characteristic UUID or ID
     * @param data The raw notification data
     */
    fun onNotification(characteristicId: String, data: ByteArray)

    /**
     * Called when the device sends a status update.
     * @param statusCode The status code (e.g., battery level, connection state)
     * @param data Additional data associated with the status
     */
    fun onStatusUpdate(statusCode: Int, data: ByteArray) {}
}

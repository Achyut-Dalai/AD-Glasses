package com.fersaiyan.cyanbridge.shared.ble

import com.fersaiyan.cyanbridge.shared.platform.PlatformLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow

/**
 * iOS implementation of [BleManager] using CoreBluetooth.
 * Simplified implementation for MVP - delegates to platform-specific BLE operations.
 */
class IosBleManager : BleManager {

    private val _isBluetoothEnabled = MutableStateFlow(false)
    override val isBluetoothEnabled: StateFlow<Boolean> = _isBluetoothEnabled.asStateFlow()

    private val _connectedDeviceMac = MutableStateFlow<String?>(null)
    override val connectedDeviceMac: StateFlow<String?> = _connectedDeviceMac.asStateFlow()

    private val _connectionState = MutableStateFlow(BleConnectionState.DISCONNECTED)
    override val connectionState: Flow<BleConnectionState> = _connectionState.asStateFlow()

    override fun startScan(timeoutMs: Long?): Flow<BleScannedDevice> = flow {
        PlatformLogger.i(TAG, "Starting BLE scan (iOS CoreBluetooth)")
        // CoreBluetooth scanning will be triggered via the iOS host app
        // and results fed back through onDeviceDiscovered()
    }

    override fun stopScan() {
        PlatformLogger.i(TAG, "Stopping BLE scan")
    }

    override suspend fun connect(identifier: String) {
        PlatformLogger.i(TAG, "Connecting to device: $identifier")
        _connectionState.value = BleConnectionState.CONNECTING
        // Connection handled by iOS host app via CoreBluetooth
        _connectedDeviceMac.value = identifier
        _connectionState.value = BleConnectionState.CONNECTED
    }

    override suspend fun disconnect() {
        PlatformLogger.i(TAG, "Disconnecting from device")
        _connectionState.value = BleConnectionState.DISCONNECTING
        _connectedDeviceMac.value = null
        _connectionState.value = BleConnectionState.DISCONNECTED
    }

    override suspend fun sendCommand(command: ByteArray) {
        PlatformLogger.d(TAG, "Sending command: ${command.size} bytes")
        // Commands sent via iOS host app CoreBluetooth write
    }

    override fun addNotificationListener(listener: BleNotificationListener) {
        synchronized(lock) { listeners.add(listener) }
    }

    override fun removeNotificationListener(listener: BleNotificationListener) {
        synchronized(lock) { listeners.remove(listener) }
    }

    override fun isConnected(): Boolean = _connectionState.value == BleConnectionState.CONNECTED

    override suspend fun requestBatteryLevel(): Int? = null

    override suspend fun requestFirmwareVersion(): String? = null

    /**
     * Called by iOS host when a BLE device is discovered.
     */
    fun onDeviceDiscovered(identifier: String, name: String?, rssi: Int) {
        PlatformLogger.d(TAG, "Device discovered: $name ($identifier) RSSI=$rssi")
    }

    /**
     * Called by iOS host when a notification is received.
     */
    fun onNotificationReceived(characteristicId: String, data: ByteArray) {
        synchronized(lock) {
            listeners.forEach { listener ->
                try {
                    listener.onNotification(characteristicId, data)
                } catch (e: Exception) {
                    PlatformLogger.e(TAG, "Error in notification listener", e)
                }
            }
        }
    }

    /**
     * Called by iOS host when Bluetooth state changes.
     */
    fun onBluetoothStateChanged(enabled: Boolean) {
        _isBluetoothEnabled.value = enabled
        if (!enabled) {
            _connectedDeviceMac.value = null
            _connectionState.value = BleConnectionState.DISCONNECTED
        }
    }

    companion object {
        private const val TAG = "IosBleManager"
        private val lock = Any()
        private val listeners = mutableListOf<BleNotificationListener>()
    }
}

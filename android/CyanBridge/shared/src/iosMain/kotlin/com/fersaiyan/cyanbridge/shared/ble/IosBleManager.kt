package com.fersaiyan.cyanbridge.shared.ble

import com.fersaiyan.cyanbridge.shared.platform.PlatformLogger
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBCentralManagerDelegateProtocol
import platform.CoreBluetooth.CBCharacteristic
import platform.CoreBluetooth.CBCharacteristicWriteType
import platform.CoreBluetooth.CBPeripheral
import platform.CoreBluetooth.CBPeripheralDelegateProtocol
import platform.CoreBluetooth.CBManagerState
import platform.CoreBluetooth.CBPeripheralState
import platform.CoreBluetooth.CBUUID
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSLog
import platform.darwin.NSObject

/**
 * iOS implementation of [BleManager] using CoreBluetooth.
 */
@OptIn(ExperimentalForeignApi::class)
class IosBleManager : BleManager {

    private val _isBluetoothEnabled = MutableStateFlow(false)
    override val isBluetoothEnabled: StateFlow<Boolean> = _isBluetoothEnabled.asStateFlow()

    private val _connectedDeviceMac = MutableStateFlow<String?>(null)
    override val connectedDeviceMac: StateFlow<String?> = _connectedDeviceMac.asStateFlow()

    private val _connectionState = MutableStateFlow(BleConnectionState.DISCONNECTED)
    override val connectionState: Flow<BleConnectionState> = _connectionState.asStateFlow()

    private val listeners = mutableListOf<BleNotificationListener>()
    private val listenersLock = Any()

    private var centralManager: CBCentralManager? = null
    private var connectedPeripheral: CBPeripheral? = null
    private val discoveredPeripherals = mutableMapOf<String, CBPeripheral>()

    private val GLASSES_SERVICE_UUID = CBUUID.UUIDWithString("0000fff0-0000-1000-8000-00805f9b34fb")
    private val GLASSES_NOTIFY_CHAR_UUID = CBUUID.UUIDWithString("0000fff1-0000-1000-8000-00805f9b34fb")
    private val GLASSES_WRITE_CHAR_UUID = CBUUID.UUIDWithString("0000fff2-0000-1000-8000-00805f9b34fb")

    init {
        centralManager = CBCentralManager(delegate = CentralManagerDelegate(), queue = null)
    }

    override fun startScan(timeoutMs: Long?): Flow<BleScannedDevice> = flow {
        val manager = centralManager ?: return@flow
        if (manager.state != CBManagerState.CBManagerStatePoweredOn) {
            PlatformLogger.w(TAG, "Bluetooth not powered on, cannot scan")
            return@flow
        }
        PlatformLogger.i(TAG, "Starting BLE scan")
        manager.scanForPeripheralsWithServices(
            arrayOf(GLASSES_SERVICE_UUID),
            options = null,
        )
    }

    override fun stopScan() {
        PlatformLogger.i(TAG, "Stopping BLE scan")
        centralManager?.stopScan()
    }

    override suspend fun connect(identifier: String) {
        val peripheral = discoveredPeripherals[identifier]
        if (peripheral == null) {
            PlatformLogger.e(TAG, "Device not found: $identifier")
            return
        }
        PlatformLogger.i(TAG, "Connecting to device: $identifier")
        _connectionState.value = BleConnectionState.CONNECTING
        centralManager?.connectPeripheral(peripheral, options = null)
    }

    override suspend fun disconnect() {
        val peripheral = connectedPeripheral ?: return
        PlatformLogger.i(TAG, "Disconnecting from device")
        _connectionState.value = BleConnectionState.DISCONNECTING
        centralManager?.cancelPeripheralConnection(peripheral)
    }

    override suspend fun sendCommand(command: ByteArray) {
        val peripheral = connectedPeripheral ?: run {
            PlatformLogger.e(TAG, "No connected device to send command")
            return
        }
        val writeChar = findCharacteristic(GLASSES_WRITE_CHAR_UUID)
        if (writeChar == null) {
            PlatformLogger.e(TAG, "Write characteristic not found")
            return
        }
        val data = command.toNSData()
        peripheral.writeValue(data, writeChar, CBCharacteristicWriteType.CBCharacteristicWriteWithResponse)
        PlatformLogger.d(TAG, "Sent command: ${command.size} bytes")
    }

    override fun addNotificationListener(listener: BleNotificationListener) {
        synchronized(listenersLock) { listeners.add(listener) }
    }

    override fun removeNotificationListener(listener: BleNotificationListener) {
        synchronized(listenersLock) { listeners.remove(listener) }
    }

    override fun isConnected(): Boolean = _connectionState.value == BleConnectionState.CONNECTED

    override suspend fun requestBatteryLevel(): Int? = null

    override suspend fun requestFirmwareVersion(): String? = null

    private fun findCharacteristic(uuid: CBUUID): CBCharacteristic? {
        return connectedPeripheral?.services
            ?.filterIsInstance<CBCharacteristic>()
            ?.find { it.UUID == uuid }
    }

    private fun notifyListeners(characteristicId: String, data: ByteArray) {
        synchronized(listenersLock) {
            listeners.forEach { listener ->
                try {
                    listener.onNotification(characteristicId, data)
                } catch (e: Exception) {
                    PlatformLogger.e(TAG, "Error in notification listener", e)
                }
            }
        }
    }

    private fun ByteArray.toNSData(): NSData {
        if (isEmpty()) return NSData()
        return this.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
        }
    }

    private inner class CentralManagerDelegate : NSObject(), CBCentralManagerDelegateProtocol {
        override fun centralManagerDidUpdateState(central: CBCentralManager) {
            val poweredOn = central.state == CBManagerState.CBManagerStatePoweredOn
            _isBluetoothEnabled.value = poweredOn
            PlatformLogger.i(TAG, "Central manager state: ${central.state} (poweredOn=$poweredOn)")
        }

        override fun centralManager(
            central: CBCentralManager,
            didDiscoverPeripheral: CBPeripheral,
            advertisementData: Map<Any?, *>,
            RSSI: platform.Foundation.NSNumber,
        ) {
            val identifier = didDiscoverPeripheral.identifier.UUIDString
            val name = didDiscoverPeripheral.name
            val rssi = RSSI.intValue
            discoveredPeripherals[identifier] = didDiscoverPeripheral
            PlatformLogger.d(TAG, "Discovered device: $name ($identifier) RSSI=$rssi")
        }

        override fun centralManager(
            central: CBCentralManager,
            didConnectPeripheral: CBPeripheral,
        ) {
            val identifier = didConnectPeripheral.identifier.UUIDString
            PlatformLogger.i(TAG, "Connected to device: $identifier")
            _connectedDeviceMac.value = identifier
            _connectionState.value = BleConnectionState.CONNECTED
            connectedPeripheral = didConnectPeripheral
            didConnectPeripheral.delegate = PeripheralDelegate()
            didConnectPeripheral.discoverServices(arrayOf(GLASSES_SERVICE_UUID))
        }

        @ObjCSignatureOverride
        override fun centralManager(
            central: CBCentralManager,
            didDisconnectPeripheral: CBPeripheral,
            error: NSError?,
        ) {
            val identifier = didDisconnectPeripheral.identifier.UUIDString
            PlatformLogger.i(TAG, "Disconnected from device: $identifier (error=${error?.localizedDescription})")
            _connectedDeviceMac.value = null
            _connectionState.value = BleConnectionState.DISCONNECTED
            connectedPeripheral = null
        }

        @ObjCSignatureOverride
        override fun centralManager(
            central: CBCentralManager,
            didFailToConnectPeripheral: CBPeripheral,
            error: NSError?,
        ) {
            PlatformLogger.e(TAG, "Failed to connect: ${error?.localizedDescription}")
            _connectionState.value = BleConnectionState.DISCONNECTED
        }
    }

    private inner class PeripheralDelegate : NSObject(), CBPeripheralDelegateProtocol {
        override fun peripheral(peripheral: CBPeripheral, didDiscoverServices: NSError?) {
            if (didDiscoverServices != null) {
                PlatformLogger.e(TAG, "Service discovery error: ${didDiscoverServices.localizedDescription}")
                return
            }
            peripheral.services?.filterIsInstance<CBCharacteristic>()?.forEach { char ->
                PlatformLogger.d(TAG, "Discovered characteristic: ${char.UUID}")
                if (char.UUID == GLASSES_NOTIFY_CHAR_UUID) {
                    peripheral.setNotifyValue(true, char)
                }
            }
        }

        @ObjCSignatureOverride
        override fun peripheral(
            peripheral: CBPeripheral,
            didUpdateValueForCharacteristic: CBCharacteristic,
            error: NSError?,
        ) {
            if (error != null) {
                PlatformLogger.e(TAG, "Characteristic update error: ${error.localizedDescription}")
                return
            }
            val data = didUpdateValueForCharacteristic.value ?: return
            val bytes = data.toByteArray() ?: return
            notifyListeners(didUpdateValueForCharacteristic.UUID.UUIDString, bytes)
        }
    }

    companion object {
        private const val TAG = "IosBleManager"
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray? {
    if (length == 0uL) return ByteArray(0)
    val bytes = ByteArray(length.toInt())
    bytes.usePinned { pinned ->
        platform.posix.memcpy(pinned.addressOf(0), this.bytes, this.length)
    }
    return bytes
}

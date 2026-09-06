package com.adglasses.app.integrations.heycyan

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.adglasses.app.BuildConfig
import com.adglasses.app.core.model.ScannedGlasses
import java.util.ArrayDeque
import java.util.UUID

class HeyCyanBleTransport(private val context: Context) {
    companion object {
        val PRIMARY_SERVICE: UUID = UUID.fromString("6e40fff0-b5a3-f393-e0a9-e50e24dcca9e")
        val PRIMARY_WRITE: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
        val PRIMARY_NOTIFY: UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")
        val LARGE_SERVICE: UUID = UUID.fromString("de5bf728-d711-4e47-af26-65e3012a5dc7")
        val LARGE_NOTIFY: UUID = UUID.fromString("de5bf729-d711-4e47-af26-65e3012a5dc7")
        val LARGE_WRITE: UUID = UUID.fromString("de5bf72a-d711-4e47-af26-65e3012a5dc7")
        private val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private const val TAG = "AD/BLE"
    }

    enum class NotificationChannel {
        Primary,
        LargeData,
    }

    sealed interface Event {
        data object Scanning : Event
        data class ScanResultFound(val device: ScannedGlasses) : Event
        data object Connecting : Event
        data object Discovering : Event
        data object Ready : Event
        data class Bytes(val channel: NotificationChannel, val bytes: ByteArray) : Event
        data class Disconnected(val status: Int) : Event
        data class Error(val message: String) : Event
    }

    var onEvent: ((Event) -> Unit)? = null

    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
    private val adapter: BluetoothAdapter? get() = bluetoothManager.adapter
    private var gatt: BluetoothGatt? = null
    private var largeWrite: BluetoothGattCharacteristic? = null
    private var primaryWrite: BluetoothGattCharacteristic? = null
    private val notifyQueue = ArrayDeque<BluetoothGattCharacteristic>()
    private var scanning = false

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val recordName = result.scanRecord?.deviceName
            val systemName = runCatching {
                if (hasConnectPermission()) result.device.name else null
            }.getOrNull()
            val advertisedServices = result.scanRecord?.serviceUuids?.map { it.uuid }.orEmpty()

            // Mirror the working iOS admission boundary. The physical AM01 family is observed as
            // JS-01, while HeyCyan/Oudmon prefixes remain supported product identifiers. A name or
            // advertised-service match only makes the device selectable: post-connect discovery of
            // both verified GATT service families is still the authoritative safety check.
            val names = listOfNotNull(recordName, systemName).map { it.trim() }
            val supportedName = names.any { raw ->
                val name = raw.lowercase()
                name.startsWith("js-01") ||
                    name.startsWith("js01") ||
                    name.contains("heycyan") ||
                    name.contains("hey cyan") ||
                    name.startsWith("o_") ||
                    name.startsWith("q_")
            }
            val supportedService = advertisedServices.any { it == PRIMARY_SERVICE }
            if (!supportedName && !supportedService) return

            val name = recordName ?: systemName ?: "HeyCyan glasses"
            debug("candidate name=$name rssi=${result.rssi} services=${advertisedServices.joinToString()}")
            onEvent?.invoke(
                Event.ScanResultFound(
                    ScannedGlasses(name, result.device.address, result.rssi),
                ),
            )
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            debug("scan failed code=$errorCode")
            onEvent?.invoke(Event.Error("Bluetooth scan failed ($errorCode)"))
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            debug("connection state status=$status newState=$newState")
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    onEvent?.invoke(Event.Discovering)
                    if (!gatt.discoverServices()) {
                        onEvent?.invoke(Event.Error("Could not discover glasses services"))
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    clearGatt(gatt)
                    onEvent?.invoke(Event.Disconnected(status))
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val serviceList = gatt.services.joinToString { it.uuid.toString() }
            debug("services discovered status=$status services=$serviceList")
            if (status != BluetoothGatt.GATT_SUCCESS) {
                onEvent?.invoke(Event.Error("Service discovery failed ($status)"))
                return
            }
            val primary = gatt.getService(PRIMARY_SERVICE)
            val large = gatt.getService(LARGE_SERVICE)
            if (primary == null || large == null) {
                onEvent?.invoke(Event.Error("The connected device does not expose both verified HeyCyan services"))
                return
            }
            primaryWrite = primary.getCharacteristic(PRIMARY_WRITE)
            largeWrite = large.getCharacteristic(LARGE_WRITE)
            val primaryNotify = primary.getCharacteristic(PRIMARY_NOTIFY)
            val largeNotify = large.getCharacteristic(LARGE_NOTIFY)
            if (primaryWrite == null || largeWrite == null || primaryNotify == null || largeNotify == null) {
                onEvent?.invoke(Event.Error("The connected device is missing a required HeyCyan characteristic"))
                return
            }
            notifyQueue.clear()
            notifyQueue += primaryNotify
            notifyQueue += largeNotify
            enableNextNotification(gatt)
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            debug("descriptor write characteristic=${descriptor.characteristic.uuid} status=$status")
            if (status != BluetoothGatt.GATT_SUCCESS) {
                onEvent?.invoke(Event.Error("Could not enable glasses notifications ($status)"))
                return
            }
            enableNextNotification(gatt)
        }

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            emitNotification(characteristic, characteristic.value?.copyOf() ?: byteArrayOf())
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            emitNotification(characteristic, value.copyOf())
        }
    }

    fun startScan() {
        if (!hasScanPermission()) {
            onEvent?.invoke(Event.Error("Bluetooth scan permission is required"))
            return
        }
        if (!hasConnectPermission()) {
            onEvent?.invoke(Event.Error("Bluetooth connect permission is required"))
            return
        }

        val currentAdapter = adapter ?: run {
            scanning = false
            onEvent?.invoke(Event.Error("Bluetooth is unavailable on this phone"))
            return
        }
        if (!currentAdapter.isEnabled) {
            scanning = false
            onEvent?.invoke(Event.Error("Turn on Bluetooth before scanning for glasses"))
            return
        }

        val scanner = currentAdapter.bluetoothLeScanner ?: run {
            scanning = false
            onEvent?.invoke(Event.Error("Bluetooth scanner is unavailable"))
            return
        }

        if (scanning) {
            runCatching { scanner.stopScan(scanCallback) }
            scanning = false
        }

        scanning = true
        debug("starting unfiltered BLE scan; supported candidates are admitted in callback")
        onEvent?.invoke(Event.Scanning)
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()

        // Verified GATT UUIDs are post-connect evidence, not a reliable advertisement-time filter.
        runCatching {
            scanner.startScan(null, settings, scanCallback)
        }.onFailure { error ->
            scanning = false
            onEvent?.invoke(Event.Error(error.message ?: "Could not start Bluetooth scan"))
        }
    }

    fun stopScan() {
        if (!scanning || !hasScanPermission()) return
        runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
        scanning = false
        debug("scan stopped")
    }

    fun connect(address: String) {
        if (!hasConnectPermission()) {
            onEvent?.invoke(Event.Error("Bluetooth connect permission is required"))
            return
        }
        val currentAdapter = adapter
        if (currentAdapter == null || !currentAdapter.isEnabled) {
            onEvent?.invoke(Event.Error("Turn on Bluetooth before connecting to glasses"))
            return
        }
        stopScan()
        close()
        val device = runCatching { currentAdapter.getRemoteDevice(address) }.getOrNull() ?: run {
            onEvent?.invoke(Event.Error("The saved glasses address is invalid"))
            return
        }
        debug("connectGatt name=${runCatching { device.name }.getOrNull() ?: "unknown"}")
        onEvent?.invoke(Event.Connecting)
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    fun writeLargeData(bytes: ByteArray): Boolean = write(largeWrite, bytes, "large")
    fun writePrimary(bytes: ByteArray): Boolean = write(primaryWrite, bytes, "primary")

    fun close() {
        stopScan()
        gatt?.let { current ->
            runCatching { current.disconnect() }
            runCatching { current.close() }
        }
        gatt = null
        primaryWrite = null
        largeWrite = null
        notifyQueue.clear()
    }

    @SuppressLint("MissingPermission")
    private fun enableNextNotification(gatt: BluetoothGatt) {
        val characteristic = notifyQueue.removeFirstOrNullCompat()
        if (characteristic == null) {
            debug("both verified notification paths are enabled")
            onEvent?.invoke(Event.Ready)
            return
        }
        debug("enabling notify ${characteristic.uuid}")
        if (!gatt.setCharacteristicNotification(characteristic, true)) {
            onEvent?.invoke(Event.Error("Could not subscribe to a glasses notification characteristic"))
            return
        }
        val descriptor = characteristic.getDescriptor(CCCD) ?: run {
            onEvent?.invoke(Event.Error("A required glasses notification descriptor is missing"))
            return
        }
        if (Build.VERSION.SDK_INT >= 33) {
            val result = gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            if (result != android.bluetooth.BluetoothStatusCodes.SUCCESS) {
                onEvent?.invoke(Event.Error("Could not write a glasses notification descriptor ($result)"))
            }
        } else {
            @Suppress("DEPRECATION")
            run {
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                if (!gatt.writeDescriptor(descriptor)) {
                    onEvent?.invoke(Event.Error("Could not write a glasses notification descriptor"))
                }
            }
        }
    }

    private fun emitNotification(characteristic: BluetoothGattCharacteristic, bytes: ByteArray) {
        val channel = when (characteristic.uuid) {
            PRIMARY_NOTIFY -> NotificationChannel.Primary
            LARGE_NOTIFY -> NotificationChannel.LargeData
            else -> return
        }
        debug("rx channel=$channel bytes=${bytes.size}")
        onEvent?.invoke(Event.Bytes(channel, bytes))
    }

    @SuppressLint("MissingPermission")
    private fun write(
        characteristic: BluetoothGattCharacteristic?,
        bytes: ByteArray,
        channel: String,
    ): Boolean {
        val currentGatt = gatt ?: return false
        val target = characteristic ?: return false
        debug("tx channel=$channel family=${bytes.getOrNull(1)?.toUByte()?.toString(16) ?: "??"} bytes=${bytes.size}")
        return if (Build.VERSION.SDK_INT >= 33) {
            currentGatt.writeCharacteristic(
                target,
                bytes,
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE,
            ) == android.bluetooth.BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            run {
                target.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                target.value = bytes
                currentGatt.writeCharacteristic(target)
            }
        }
    }

    private fun hasScanPermission(): Boolean = Build.VERSION.SDK_INT < 31 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED

    private fun hasConnectPermission(): Boolean = Build.VERSION.SDK_INT < 31 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun clearGatt(disconnectedGatt: BluetoothGatt) {
        if (gatt === disconnectedGatt) {
            runCatching { disconnectedGatt.close() }
            gatt = null
            primaryWrite = null
            largeWrite = null
            notifyQueue.clear()
        }
    }

    private fun debug(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }
}

private fun <T> ArrayDeque<T>.removeFirstOrNullCompat(): T? = if (isEmpty()) null else removeFirst()

package com.fersaiyan.cyanbridge.ui.reactnative

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.fersaiyan.cyanbridge.devices.ADDeviceSupportPolicy
import com.fersaiyan.cyanbridge.devices.DeviceClassifier
import com.fersaiyan.cyanbridge.devices.DeviceProfileStore
import com.fersaiyan.cyanbridge.devices.ScannedDevice
import com.fersaiyan.cyanbridge.shared.devices.DeviceClass
import com.fersaiyan.cyanbridge.shared.devices.DeviceProfile
import com.fersaiyan.cyanbridge.ui.AutoPairManager
import com.fersaiyan.cyanbridge.ui.BluetoothUtils
import com.fersaiyan.cyanbridge.ui.hasBluetooth
import com.fersaiyan.cyanbridge.ui.requestBluetoothPermission
import com.hjq.permissions.OnPermissionCallback
import com.oudmon.ble.base.bluetooth.BleOperateManager
import com.oudmon.ble.base.scan.BleScannerHelper
import com.oudmon.ble.base.scan.ScanRecord
import com.oudmon.ble.base.scan.ScanWrapperCallback

/**
 * Owns presentation-neutral discovery state for the React Native pairing screen.
 * Device classification, support policy, profile persistence and transport remain native.
 */
class ADPairingModule(
    private val reactContext: ReactApplicationContext,
) : ReactContextBaseJavaModule(reactContext) {

    private val handler = Handler(Looper.getMainLooper())
    private val devices = mutableListOf<ScannedDevice>()
    private var scanning = false
    private var listenerCount = 0
    private val timeout = Runnable { stopScanInternal() }

    override fun getName(): String = "ADPairing"

    private val scanCallback = object : ScanWrapperCallback {
        override fun onStart() {
            scanning = true
            emitState()
        }

        override fun onStop() {
            scanning = false
            emitState()
        }

        override fun onLeScan(device: BluetoothDevice?, rssi: Int, scanRecord: ByteArray?) {
            if (!hasBluetooth(reactContext)) return
            val item = device ?: return
            upsert(item.address, runCatching { item.name }.getOrNull(), rssi)
        }

        override fun onParsedData(device: BluetoothDevice?, scanRecord: ScanRecord?) {
            if (!hasBluetooth(reactContext)) return
            val item = device ?: return
            val name = runCatching { scanRecord?.deviceName ?: item.name }.getOrNull()
            val rssi = devices.firstOrNull { it.macAddress.equals(item.address, true) }?.rssi ?: 0
            upsert(item.address, name, rssi, scanRecord?.serviceUuids.orEmpty())
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            emitState(error = "Bluetooth scan failed ($errorCode)")
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) = Unit
    }

    @ReactMethod
    fun addListener(eventName: String) {
        listenerCount += 1
    }

    @ReactMethod
    fun removeListeners(count: Double) {
        listenerCount = (listenerCount - count.toInt()).coerceAtLeast(0)
    }

    @ReactMethod
    fun getState(promise: Promise) {
        promise.resolve(stateMap())
    }

    @ReactMethod
    fun startScan(promise: Promise) {
        val activity = currentActivity
        if (!hasBluetooth(reactContext)) {
            if (activity == null) {
                promise.reject("E_PAIR_PERMISSION", "Bluetooth permission is required")
                return
            }
            requestBluetoothPermission(
                activity,
                object : OnPermissionCallback {
                    override fun onGranted(permissions: MutableList<String>, all: Boolean) {
                        if (all) startScanAfterPermission(promise)
                        else promise.reject("E_PAIR_PERMISSION", "Bluetooth permission is required")
                    }

                    override fun onDenied(permissions: MutableList<String>, never: Boolean) {
                        promise.reject("E_PAIR_PERMISSION", "Bluetooth permission is required")
                    }
                },
            )
            return
        }
        startScanAfterPermission(promise)
    }

    @ReactMethod
    fun stopScan() {
        stopScanInternal()
    }

    @ReactMethod
    fun connect(macAddress: String, promise: Promise) {
        val device = devices.firstOrNull { it.macAddress.equals(macAddress, ignoreCase = true) }
        if (device == null) {
            promise.reject("E_PAIR_DEVICE", "That glasses device is no longer available")
            return
        }
        val deviceClass = device.detectedClass
        if (!ADDeviceSupportPolicy.isPairable(deviceClass)) {
            promise.reject("E_PAIR_UNSUPPORTED", "These glasses are not enabled in AD Glasses yet")
            return
        }
        if (!hasBluetooth(reactContext)) {
            promise.reject("E_PAIR_PERMISSION", "Bluetooth permission is required")
            return
        }

        stopScanInternal()
        AutoPairManager.setAutoReconnectSuppressed(false, reason = "user_manual_pair")
        device.userSelectedClass = null
        DeviceProfileStore.saveLastSelected(
            reactContext,
            DeviceProfile(
                macAddress = device.macAddress,
                advertisedName = device.advertisedName,
                detectedClass = deviceClass,
                selectedClass = deviceClass,
                userOverridden = false,
            ),
        )
        BleOperateManager.getInstance().connectDirectly(device.macAddress)
        promise.resolve(true)
    }

    override fun invalidate() {
        stopScanInternal()
        super.invalidate()
    }

    private fun startScanAfterPermission(promise: Promise) {
        val activity = currentActivity
        if (!BluetoothUtils.isEnabledBluetooth(reactContext)) {
            if (activity != null) {
                activity.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            }
            scanning = false
            emitState(error = "Turn on Bluetooth, then scan again")
            promise.reject("E_PAIR_BLUETOOTH_OFF", "Bluetooth is turned off")
            return
        }

        handler.removeCallbacks(timeout)
        devices.clear()
        BleScannerHelper.getInstance().reSetCallback()
        scanning = true
        emitState()
        BleScannerHelper.getInstance().scanDevice(reactContext, null, scanCallback)
        handler.postDelayed(timeout, SCAN_TIMEOUT_MS)
        promise.resolve(stateMap())
    }

    private fun stopScanInternal() {
        handler.removeCallbacks(timeout)
        runCatching { BleScannerHelper.getInstance().stopScan(reactContext) }
        scanning = false
        emitState()
    }

    private fun upsert(
        mac: String,
        name: String?,
        rssi: Int,
        serviceUuids: List<ParcelUuid> = emptyList(),
    ) {
        val cleanName = name?.trim()?.takeIf { it.isNotEmpty() }
        val existing = devices.firstOrNull { it.macAddress.equals(mac, ignoreCase = true) }
        if (existing != null) {
            existing.rssi = rssi
            if (existing.advertisedName.isNullOrBlank() && cleanName != null) existing.advertisedName = cleanName
            if (serviceUuids.isNotEmpty()) existing.serviceUuids = serviceUuids
            existing.setDetectedClass(DeviceClassifier.guessDeviceClass(existing.advertisedName, existing.serviceUuids))
            emitState()
            return
        }

        val deviceClass = DeviceClassifier.guessDeviceClass(cleanName, serviceUuids)
        if (cleanName == null && deviceClass == DeviceClass.UNKNOWN) return
        devices += ScannedDevice(
            macAddress = mac,
            advertisedName = cleanName ?: deviceClass.displayName(),
            rssi = rssi,
            serviceUuids = serviceUuids,
        )
        emitState()
    }

    private fun stateMap(error: String? = null) = Arguments.createMap().apply {
        putBoolean("scanning", scanning)
        putBoolean("permissionGranted", hasBluetooth(reactContext))
        putBoolean("bluetoothEnabled", BluetoothUtils.isEnabledBluetooth(reactContext))
        if (error != null) putString("error", error)
        putArray("devices", Arguments.createArray().apply {
            devices
                .filter { ADDeviceSupportPolicy.shouldShowScanResult(it.detectedClass) }
                .sortedByDescending { it.rssi }
                .forEach { item ->
                    pushMap(Arguments.createMap().apply {
                        putString("macAddress", item.macAddress)
                        putString("name", item.advertisedName ?: item.detectedClass.displayName())
                        putInt("rssi", item.rssi)
                        putString("deviceClass", item.detectedClass.name)
                        putString("deviceClassLabel", item.detectedClass.displayName())
                        putBoolean("pairable", ADDeviceSupportPolicy.isPairable(item.detectedClass))
                    })
                }
        })
    }

    private fun emitState(error: String? = null) {
        if (listenerCount <= 0 || !reactContext.hasActiveReactInstance()) return
        reactContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            .emit(EVENT_STATE, stateMap(error))
    }

    private companion object {
        const val EVENT_STATE = "adPairingState"
        const val SCAN_TIMEOUT_MS = 15_000L
    }
}

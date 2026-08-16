package com.fersaiyan.cyanbridge.ui

import com.fersaiyan.cyanbridge.shared.devices.DeviceProfile
import com.fersaiyan.cyanbridge.shared.devices.ScannedDevice as SharedScannedDevice

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fersaiyan.cyanbridge.devices.ADDeviceSupportPolicy
import com.fersaiyan.cyanbridge.shared.devices.DeviceClass
import com.fersaiyan.cyanbridge.devices.DeviceClassifier
import com.fersaiyan.cyanbridge.devices.DeviceProfileStore
import com.fersaiyan.cyanbridge.devices.metarayban.MetaRaybanManager
import com.fersaiyan.cyanbridge.devices.ScannedDevice
import com.fersaiyan.cyanbridge.ui.adglasses.ADGlassesTheme
import com.fersaiyan.cyanbridge.ui.adglasses.ADNativeDeviceBindScreen
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.XXPermissions
import com.oudmon.ble.base.bluetooth.BleOperateManager
import com.oudmon.ble.base.scan.BleScannerHelper
import com.oudmon.ble.base.scan.ScanRecord
import com.oudmon.ble.base.scan.ScanWrapperCallback
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class DeviceBindActivity : BaseActivity() {
    private var scanSize = 0
    private val scanTimeout = ScanTimeout()
    private val handler = Handler(Looper.getMainLooper())
    private val deviceList = mutableListOf<ScannedDevice>()
    private val bleScanCallback = BleCallback()

    private var scannedDevices by mutableStateOf<List<ScannedDevice>>(emptyList())
    private var isScanning by mutableStateOf(false)
    private var connectingDevice by mutableStateOf<ScannedDevice?>(null)
    private var selectedDeviceClass by mutableStateOf(DeviceClass.HEY_CYAN)
    private var initialScanStarted = false
    private var lastDeviceListPublishAtMs = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EventBus.getDefault().register(this)
        setContent {
            ADGlassesTheme {
                ADNativeDeviceBindScreen(
                    devices = scannedDevices.map { it.toShared() },
                    isScanning = isScanning,
                    connectingDevice = connectingDevice?.toShared(),
                    selectedClass = selectedDeviceClass,
                    onScan = ::startScan,
                    onStopScan = ::stopScan,
                    onSelectDevice = { sharedDevice ->
                        val device = deviceList.firstOrNull {
                            it.macAddress.equals(sharedDevice.macAddress, ignoreCase = true)
                        }
                        if (device != null && ADDeviceSupportPolicy.isPairable(device.detectedClass)) {
                            // AD Glasses currently has one validated BLE product family: HeyCyan.
                            // Detection is the source of truth; do not ask the user to classify it.
                            selectedDeviceClass = device.detectedClass
                            connectingDevice = device
                            confirmConnection()
                        }
                    },
                    onSelectedClassChange = { requested ->
                        if (ADDeviceSupportPolicy.isPairable(requested)) {
                            selectedDeviceClass = requested
                        }
                    },
                    onConfirmConnection = ::confirmConnection,
                    onDismissConnection = { connectingDevice = null },
                    onBack = ::finish,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!initialScanStarted) {
            initialScanStarted = true
            startScan()
        }
    }

    // BaseActivity invokes this after Compose installs its host view; no ViewBinding remains.
    override fun setupViews() = Unit

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(messageEvent: BluetoothEvent) {
        Log.i(TAG, "onMessageEvent: ${messageEvent.connect}")
        if (messageEvent.connect) finish()
    }

    private fun startScan() {
        handler.removeCallbacks(scanTimeout)
        deviceList.clear()
        scannedDevices = emptyList()
        lastDeviceListPublishAtMs = 0L
        if (!hasBluetooth(this)) {
            isScanning = false
            requestBluetoothPermission(this, PermissionCallback())
            return
        }
        BleScannerHelper.getInstance().reSetCallback()
        if (!BluetoothUtils.isEnabledBluetooth(this)) {
            startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_ENABLE_BLUETOOTH)
            return
        }
        scanSize = 0
        isScanning = true
        BleScannerHelper.getInstance().scanDevice(this, null, bleScanCallback)
        handler.postDelayed(scanTimeout, 15_000)
    }

    private fun stopScan() {
        handler.removeCallbacks(scanTimeout)
        BleScannerHelper.getInstance().stopScan(this)
        isScanning = false
    }

    private fun confirmConnection() {
        val device = connectingDevice ?: return
        if (!ADDeviceSupportPolicy.isPairable(selectedDeviceClass)) {
            connectingDevice = null
            Toast.makeText(
                this,
                "That glasses profile is not enabled in AD Glasses.",
                Toast.LENGTH_LONG,
            ).show()
            return
        }
        if (!hasBluetooth(this)) {
            Toast.makeText(this, "Bluetooth permission is required to connect", Toast.LENGTH_SHORT).show()
            requestBluetoothPermission(this, PermissionCallback())
            return
        }
        connectingDevice = null
        handler.removeCallbacks(scanTimeout)
        BleScannerHelper.getInstance().stopScan(this)
        isScanning = false

        AutoPairManager.setAutoReconnectSuppressed(false, reason = "user_manual_pair")
        device.userSelectedClass = null
        DeviceProfileStore.saveLastSelected(
            this,
            DeviceProfile(
                macAddress = device.macAddress,
                advertisedName = device.advertisedName,
                detectedClass = device.detectedClass,
                selectedClass = device.detectedClass,
                userOverridden = false,
            ),
        )

        // The only current generic BLE pairing route is HeyCyan. Meta will use a
        // dedicated integration when enabled; upstream-only managers stay unreachable.
        BleOperateManager.getInstance().connectDirectly(device.macAddress)
    }

    private fun upsertDevice(
        mac: String,
        name: String?,
        rssi: Int,
        scanRecord: ScanRecord? = null,
    ) {
        val sanitizedName = name?.trim()?.takeIf { it.isNotEmpty() }
        val existingIndex = deviceList.indexOfFirst { it.macAddress.equals(mac, ignoreCase = true) }
        if (existingIndex >= 0) {
            val existing = deviceList[existingIndex]
            val previousName = existing.advertisedName
            val previousClass = existing.detectedClass
            existing.rssi = rssi
            if (existing.advertisedName.isNullOrBlank() && sanitizedName != null) {
                existing.advertisedName = sanitizedName
            }
            scanRecord?.serviceUuids?.takeIf { it.isNotEmpty() }?.let { existing.serviceUuids = it }
            existing.setDetectedClass(DeviceClassifier.guessDeviceClass(existing.advertisedName, existing.serviceUuids))
            publishDevices(
                force = previousName != existing.advertisedName || previousClass != existing.detectedClass,
            )
            return
        }
        val detectedClass = DeviceClassifier.guessDeviceClass(sanitizedName, scanRecord?.serviceUuids.orEmpty())
        if (sanitizedName == null && detectedClass == DeviceClass.UNKNOWN) return

        val newDevice = ScannedDevice(
            macAddress = mac,
            advertisedName = sanitizedName ?: detectedClass.displayName(),
            rssi = rssi,
            serviceUuids = scanRecord?.serviceUuids.orEmpty(),
        )
        // Old per-MAC overrides remain readable for upstream compatibility but no longer
        // change AD Glasses pairing decisions; the detector owns the product classification.
        scanSize++
        deviceList += newDevice
        publishDevices(force = true)
        if (scanSize > 30) BleScannerHelper.getInstance().stopScan(this)
    }

    /** Avoid repeatedly recreating scan rows while TalkBack is navigating them. */
    private fun publishDevices(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastDeviceListPublishAtMs < DEVICE_LIST_PUBLISH_INTERVAL_MS) return
        lastDeviceListPublishAtMs = now
        scannedDevices = deviceList
            .filter { ADDeviceSupportPolicy.shouldShowScanResult(it.detectedClass) }
            .toList()
    }

    override fun onDestroy() {
        handler.removeCallbacks(scanTimeout)
        if (EventBus.getDefault().isRegistered(this)) EventBus.getDefault().unregister(this)
        super.onDestroy()
    }

    @Deprecated("Deprecated in AndroidX Activity; retained for the vendor scanner flow.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ENABLE_BLUETOOTH && BluetoothUtils.isEnabledBluetooth(this)) {
            startScan()
        }
    }

    private inner class ScanTimeout : Runnable {
        override fun run() {
            BleScannerHelper.getInstance().stopScan(this@DeviceBindActivity)
            isScanning = false
        }
    }

    private inner class PermissionCallback : OnPermissionCallback {
        override fun onGranted(permissions: MutableList<String>, all: Boolean) {
            if (all) startScan()
        }

        override fun onDenied(permissions: MutableList<String>, never: Boolean) {
            super.onDenied(permissions, never)
            Toast.makeText(
                this@DeviceBindActivity,
                "Bluetooth permission is required to find and connect to glasses",
                Toast.LENGTH_LONG,
            ).show()
            if (never) XXPermissions.startPermissionActivity(this@DeviceBindActivity, permissions)
        }
    }

    private inner class BleCallback : ScanWrapperCallback {
        override fun onStart() {
            isScanning = true
        }

        override fun onStop() {
            isScanning = false
        }

        override fun onLeScan(device: BluetoothDevice?, rssi: Int, scanRecord: ByteArray?) {
            if (!hasBluetooth(this@DeviceBindActivity)) return
            val bluetoothDevice = device ?: return
            val address = bluetoothDevice.address
            val name = runCatching { bluetoothDevice.name }.getOrNull()
            upsertDevice(address, name, rssi)
        }

        override fun onScanFailed(errorCode: Int) {
            isScanning = false
            Log.w(TAG, "Scan failed: $errorCode")
        }

        override fun onParsedData(device: BluetoothDevice?, scanRecord: ScanRecord?) {
            if (!hasBluetooth(this@DeviceBindActivity)) return
            val bluetoothDevice = device ?: return
            val address = bluetoothDevice.address
            val name = runCatching { scanRecord?.deviceName ?: bluetoothDevice.name }.getOrNull()
            val rssi = deviceList.firstOrNull { it.macAddress.equals(address, true) }?.rssi ?: 0
            upsertDevice(address, name, rssi, scanRecord)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) = Unit
    }

    private fun ScannedDevice.toShared(): SharedScannedDevice = SharedScannedDevice(
        macAddress = macAddress,
        advertisedName = advertisedName,
        rssi = rssi,
        detectedClass = detectedClass,
        selectedClass = null,
        userOverridden = false,
    )

    private companion object {
        const val REQUEST_ENABLE_BLUETOOTH = 300
        const val DEVICE_LIST_PUBLISH_INTERVAL_MS = 1_000L
    }
}

package com.achyut.adglasses.bridge.devices.memomind

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.achyut.adglasses.bridge.core.DeviceInfo
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * BLE scanner that discovers MemoMind glasses by name pattern matching.
 *
 * Uses Android's [BluetoothLeScanner] with [ScanSettings.SCAN_MODE_LOW_LATENCY].
 * Filters results by device name against [MemoMindConstants.NAME_PATTERNS].
 * Emits [DeviceInfo] objects for each matching device.
 *
 * Tag: MemoMindBleScanner
 */
class MemoMindBleScanner(
    private val context: Context,
) {
    companion object {
        private const val TAG = "MemoMindBleScanner"
    }

    private val bluetoothAdapter: BluetoothAdapter?
        get() {
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE)
                as? android.bluetooth.BluetoothManager
            return manager?.adapter
        }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Start scanning for MemoMind glasses. Emits [DeviceInfo] for each
     * matching device discovered. The flow completes when [stopScan] is
     * called via [android.bluetooth.le.ScanCallback.onScanFailed] or when
     * the collector cancels the collection.
     */
    @SuppressLint("MissingPermission")
    fun scan(): Flow<DeviceInfo> = callbackFlow {
        if (!hasScanPermissions()) {
            val error = SecurityException("Bluetooth scan and connect permissions are required")
            Log.w(TAG, error.message.orEmpty())
            close(error)
            return@callbackFlow
        }

        val adapter = bluetoothAdapter
        if (adapter == null) {
            Log.w(TAG, "BluetoothAdapter is null – cannot scan")
            close()
            return@callbackFlow
        }

        val scanner: BluetoothLeScanner
        try {
            scanner = adapter.bluetoothLeScanner
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException getting bluetoothLeScanner", e)
            close(e)
            return@callbackFlow
        }

        val seenAddresses = mutableSetOf<String>()

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val name = device.name
                if (name != null && matchesPattern(name)) {
                    // Deduplicate: skip if we have already emitted this device address.
                    if (!seenAddresses.add(device.address)) return

                    val info = DeviceInfo(
                        id = device.address,
                        name = name,
                        address = device.address,
                        adapterId = "memomind",
                        rssi = result.rssi,
                    )
                    Log.d(TAG, "Found MemoMind device: $name (${device.address}) RSSI=${result.rssi}")
                    trySend(info)
                }
            }

            override fun onBatchScanResults(results: List<ScanResult>) {
                for (result in results) {
                    onScanResult(0, result)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.w(TAG, "BLE scan failed with error code $errorCode")
                close(IllegalStateException("BLE scan failed: errorCode=$errorCode"))
            }
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                }
            }
            .build()

        Log.i(TAG, "Starting BLE scan for MemoMind patterns: ${MemoMindConstants.NAME_PATTERNS}")
        try {
            scanner.startScan(null, settings, scanCallback)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException starting BLE scan", e)
            close(e)
            return@callbackFlow
        }

        awaitClose {
            Log.i(TAG, "Stopping BLE scan")
            try {
                scanner.stopScan(scanCallback)
            } catch (e: SecurityException) {
                Log.w(TAG, "SecurityException stopping BLE scan", e)
            }
        }
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    private fun hasScanPermissions(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
        }
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.BLUETOOTH_SCAN,
        ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.BLUETOOTH_CONNECT,
            ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Returns true if [name] contains one of the known MemoMind name patterns (case-insensitive).
     */
    private fun matchesPattern(name: String): Boolean {
        val lower = name.lowercase()
        return MemoMindConstants.NAME_PATTERNS.any { pattern ->
            lower.contains(pattern.lowercase())
        }
    }
}

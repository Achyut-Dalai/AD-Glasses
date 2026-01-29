package com.fersaiyan.cyanbridge.ui

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.charset.StandardCharsets

/**
 * Small helper that watches BLE payloads and tries to extract an IPv4
 * address. We feed it from the Bluetooth callbacks and then read the
 * last-seen IP from the data download flow.
 */
class BleIpBridge {
    private val _ip = MutableStateFlow<String?>(null)
    val ip = _ip.asStateFlow()

    fun onCharacteristicChanged(source: String, value: ByteArray) {
        val msg = value.toString(StandardCharsets.UTF_8)
        Log.d("BleIpBridge", "[$source] raw='${msg.replace("\n", "\\n")}'")

        // Regex to find an IPv4 address in the payload
        val regex = Regex("""(\b(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\b)""")
        val match = regex.find(msg)
        if (match != null) {
            val foundIp = match.value
            Log.i("BleIpBridge", "Detected device IP in BLE payload: $foundIp")
            _ip.value = foundIp
        }
    }
}

// Single shared instance used across the app
val bleIpBridge: BleIpBridge = BleIpBridge()

package com.fersaiyan.cyanbridge.ota

import android.content.Context
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.os.Build
import android.util.Log
import com.fersaiyan.cyanbridge.ui.wifi.p2p.WifiP2pManagerSingleton
import com.oudmon.ble.base.bluetooth.BleOperateManager
import com.oudmon.ble.base.bluetooth.DeviceManager
import com.oudmon.ble.base.communication.LargeDataHandler
import com.oudmon.ble.base.communication.bigData.resp.GlassesDeviceNotifyListener
import com.oudmon.ble.base.communication.bigData.resp.GlassesDeviceNotifyRsp
import com.oudmon.ble.base.communication.utils.ByteUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface

enum class OtaState {
    IDLE,
    CHECKING_CONNECTION,
    ENTERING_OTA_MODE,
    STARTING_P2P,
    WAITING_BLE_IP,
    STARTING_HTTP_SERVER,
    SENDING_URL,
    DOWNLOADING,
    COMPLETE,
    FAILED,
}

data class OtaUiState(
    val state: OtaState = OtaState.IDLE,
    val detail: String = "",
    val progress: Int? = null,
    val error: String? = null,
)

/**
 * Orchestrates OTA firmware updates to the glasses.
 *
 * Flow (mirrors the official HeyCyan app):
 * 1. Verify BLE connection
 * 2. Send glassesControl({2,1,5}) to enter OTA mode
 * 3. Start P2P peer discovery + connection
 * 4. Wait for glasses to report their P2P IP via BLE notify (type 0x08)
 * 5. Start HTTP server on the phone's P2P IP
 * 6. Send the OTA URL to glasses via writeIpToSoc (BLE 0xFC)
 * 7. Wait for glasses to download the SWU and flash it
 */
class OtaManager(
    private val context: Context,
) {
    private val _uiState = MutableStateFlow(OtaUiState())
    val uiState: StateFlow<OtaUiState> = _uiState

    private var httpServer: OtaHttpServer? = null
    private var otaJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var p2pManager: WifiP2pManagerSingleton? = null
    private var p2pCallback: WifiP2pManagerSingleton.WifiP2pCallback? = null
    private var otaNotifyListener: GlassesDeviceNotifyListener? = null
    private var otaNotifyRegistered = false

    @Volatile
    private var bleIpReceived: String? = null

    @Volatile
    private var p2pConnected = false

    @Volatile
    private var p2pInfo: WifiP2pInfo? = null

    @Volatile
    private var otaComplete = false

    @Volatile
    private var otaFailed = false

    val isActive: Boolean get() = otaJob?.isActive == true

    fun startOta(swuFile: File) {
        if (otaJob?.isActive == true) {
            Log.w(TAG, "OTA already in progress")
            return
        }
        otaJob = scope.launch { runOta(swuFile) }
    }

    fun cancel() {
        Log.i(TAG, "OTA cancelled by user")
        otaJob?.cancel()
        cleanup()
        _uiState.value = OtaUiState()
    }

    private fun cleanup() {
        httpServer?.stop()
        httpServer = null
        p2pManager?.removeGroup { _ -> }
        p2pManager?.unregisterReceiver()
        if (otaNotifyRegistered) {
            try {
                LargeDataHandler.getInstance().removeOutDeviceListener(2)
            } catch (_: Exception) {
            }
            otaNotifyRegistered = false
        }
        p2pCallback = null
        otaNotifyListener = null
    }

    private suspend fun runOta(swuFile: File) {
        val otaStartTime = System.currentTimeMillis()
        Log.i(TAG, "========== OTA FLOW START ==========")
        Log.i(TAG, "  SWU file: ${swuFile.absolutePath}")
        Log.i(TAG, "  SWU size: ${swuFile.length()} bytes (${swuFile.length() / 1024 / 1024} MB)")

        try {
            // Reset state
            bleIpReceived = null
            p2pConnected = false
            p2pInfo = null
            otaComplete = false
            otaFailed = false

            // Step 1: Check BLE connection
            Log.i(TAG, "[Step 1/7] Checking BLE connection...")
            updateState(OtaState.CHECKING_CONNECTION, "Checking BLE connection...")
            if (!BleOperateManager.getInstance().isConnected) {
                Log.e(TAG, "[Step 1/7] FAIL: BLE not connected")
                updateState(OtaState.FAILED, error = "Bluetooth not connected to glasses")
                return
            }

            val deviceName = try {
                DeviceManager.getInstance().deviceName ?: "unknown"
            } catch (_: Exception) {
                "unknown"
            }
            val deviceMac = try {
                DeviceManager.getInstance().deviceAddress ?: "unknown"
            } catch (_: Exception) {
                "unknown"
            }
            Log.i(TAG, "[Step 1/7] OK: Connected to $deviceName ($deviceMac)")

            // Step 2: Enter OTA mode
            Log.i(TAG, "[Step 2/7] Sending OTA mode command (glassesControl {0x02, 0x01, 0x05})...")
            updateState(OtaState.ENTERING_OTA_MODE, "Sending OTA mode command...")
            val otaModeStart = System.currentTimeMillis()
            val otaModeOk = enterOtaMode()
            val otaModeElapsed = System.currentTimeMillis() - otaModeStart
            if (!otaModeOk) {
                Log.e(TAG, "[Step 2/7] FAIL: Glasses rejected OTA mode (${otaModeElapsed}ms)")
                updateState(OtaState.FAILED, error = "Glasses rejected OTA mode command")
                return
            }
            Log.i(TAG, "[Step 2/7] OK: OTA mode accepted (${otaModeElapsed}ms)")

            // Step 3: Start P2P
            Log.i(TAG, "[Step 3/7] Starting P2P discovery...")
            updateState(OtaState.STARTING_P2P, "Starting P2P connection...")
            startP2p()
            Log.i(TAG, "[Step 3/7] P2P discovery initiated")

            // Step 4: Wait for BLE IP notification
            Log.i(TAG, "[Step 4/7] Waiting for glasses P2P IP via BLE notify (type 0x08), timeout=45s...")
            updateState(OtaState.WAITING_BLE_IP, "Waiting for glasses P2P IP via BLE...")
            val bleIpStart = System.currentTimeMillis()
            val glassesIp = waitForBleIp(timeoutMs = 45_000)
            val bleIpElapsed = System.currentTimeMillis() - bleIpStart
            if (glassesIp == null) {
                Log.e(TAG, "[Step 4/7] FAIL: Timed out waiting for BLE IP (${bleIpElapsed}ms)")
                Log.e(TAG, "  bleIpReceived=$bleIpReceived, p2pConnected=$p2pConnected")
                updateState(OtaState.FAILED, error = "Timed out waiting for glasses IP (BLE 0x08)")
                return
            }
            Log.i(TAG, "[Step 4/7] OK: Glasses IP=$glassesIp (${bleIpElapsed}ms)")

            // Step 5: Start HTTP server
            val localIp = getLocalP2pIp() ?: P2P_DEFAULT_IP
            Log.i(TAG, "[Step 5/7] Starting HTTP server on $localIp:$HTTP_PORT...")
            updateState(OtaState.STARTING_HTTP_SERVER, "Starting HTTP server on $localIp:$HTTP_PORT...")
            httpServer = OtaHttpServer(HTTP_PORT)
            httpServer!!.start(swuFile)
            Log.i(TAG, "[Step 5/7] OK: HTTP server started, serving ${swuFile.name}")

            // Step 6: Send OTA URL to glasses
            val firmwareName = swuFile.name
            val url = "http://$localIp:$HTTP_PORT/$firmwareName"
            Log.i(TAG, "[Step 6/7] Sending OTA URL to glasses via BLE writeIpToSoc (0xFC)...")
            Log.i(TAG, "  URL: $url")
            updateState(OtaState.SENDING_URL, "Sending OTA URL: $url")
            sendOtaUrl(url)
            Log.i(TAG, "[Step 6/7] OK: OTA URL sent")

            // Step 7: Wait for download + flash
            Log.i(TAG, "[Step 7/7] Waiting for glasses to download and flash SWU (timeout=5min)...")
            updateState(OtaState.DOWNLOADING, "Glasses downloading and flashing SWU...")
            val downloadStart = System.currentTimeMillis()
            val success = waitForOtaResult(timeoutMs = 300_000) // 5 min timeout
            val downloadElapsed = System.currentTimeMillis() - downloadStart
            val totalElapsed = System.currentTimeMillis() - otaStartTime

            if (success) {
                Log.i(TAG, "[Step 7/7] OK: OTA complete! (${downloadElapsed}ms download, ${totalElapsed}ms total)")
                Log.i(TAG, "========== OTA FLOW SUCCESS ==========")
                updateState(OtaState.COMPLETE, "OTA complete! Glasses will reboot.")
            } else {
                Log.e(TAG, "[Step 7/7] FAIL: OTA failed or timed out (${downloadElapsed}ms)")
                Log.e(TAG, "  otaComplete=$otaComplete, otaFailed=$otaFailed")
                Log.e(TAG, "========== OTA FLOW FAILED ==========")
                updateState(OtaState.FAILED, error = "OTA failed or timed out during download/flash")
            }
        } catch (e: CancellationException) {
            Log.i(TAG, "========== OTA FLOW CANCELLED ==========")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "========== OTA FLOW EXCEPTION ==========", e)
            Log.e(TAG, "  ${e.javaClass.simpleName}: ${e.message}")
            updateState(OtaState.FAILED, error = "OTA failed: ${e.message}")
        } finally {
            cleanup()
        }
    }

    /**
     * Send glassesControl({2,1,5}) to enter OTA mode.
     * Returns true if glasses respond with workTypeIng==5 (OTA mode).
     */
    private suspend fun enterOtaMode(): Boolean = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { cont ->
            LargeDataHandler.getInstance().glassesControl(
                byteArrayOf(0x02, 0x01, 0x05)
            ) { _, response ->
                if (cont.isActive) {
                    if (response.dataType == 1 && response.errorCode == 0) {
                        Log.i(TAG, "glassesControl OTA response: workTypeIng=${response.workTypeIng}")
                        // workTypeIng==5 means OTA mode accepted
                        cont.resume(response.workTypeIng == 5) {}
                    } else {
                        Log.e(TAG, "glassesControl OTA failed: dataType=${response.dataType}, errorCode=${response.errorCode}")
                        cont.resume(false) {}
                    }
                }
            }
            // Timeout fallback
            scope.launch {
                delay(10_000)
                if (cont.isActive) {
                    Log.e(TAG, "enterOtaMode timed out")
                    cont.resume(false) {}
                }
            }
        }
    }

    /**
     * Start P2P peer discovery and connection, mirroring the official app flow.
     */
    private fun startP2p() {
        p2pManager = WifiP2pManagerSingleton.getInstance(context)

        p2pCallback = object : WifiP2pManagerSingleton.WifiP2pCallback {
            override fun onWifiP2pEnabled() {
                Log.i(TAG, "P2P enabled")
            }

            override fun onWifiP2pDisabled() {
                Log.w(TAG, "P2P disabled")
            }

            override fun onPeersChanged(peers: Collection<WifiP2pDevice>) {
                Log.i(TAG, "P2P peers changed: ${peers.size} peers")
                if (p2pManager?.isConnecting() == true || p2pManager?.isConnected() == true) {
                    return
                }
                // Find the glasses peer by matching the paired device name/address
                val pairedName = try {
                    DeviceManager.getInstance().deviceName
                } catch (_: Exception) {
                    null
                }
                val pairedMac = try {
                    DeviceManager.getInstance().deviceAddress
                } catch (_: Exception) {
                    null
                }
                val target = peers.firstOrNull { peer ->
                    peer.deviceName == pairedName ||
                        peer.deviceAddress == pairedMac ||
                        peer.deviceName?.endsWith("_${pairedMac?.takeLast(5)?.replace(":", "")}") == true
                }
                if (target != null) {
                    Log.i(TAG, "Found glasses P2P peer: ${target.deviceName} / ${target.deviceAddress}")
                    p2pManager?.connectToDevice(target)
                } else {
                    Log.w(TAG, "No matching glasses peer found among ${peers.size} peers")
                }
            }

            override fun onConnected(info: WifiP2pInfo) {
                Log.i(TAG, "P2P connected: groupFormed=${info.groupFormed}, isGroupOwner=${info.isGroupOwner}")
                p2pConnected = info.groupFormed
                p2pInfo = info
            }

            override fun onDisconnected() {
                Log.w(TAG, "P2P disconnected")
                p2pConnected = false
            }

            override fun onPeerDiscoveryStarted() {
                Log.i(TAG, "P2P discovery started")
            }

            override fun onPeerDiscoveryFailed(reason: Int) {
                Log.e(TAG, "P2P discovery failed: reason=$reason")
            }

            override fun onConnectRequestSent() {
                Log.i(TAG, "P2P connect request sent")
            }

            override fun onConnectRequestFailed(reason: Int) {
                Log.e(TAG, "P2P connect request failed: reason=$reason")
            }

            override fun onThisDeviceChanged(device: WifiP2pDevice) {
                Log.i(TAG, "P2P this device changed: ${device.deviceName}")
            }

            override fun connecting() {
                Log.i(TAG, "P2P connecting...")
            }

            override fun cancelConnect() {
                Log.i(TAG, "P2P connect cancelled")
            }

            override fun cancelConnectFail(reason: Int) {
                Log.e(TAG, "P2P cancel connect failed: reason=$reason")
            }

            override fun retryAlsoFailed() {
                Log.e(TAG, "P2P retry also failed")
            }
        }

        p2pManager?.registerReceiver()
        p2pManager?.resetFailCount()
        p2pManager?.startPeerDiscovery()
    }

    /**
     * Wait for the glasses to send their P2P IP via BLE notification (type 0x08).
     */
    private suspend fun waitForBleIp(timeoutMs: Long): String? {
        // Register BLE notify listener for device notifications (cmdType=2)
        otaNotifyListener = object : GlassesDeviceNotifyListener() {
            override fun parseData(cmdType: Int, response: GlassesDeviceNotifyRsp) {
                val load = response.loadData
                if (load.size < 7) return
                when (load[6].toInt()) {
                    0x08 -> {
                        // Glasses IP address
                        if (load.size >= 11) {
                            val ip = "${ByteUtil.byteToInt(load[7])}." +
                                "${ByteUtil.byteToInt(load[8])}." +
                                "${ByteUtil.byteToInt(load[9])}." +
                                "${ByteUtil.byteToInt(load[10])}"
                            Log.i(TAG, "[BLE] Glasses IP received: $ip (raw bytes: ${load[7]},${load[8]},${load[9]},${load[10]})")
                            bleIpReceived = ip
                        } else {
                            Log.w(TAG, "[BLE] IP notify too short: ${load.size} bytes")
                        }
                    }
                    0x09 -> {
                        // P2P/WiFi error
                        val errorCode = ByteUtil.byteToInt(load.getOrNull(7) ?: 0)
                        Log.e(TAG, "[BLE] P2P/WiFi error from glasses: errorCode=$errorCode (raw=${load.getOrNull(7)})")
                    }
                    0x04 -> {
                        // OTA progress
                        if (load.size >= 10) {
                            val progress = ByteUtil.byteToInt(load[7])
                            val phase = ByteUtil.byteToInt(load.getOrNull(8) ?: 0)
                            Log.i(TAG, "[BLE] OTA progress: $progress% (phase=$phase)")
                        }
                    }
                    0x07 -> {
                        // OTA complete
                        Log.i(TAG, "[BLE] OTA COMPLETE notification received!")
                        otaComplete = true
                    }
                    else -> {
                        Log.d(TAG, "[BLE] Unknown notify type: 0x${load[6].toString(16)} (${load.size} bytes)")
                    }
                }
            }
        }

        try {
            LargeDataHandler.getInstance().addOutDeviceListener(2, otaNotifyListener)
            otaNotifyRegistered = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register OTA notify listener: ${e.message}")
        }

        // Poll for BLE IP with timeout
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val ip = bleIpReceived
            if (ip != null) return ip
            delay(500)
        }
        return null
    }

    /**
     * Send the OTA URL to the glasses via BLE writeIpToSoc (0xFC).
     */
    private fun sendOtaUrl(url: String) {
        Log.i(TAG, "Sending OTA URL via BLE: $url")
        LargeDataHandler.getInstance().writeIpToSoc(url) { cmdType, response ->
            Log.i(TAG, "writeIpToSoc callback: cmdType=$cmdType, response=$response")
        }
    }

    /**
     * Wait for OTA download + flash to complete.
     * Monitors BLE notifications for progress (0x04) and completion (0x07).
     */
    private suspend fun waitForOtaResult(timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (otaComplete) return true
            if (otaFailed) return false
            delay(1000)
        }
        return false
    }

    /**
     * Get the phone's local IP on the P2P network.
     * Typically 192.168.49.1 when the phone is the P2P group owner.
     */
    private fun getLocalP2pIp(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue
                for (addr in intf.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val ip = addr.hostAddress
                        if (ip?.startsWith("192.168.") == true) {
                            return ip
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get local P2P IP: ${e.message}")
        }
        return P2P_DEFAULT_IP
    }

    private fun updateState(state: OtaState, detail: String = "", error: String? = null) {
        _uiState.value = OtaUiState(
            state = state,
            detail = detail,
            error = error,
            progress = when (state) {
                OtaState.COMPLETE -> 100
                OtaState.FAILED -> null
                else -> null // indeterminate
            },
        )
        Log.i(TAG, "State: $state — $detail")
    }

    companion object {
        private const val TAG = "OtaManager"
        private const val HTTP_PORT = 8080
        private const val P2P_DEFAULT_IP = "192.168.49.1"
    }
}

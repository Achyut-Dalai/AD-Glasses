package com.ad_glasses.ota

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.ad_glasses.ui.wifi.p2p.WifiP2pManagerSingleton
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

enum class OtaTarget {
    V821_WIFI,
    JIELI_BLE,
}

enum class OtaState {
    IDLE,
    CHECKING_CONNECTION,
    ENTERING_OTA_MODE,
    STARTING_P2P,
    WAITING_BLE_IP,
    STARTING_HTTP_SERVER,
    SENDING_URL,
    DOWNLOADING,
    TEARING_DOWN_P2P,
    WAITING_FOR_FRESH_BLE,
    PREPARING_BLE_DFU,
    BLE_DFU_TRANSFERRING,
    VERIFYING_FIRMWARE,
    CANCELLING,
    COMPLETE,
    FAILED,
}

/** Readiness checkpoints required before the next OTA stage may use BLE. */
enum class OtaReadinessStage {
    AFTER_WIFI,
    AFTER_BLE,
}

internal fun isWifiOtaModeReady(
    dataType: Int,
    glassWorkType: Int,
    otaStatus: Int,
): Boolean = dataType == 1 && glassWorkType == 5 && otaStatus == 1

internal fun isWifiOtaCompleteNotification(notifyType: Int, result: Int?): Boolean =
    notifyType == 0x07 && result == 1

internal fun isCombinedOtaFilenamePair(wifiFilename: String, bleFilename: String): Boolean =
    OtaTarget.V821_WIFI.isExpectedFirmwareFilename(wifiFilename) &&
        OtaTarget.JIELI_BLE.isExpectedFirmwareFilename(bleFilename)

internal fun selectLocalP2pIpv4(
    glassesIp: String,
    candidates: List<Pair<String, String>>,
): String? {
    val octets = glassesIp.split('.')
    if (octets.size != 4 || octets.any { it.toIntOrNull() !in 0..255 }) return null
    val subnetPrefix = octets.take(3).joinToString(".") + "."
    val onGlassesSubnet = candidates.filter { (_, ip) ->
        ip != glassesIp && ip.startsWith(subnetPrefix)
    }
    return onGlassesSubnet.firstOrNull { (name, _) -> isP2pInterface(name) }?.second
        ?: onGlassesSubnet.firstOrNull()?.second
}

private fun isP2pInterface(name: String): Boolean =
    name.contains("p2p", ignoreCase = true) || name.contains("wfd", ignoreCase = true)

data class OtaUiState(
    val state: OtaState = OtaState.IDLE,
    val detail: String = "",
    val progress: Int? = null,
    val error: String? = null,
)

/**
 * Orchestrates OTA firmware updates to the glasses.
 *
 * Combined flow (mirrors the official HeyCyan app):
 * 1. Verify BLE connection
 * 2. Flash the Wi-Fi SWU over P2P/HTTP and require notify 0x07/result 1
 * 3. Tear down P2P and restore the phone's default route while retaining the lease
 * 4. Re-read BLE/device info, then run the verified JieLi DFU sequence
 * 5. Clear stale BLE firmware state, reconnect, and require a fresh final read
 */
class OtaManager(
    private val context: Context,
) {
    private val _uiState = MutableStateFlow(OtaUiState())
    val uiState: StateFlow<OtaUiState> = _uiState

    private var httpServer: OtaHttpServer? = null
    private var bleDfuManager: BleDfuManager? = null
    private var otaJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var p2pManager: WifiP2pManagerSingleton? = null
    private var p2pCallback: WifiP2pManagerSingleton.WifiP2pCallback? = null
    private var p2pReceiverRegistered = false
    private var otaNotifyListener: GlassesDeviceNotifyListener? = null
    private var otaNotifyRegistered = false
    private var otaWakeLock: PowerManager.WakeLock? = null
    private var cleanupInProgress = false
    private var cleanupCompletion: CompletableDeferred<Unit>? = null
    private var retainSessionAfterCleanup = false
    private var resetUiAfterCleanup = false
    private var cleanupRetryPending = false
    private var cleanupRetryJob: Job? = null
    @Volatile private var sessionFinishNotified = true
    private var onSessionFinished: () -> Unit = {}

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

    @Volatile
    private var awaitingFreshBleReadiness = false

    /** Includes the cleanup/recovery window after the coroutine's transport work ends. */
    val isActive: Boolean get() = otaJob?.isActive == true || !sessionFinishNotified

    val isAwaitingFreshBleReadiness: Boolean get() = awaitingFreshBleReadiness

    /** Start the official combined order: Wi-Fi SWU, then BLE/JieLi BIN. */
    fun startCombinedOta(
        wifiFirmwareFile: File,
        bleFirmwareFile: File,
        awaitFreshBleReadiness: suspend (OtaReadinessStage) -> Boolean,
        onSessionFinished: () -> Unit,
    ) {
        if (isActive) {
            Log.w(TAG, "OTA already in progress")
            return
        }
        sessionFinishNotified = false
        cleanupInProgress = false
        cleanupCompletion = null
        retainSessionAfterCleanup = false
        resetUiAfterCleanup = false
        cleanupRetryPending = false
        cleanupRetryJob?.cancel()
        cleanupRetryJob = null
        this.onSessionFinished = onSessionFinished
        val invalidPairMessage = validateFirmwarePair(wifiFirmwareFile, bleFirmwareFile)
        if (invalidPairMessage != null) {
            Log.e(TAG, invalidPairMessage)
            updateState(OtaState.FAILED, error = invalidPairMessage)
            cleanup()
            return
        }
        acquireOtaWakeLock()
        otaJob = scope.launch {
            try {
                val wifiSucceeded = runWifiOta(wifiFirmwareFile)
                if (!wifiSucceeded) return@launch

                // Do not let a BLE reconnect or another command race the P2P teardown.
                updateState(
                    OtaState.TEARING_DOWN_P2P,
                    "Stage 1/2 complete. Tearing down Wi-Fi Direct before BLE DFU...",
                    progress = 50,
                )
                awaitingFreshBleReadiness = true
                cleanupWifiTransportForNextStage()
                ensureActive()

                updateState(
                    OtaState.WAITING_FOR_FRESH_BLE,
                    "Stage 1/2 complete. Waiting for a fresh BLE/device-info read...",
                    progress = 50,
                )
                if (!awaitFreshBleReadiness(OtaReadinessStage.AFTER_WIFI)) {
                    updateState(
                        OtaState.FAILED,
                        error = "Could not establish a fresh BLE/device-info session after Wi-Fi OTA",
                    )
                    return@launch
                }
                awaitingFreshBleReadiness = false

                updateState(
                    OtaState.PREPARING_BLE_DFU,
                    "Stage 2/2: preparing the BLE/JieLi firmware transfer...",
                    progress = 50,
                )
                val bleSucceeded = runBleDfu(bleFirmwareFile)
                if (!bleSucceeded) return@launch

                // The official app clears its BLE firmware cache, disconnects, and only then
                // considers the update ready. Do not report a pair success before that read.
                awaitingFreshBleReadiness = true
                updateState(
                    OtaState.VERIFYING_FIRMWARE,
                    "Stage 2/2 complete. Reconnecting and verifying both firmware components...",
                    progress = 100,
                )
                if (!awaitFreshBleReadiness(OtaReadinessStage.AFTER_BLE)) {
                    updateState(
                        OtaState.FAILED,
                        error = "BLE DFU completed, but fresh post-update device info was unavailable",
                    )
                    return@launch
                }
                awaitingFreshBleReadiness = false
                updateState(OtaState.COMPLETE, "Combined OTA complete. Both firmware components are ready.")
            } catch (error: CancellationException) {
                Log.i(TAG, "========== COMBINED OTA FLOW CANCELLED ==========")
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "========== COMBINED OTA FLOW EXCEPTION ==========", error)
                updateState(OtaState.FAILED, error = "OTA failed: ${error.message ?: error.javaClass.simpleName}")
            } finally {
                awaitingFreshBleReadiness = false
                cleanup()
            }
        }
    }

    fun cancel() {
        Log.i(TAG, "OTA cancelled by user")
        awaitingFreshBleReadiness = false
        resetUiAfterCleanup = true
        updateState(OtaState.CANCELLING, "Cancelling the combined OTA session...")
        otaJob?.cancel()
        bleDfuManager?.cancel()
        bleDfuManager = null
        cleanup()
    }

    fun onBluetoothDisconnected() {
        if (awaitingFreshBleReadiness) {
            Log.i(TAG, "Bluetooth disconnected during the expected OTA readiness reconnect")
            return
        }
        val wasActive = isActive
        if (wasActive && _uiState.value.state != OtaState.CANCELLING) {
            updateState(OtaState.FAILED, error = "Bluetooth disconnected during OTA")
        }
        Log.i(TAG, "Bluetooth disconnected; abandoning OTA resources")
        otaJob?.cancel()
        httpServer?.stop()
        httpServer = null
        bleDfuManager?.cancel()
        bleDfuManager = null
        if (!sessionFinishNotified) {
            // Keep the lease until the normal retrying P2P teardown confirms that the
            // shared Wi-Fi Direct controller is no longer owned by this OTA session.
            cleanup()
        }
    }

    /** Retry a quarantined P2P teardown after the BLE link becomes usable again. */
    fun onBluetoothConnected() {
        retryP2pCleanupIfNeeded()
    }

    private fun cleanup(retainSession: Boolean = false) {
        if (cleanupInProgress) {
            // A final cleanup (cancellation, failure, or completion) supersedes an
            // intermediate Wi-Fi teardown so the lease cannot be stranded.
            if (!retainSession) {
                retainSessionAfterCleanup = false
                retryP2pCleanupIfNeeded()
            }
            return
        }
        cleanupInProgress = true
        retainSessionAfterCleanup = retainSession
        httpServer?.stop()
        httpServer = null
        bleDfuManager?.cancel()
        bleDfuManager = null
        val manager = p2pManager

        if (manager == null) {
            finishCleanup()
            return
        }

        manager.stopP2pOperations()
        manager.cancelP2pConnection()
        if (!cleanupInProgress) return
        removeP2pGroup(manager, attempt = 1)
    }

    /** Tear down only the Wi-Fi transport while retaining the exclusive OTA lease. */
    private suspend fun cleanupWifiTransportForNextStage() {
        if (p2pManager == null && httpServer == null && !otaNotifyRegistered) {
            restoreDefaultNetwork()
            return
        }

        val completion = CompletableDeferred<Unit>()
        cleanupCompletion = completion
        cleanup(retainSession = true)
        try {
            val confirmed = withTimeoutOrNull(P2P_TEARDOWN_TIMEOUT_MS) {
                completion.await()
                true
            } ?: false
            if (!confirmed) {
                throw IllegalStateException("Could not confirm Wi-Fi Direct teardown before BLE DFU")
            }
        } finally {
            if (!completion.isCompleted) {
                cleanup(retainSession = false)
            }
        }
    }

    private fun removeP2pGroup(manager: WifiP2pManagerSingleton, attempt: Int) {
        val resultHandled = java.util.concurrent.atomic.AtomicBoolean(false)
        val handleResult: (Boolean) -> Unit = { success ->
            if (success) {
                Log.i(TAG, "OTA P2P removal accepted on cleanup attempt $attempt; waiting for disconnect")
            } else {
                Log.w(TAG, "OTA P2P removal failed on attempt $attempt; checking group state")
            }
            awaitP2pDisconnect(manager, attempt)
        }
        scope.launch {
            delay(P2P_GROUP_REMOVE_ACTION_TIMEOUT_MS)
            if (cleanupInProgress && resultHandled.compareAndSet(false, true)) {
                Log.w(TAG, "OTA P2P removal gave no callback on attempt $attempt; checking group state")
                awaitP2pDisconnect(manager, attempt)
            }
        }
        try {
            manager.removeGroup { success ->
                if (resultHandled.compareAndSet(false, true)) {
                    handleResult(success)
                } else {
                    Log.d(TAG, "Ignoring late OTA P2P removal callback for attempt $attempt")
                }
            }
        } catch (e: Exception) {
            if (resultHandled.compareAndSet(false, true)) {
                Log.w(TAG, "OTA P2P removal threw on attempt $attempt; checking group state", e)
                awaitP2pDisconnect(manager, attempt)
            }
        }
    }

    private fun awaitP2pDisconnect(manager: WifiP2pManagerSingleton, attempt: Int) {
        scope.launch {
            val deadline = System.currentTimeMillis() + P2P_GROUP_DISCONNECT_TIMEOUT_MS
            while (cleanupInProgress && System.currentTimeMillis() < deadline) {
                manager.requestConnectionInfo()
                delay(250)
                if (!manager.isConnecting() && !manager.isConnected()) {
                    Log.i(TAG, "Confirmed OTA P2P group is gone after cleanup attempt $attempt")
                    finishCleanup()
                    return@launch
                }
            }

            if (cleanupInProgress) {
                if (!manager.canUseP2p() || attempt >= P2P_GROUP_REMOVAL_MAX_ATTEMPTS) {
                    cleanupRetryPending = true
                    Log.e(TAG, "OTA P2P teardown could not be confirmed; keeping the OTA lease quarantined until a cleanup retry")
                    // Keep retrying from the manager scope so an Activity recreation cannot
                    // strand the exclusive lease or leave the P2P group owned indefinitely.
                    scheduleP2pRemovalRetry(
                        manager = manager,
                        attempt = 1,
                        delayMs = P2P_QUARANTINE_RETRY_MS,
                    )
                } else {
                    Log.w(TAG, "OTA P2P group still present after cleanup attempt $attempt; retaining the OTA lease and retrying")
                    scheduleP2pRemovalRetry(manager, attempt + 1)
                }
            }
        }
    }

    private fun retryP2pCleanupIfNeeded() {
        if (!cleanupInProgress || !cleanupRetryPending) return
        val manager = p2pManager ?: return
        cleanupRetryJob?.cancel()
        cleanupRetryJob = null
        cleanupRetryPending = false
        Log.i(TAG, "Retrying quarantined OTA P2P teardown")
        removeP2pGroup(manager, attempt = 1)
    }

    private fun scheduleP2pRemovalRetry(
        manager: WifiP2pManagerSingleton,
        attempt: Int,
        delayMs: Long = P2P_GROUP_REMOVAL_RETRY_MS,
    ) {
        cleanupRetryJob?.cancel()
        cleanupRetryJob = scope.launch {
            delay(delayMs)
            cleanupRetryJob = null
            if (cleanupInProgress && !sessionFinishNotified) {
                cleanupRetryPending = false
                removeP2pGroup(manager, attempt)
            }
        }
    }

    private fun finishCleanup() {
        if (!cleanupInProgress) return
        val manager = p2pManager
        val retainSession = retainSessionAfterCleanup
        retainSessionAfterCleanup = false
        p2pCallback?.let { callback -> manager?.removeCallback(callback) }
        if (p2pReceiverRegistered) {
            try {
                manager?.unregisterReceiver()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to unregister OTA P2P receiver: ${e.message}")
            }
            p2pReceiverRegistered = false
        }
        if (otaNotifyRegistered) {
            try {
                LargeDataHandler.getInstance().removeOutDeviceListener(OTA_NOTIFY_CMD_TYPE)
            } catch (_: Exception) {
            }
            otaNotifyRegistered = false
        }
        p2pManager = null
        p2pCallback = null
        otaNotifyListener = null
        p2pConnected = false
        p2pInfo = null
        WifiP2pManagerSingleton.lastGroupOwnerIp = null
        cleanupInProgress = false
        cleanupRetryPending = false
        cleanupRetryJob?.cancel()
        cleanupRetryJob = null
        restoreDefaultNetwork()

        val completion = cleanupCompletion
        cleanupCompletion = null
        if (retainSession) {
            completion?.complete(Unit)
            return
        }

        releaseOtaWakeLock()
        notifySessionFinished()
        if (resetUiAfterCleanup) {
            resetUiAfterCleanup = false
            _uiState.value = OtaUiState()
        }
        completion?.complete(Unit)
    }

    private fun restoreDefaultNetwork() {
        val connectivityManager =
            context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return
        try {
            connectivityManager.bindProcessToNetwork(null)
            Log.i(TAG, "Restored the default process network after OTA P2P teardown")
        } catch (error: RuntimeException) {
            Log.w(TAG, "Could not restore the default process network after OTA P2P teardown", error)
        }
    }

    /** Match the official activity's wake protection while either OTA transport owns the device. */
    private fun acquireOtaWakeLock() {
        if (otaWakeLock?.isHeld == true) return
        val powerManager = context.applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return
        try {
            otaWakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "ADGlasses:Ota",
            ).apply {
                setReferenceCounted(false)
                acquire(OTA_WAKE_LOCK_TIMEOUT_MS)
            }
        } catch (error: SecurityException) {
            Log.w(TAG, "Could not acquire OTA wake lock", error)
            otaWakeLock = null
        }
    }

    private fun releaseOtaWakeLock() {
        val wakeLock = otaWakeLock ?: return
        otaWakeLock = null
        try {
            if (wakeLock.isHeld) wakeLock.release()
        } catch (error: RuntimeException) {
            Log.w(TAG, "Could not release OTA wake lock", error)
        }
    }

    private fun notifySessionFinished() {
        if (sessionFinishNotified) return
        sessionFinishNotified = true
        onSessionFinished()
    }

    private suspend fun runWifiOta(swuFile: File): Boolean {
        val otaStartTime = System.currentTimeMillis()
        Log.i(TAG, "========== WIFI OTA FLOW START ==========")
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
                return false
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

            if (!hasWifiP2pPermission()) {
                Log.e(TAG, "[Step 1/7] FAIL: Required Wi-Fi Direct permission is missing")
                updateState(OtaState.FAILED, error = "Grant Nearby devices or Location permission before Wi-Fi OTA")
                return false
            }
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wifiManager?.isWifiEnabled != true) {
                Log.e(TAG, "[Step 1/7] FAIL: Wi-Fi is disabled")
                updateState(OtaState.FAILED, error = "Enable Wi-Fi before Wi-Fi OTA")
                return false
            }

            // Register before the OTA-mode command so an immediate 0x08 IP notify is not lost.
            if (!registerOtaNotifyListener()) {
                Log.e(TAG, "[Step 1/7] FAIL: Could not register the OTA BLE notify listener")
                updateState(OtaState.FAILED, error = "Could not receive OTA P2P notifications")
                return false
            }

            // Step 2: Enter OTA mode
            Log.i(TAG, "[Step 2/7] Sending OTA mode command (glassesControl {0x02, 0x01, 0x05})...")
            updateState(OtaState.ENTERING_OTA_MODE, "Sending OTA mode command...")
            val otaModeStart = System.currentTimeMillis()
            val otaModeOk = enterOtaMode()
            val otaModeElapsed = System.currentTimeMillis() - otaModeStart
            if (!otaModeOk) {
                Log.e(TAG, "[Step 2/7] FAIL: Glasses did not report OTA readiness (${otaModeElapsed}ms)")
                updateState(OtaState.FAILED, error = "Glasses did not report OTA mode ready")
                return false
            }
            Log.i(TAG, "[Step 2/7] OK: OTA mode accepted (${otaModeElapsed}ms)")

            // Step 3: Start P2P
            Log.i(TAG, "[Step 3/7] Starting P2P discovery...")
            updateState(OtaState.STARTING_P2P, "Starting P2P connection...")
            if (!startP2p()) {
                updateState(OtaState.FAILED, error = "Could not start Wi-Fi Direct discovery")
                return false
            }
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
                return false
            }
            Log.i(TAG, "[Step 4/7] OK: Glasses IP=$glassesIp (${bleIpElapsed}ms)")

            Log.i(TAG, "[Step 4/7] Waiting for the Android P2P group before selecting the phone address...")
            if (!waitForP2pConnection(P2P_CONNECT_TIMEOUT_MS)) {
                Log.e(TAG, "[Step 4/7] FAIL: BLE reported $glassesIp but the P2P group did not connect")
                updateState(OtaState.FAILED, error = "Glasses reported an IP, but Wi-Fi Direct did not connect")
                return false
            }

            // Step 5: Start HTTP server
            val localIp = waitForLocalP2pIp(glassesIp, LOCAL_P2P_IP_TIMEOUT_MS)
            if (localIp == null) {
                Log.e(TAG, "[Step 5/7] FAIL: Could not identify the phone's local P2P address for glasses IP $glassesIp")
                updateState(OtaState.FAILED, error = "Could not identify the phone's Wi-Fi Direct address")
                return false
            }
            Log.i(TAG, "[Step 5/7] Starting HTTP server on $localIp:$HTTP_PORT...")
            updateState(OtaState.STARTING_HTTP_SERVER, "Starting HTTP server on $localIp:$HTTP_PORT...")
            httpServer = OtaHttpServer(HTTP_PORT)
            withContext(Dispatchers.IO) {
                httpServer!!.start(swuFile, localIp)
            }
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
                Log.i(TAG, "[Step 7/7] OK: Wi-Fi OTA complete! (${downloadElapsed}ms download, ${totalElapsed}ms total)")
                Log.i(TAG, "========== WIFI OTA FLOW SUCCESS ==========")
                return true
            } else {
                Log.e(TAG, "[Step 7/7] FAIL: OTA failed or timed out (${downloadElapsed}ms)")
                Log.e(TAG, "  otaComplete=$otaComplete, otaFailed=$otaFailed")
                Log.e(TAG, "========== OTA FLOW FAILED ==========")
                updateState(OtaState.FAILED, error = "Wi-Fi OTA failed or timed out during download/flash")
                return false
            }
        } catch (e: CancellationException) {
            Log.i(TAG, "========== OTA FLOW CANCELLED ==========")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "========== OTA FLOW EXCEPTION ==========", e)
            Log.e(TAG, "  ${e.javaClass.simpleName}: ${e.message}")
            updateState(OtaState.FAILED, error = "OTA failed: ${e.message}")
            return false
        }
    }

    /**
     * BLE DFU OTA flow for the JieLi chip (.bin firmware).
     * Uses DfuHandle from the glasses SDK — no P2P, no HTTP, pure BLE.
     */
    private suspend fun runBleDfu(binFile: File): Boolean {
        val startTime = System.currentTimeMillis()
        Log.i(TAG, "========== BLE DFU FLOW START ==========")
        Log.i(TAG, "  BIN file: ${binFile.absolutePath}")
        Log.i(TAG, "  BIN size: ${binFile.length()} bytes (${binFile.length() / 1024} KB)")

        try {
            // Step 1: Check BLE connection
            Log.i(TAG, "[Step 1/2] Checking BLE connection...")
            updateState(OtaState.CHECKING_CONNECTION, "Checking BLE connection...")
            if (!BleOperateManager.getInstance().isConnected) {
                Log.e(TAG, "[Step 1/2] FAIL: BLE not connected")
                updateState(OtaState.FAILED, error = "Bluetooth not connected to glasses")
                return false
            }

            val deviceName = try {
                DeviceManager.getInstance().deviceName ?: "unknown"
            } catch (_: Exception) {
                "unknown"
            }
            Log.i(TAG, "[Step 1/2] OK: Connected to $deviceName")

            // Step 2: Start BLE DFU transfer
            Log.i(TAG, "[Step 2/2] Starting BLE DFU transfer...")
            updateState(OtaState.BLE_DFU_TRANSFERRING, "Transferring firmware via BLE...")

            val dfuManager = BleDfuManager()
            bleDfuManager = dfuManager

            val result = suspendCancellableCoroutine<Pair<Boolean, String?>> { cont ->
                cont.invokeOnCancellation { dfuManager.cancel() }
                dfuManager.startDfu(
                    binFile = binFile,
                    onProgress = { percent ->
                        updateState(
                            OtaState.BLE_DFU_TRANSFERRING,
                            "Stage 2/2: BLE transfer: $percent%",
                            progress = 50 + (percent.coerceIn(0, 100) / 2),
                        )
                    },
                    onComplete = {
                        if (cont.isActive) cont.resume(true to null) {}
                    },
                    onError = { msg ->
                        Log.e(TAG, "BLE DFU error: $msg")
                        if (cont.isActive) cont.resume(false to msg) {}
                    },
                )
            }

            val elapsed = System.currentTimeMillis() - startTime
            if (result.first) {
                Log.i(TAG, "[Step 2/2] OK: BLE DFU complete! (${elapsed}ms)")
                Log.i(TAG, "========== BLE DFU FLOW SUCCESS ==========")
                return true
            } else {
                Log.e(TAG, "[Step 2/2] FAIL: BLE DFU failed (${elapsed}ms)")
                Log.e(TAG, "========== BLE DFU FLOW FAILED ==========")
                updateState(OtaState.FAILED, error = result.second ?: "BLE DFU transfer failed")
                return false
            }
        } catch (e: CancellationException) {
            Log.i(TAG, "========== BLE DFU FLOW CANCELLED ==========")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "========== BLE DFU FLOW EXCEPTION ==========", e)
            updateState(OtaState.FAILED, error = "BLE DFU failed: ${e.message}")
            return false
        }
    }

    /**
     * Send glassesControl({2,1,5}) to enter OTA mode.
     * The vendor parser reports OTA readiness as otaStatus==1 for glassWorkType 5.
     */
    private suspend fun enterOtaMode(): Boolean = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { cont ->
            LargeDataHandler.getInstance().glassesControl(
                byteArrayOf(0x02, 0x01, 0x05)
            ) { _, response ->
                if (cont.isActive) {
                    val ready = isWifiOtaModeReady(
                        dataType = response.dataType,
                        glassWorkType = response.glassWorkType,
                        otaStatus = response.otaStatus,
                    )
                    Log.i(
                        TAG,
                        "glassesControl OTA response: dataType=${response.dataType}, " +
                            "glassWorkType=${response.glassWorkType}, errorCode=${response.errorCode}, " +
                            "otaStatus=${response.otaStatus}, workTypeIng=${response.workTypeIng}, ready=$ready",
                    )
                    cont.resume(ready) {}
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
    private fun startP2p(): Boolean {
        p2pManager = WifiP2pManagerSingleton.getInstance(context)

        p2pCallback = object : WifiP2pManagerSingleton.WifiP2pCallback {
            override fun onWifiP2pEnabled() {
                Log.i(TAG, "P2P enabled")
            }

            override fun onWifiP2pDisabled() {
                Log.w(TAG, "P2P disabled")
            }

            override fun onPeersChanged(peers: Collection<WifiP2pDevice>) {
                if (cleanupInProgress) {
                    Log.d(TAG, "Ignoring P2P peers during OTA teardown")
                    return
                }
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
                if (cleanupInProgress && !info.groupFormed) {
                    Log.i(TAG, "Confirmed OTA P2P group removal from connection info")
                    finishCleanup()
                }
            }

            override fun onDisconnected() {
                Log.w(TAG, "P2P disconnected")
                p2pConnected = false
                if (cleanupInProgress) {
                    finishCleanup()
                }
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

        val manager = p2pManager ?: return false
        val callback = p2pCallback ?: return false
        manager.addCallback(callback)
        try {
            manager.registerReceiver()
            p2pReceiverRegistered = true
        } catch (e: Exception) {
            manager.removeCallback(callback)
            Log.e(TAG, "Failed to register OTA P2P receiver: ${e.message}", e)
            return false
        }
        manager.resetFailCount()
        manager.startPeerDiscovery()
        return true
    }

    private fun hasWifiP2pPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.NEARBY_WIFI_DEVICES
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    /** Register for OTA notifications before the mode command can emit a P2P IP. */
    private fun registerOtaNotifyListener(): Boolean {
        if (otaNotifyRegistered) return true
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
                        // 255 is a noisy vendor notification; other error codes are fatal.
                        if (errorCode != 255) otaFailed = true
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
                        val result = load.getOrNull(7)?.let(ByteUtil::byteToInt)
                        if (isWifiOtaCompleteNotification(notifyType = 0x07, result = result)) {
                            Log.i(TAG, "[BLE] OTA COMPLETE notification received!")
                            otaComplete = true
                        } else {
                            // The official app accepts type 0x07 only when loadData[7] == 1.
                            Log.w(TAG, "[BLE] OTA completion notification was not successful: result=$result")
                            otaFailed = true
                        }
                    }
                    else -> {
                        Log.d(TAG, "[BLE] Unknown notify type: 0x${load[6].toString(16)} (${load.size} bytes)")
                    }
                }
            }
        }

        val listener = otaNotifyListener ?: return false
        return try {
            LargeDataHandler.getInstance().addOutDeviceListener(OTA_NOTIFY_CMD_TYPE, listener)
            otaNotifyRegistered = true
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register OTA notify listener: ${e.message}")
            false
        }
    }

    /** Wait for the already-registered glasses P2P IP notification (type 0x08). */
    private suspend fun waitForBleIp(timeoutMs: Long): String? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (otaFailed) return null
            val ip = bleIpReceived
            if (ip != null) return ip
            delay(500)
        }
        return null
    }

    private suspend fun waitForP2pConnection(timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (otaFailed) return false
            if (p2pConnected) return true
            delay(250)
        }
        return false
    }

    private suspend fun waitForLocalP2pIp(glassesIp: String, timeoutMs: Long): String? {
        val deadline = System.currentTimeMillis() + timeoutMs
        do {
            if (otaFailed) return null
            getLocalP2pIp(glassesIp)?.let { return it }
            delay(250)
        } while (System.currentTimeMillis() < deadline)
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
    private fun getLocalP2pIp(glassesIp: String): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            val candidates = mutableListOf<Pair<String, String>>()
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue
                for (addr in intf.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val ip = addr.hostAddress ?: continue
                        candidates += intf.name to ip
                    }
                }
            }
            val selected = selectLocalP2pIpv4(glassesIp, candidates)
            Log.i(TAG, "P2P local IP candidates=$candidates, glassesIp=$glassesIp, selected=$selected")
            return selected
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get local P2P IP: ${e.message}")
        }
        return null
    }

    private fun validateFirmwarePair(wifiFile: File, bleFile: File): String? {
        if (!wifiFile.isFile || !wifiFile.canRead() || wifiFile.length() <= 0L) {
            return "Wi-Fi OTA requires a readable, non-empty .swu file"
        }
        if (!bleFile.isFile || !bleFile.canRead() || bleFile.length() <= 0L) {
            return "BLE OTA requires a readable, non-empty .bin file"
        }
        if (!isCombinedOtaFilenamePair(wifiFile.name, bleFile.name)) {
            return "Combined OTA requires one .swu Wi-Fi image and one .bin BLE image"
        }
        return null
    }

    private fun updateState(
        state: OtaState,
        detail: String = "",
        error: String? = null,
        progress: Int? = null,
    ) {
        _uiState.value = OtaUiState(
            state = state,
            detail = detail,
            error = error,
            progress = progress ?: when (state) {
                OtaState.COMPLETE -> 100
                OtaState.FAILED, OtaState.CANCELLING -> null
                else -> _uiState.value.progress.takeIf { state == OtaState.BLE_DFU_TRANSFERRING }
            },
        )
        Log.i(TAG, "State: $state — $detail")
    }

    companion object {
        private const val TAG = "OtaManager"
        private const val HTTP_PORT = 8080
        private const val P2P_CONNECT_TIMEOUT_MS = 40_000L
        private const val LOCAL_P2P_IP_TIMEOUT_MS = 5_000L
        private const val P2P_GROUP_REMOVAL_RETRY_MS = 1_000L
        private const val P2P_QUARANTINE_RETRY_MS = 5_000L
        private const val P2P_GROUP_REMOVE_ACTION_TIMEOUT_MS = 5_000L
        private const val P2P_GROUP_DISCONNECT_TIMEOUT_MS = 5_000L
        private const val P2P_GROUP_REMOVAL_MAX_ATTEMPTS = 3
        private const val P2P_TEARDOWN_TIMEOUT_MS = 25_000L
        // The official OTAActivity uses listener slot 1; slot 2 is used by media/P2P flows.
        private const val OTA_NOTIFY_CMD_TYPE = 1
        private const val OTA_WAKE_LOCK_TIMEOUT_MS = 10 * 60_000L
    }
}

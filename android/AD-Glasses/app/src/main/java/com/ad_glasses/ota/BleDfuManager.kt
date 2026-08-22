package com.ad_glasses.ota

import android.util.Log
import com.oudmon.ble.base.bluetooth.BleOperateManager
import com.oudmon.ble.base.communication.DfuHandle
import java.io.File

/** Small testable surface for the vendor [DfuHandle] state machine. */
internal interface DfuTransport {
    interface Callback {
        fun onActionResult(type: Int, errorCode: Int)
        fun onProgress(percent: Int)
    }

    fun initCallback()
    fun checkFile(path: String): Boolean
    fun start(callback: Callback)
    fun init()
    fun sendPacket()
    fun check()
    fun endAndRelease()
    fun clearCallback()
}

private class VendorDfuTransport(
    private val handle: DfuHandle,
) : DfuTransport {
    override fun initCallback() = handle.initCallback()

    override fun checkFile(path: String): Boolean = handle.checkFile(path)

    override fun start(callback: DfuTransport.Callback) {
        handle.start(object : DfuHandle.IOpResult {
            override fun onActionResult(type: Int, errCode: Int) {
                callback.onActionResult(type, errCode)
            }

            override fun onProgress(percent: Int) {
                callback.onProgress(percent)
            }
        })
    }

    override fun init() = handle.init()

    override fun sendPacket() = handle.sendPacket()

    override fun check() = handle.check()

    override fun endAndRelease() = handle.endAndRelease()

    override fun clearCallback() = BleOperateManager.getInstance().setCallback(null)
}

/**
 * Wraps [DfuHandle] for BLE DFU OTA updates to the JieLi chip.
 *
 * The sequence is deliberately the one used by the official app:
 * start → type 1/init → type 2/sendPacket → type 3/check →
 * type 4/endAndRelease → success. Progress is display-only; in particular,
 * progress 100 is not success. Any nonzero device status is terminal.
 */
class BleDfuManager private constructor(
    private val transportFactory: () -> DfuTransport,
    private val isBleConnected: () -> Boolean,
    private val emitLogs: Boolean,
) {
    private val lock = Any()
    private var transport: DfuTransport? = null
    private var phase = Phase.IDLE
    private var terminal = true

    constructor() : this(
        transportFactory = { VendorDfuTransport(DfuHandle.getInstance()) },
        isBleConnected = { BleOperateManager.getInstance().isConnected },
        emitLogs = true,
    )

    /** The test constructor avoids requiring a real vendor BLE singleton. */
    internal constructor(transportFactory: () -> DfuTransport) : this(
        transportFactory = transportFactory,
        isBleConnected = { true },
        emitLogs = false,
    )

    /**
     * Start BLE DFU OTA with the given .bin file.
     * Callbacks are invoked on the BLE callback thread.
     */
    fun startDfu(
        binFile: File,
        onProgress: (percent: Int) -> Unit,
        onComplete: () -> Unit,
        onError: (message: String) -> Unit,
    ) {
        info("========== BLE DFU START ==========")
        info("  File: ${binFile.absolutePath}")
        info("  Size: ${binFile.length()} bytes (${binFile.length() / 1024} KB)")

        if (!isBleConnected()) {
            error("FAIL: BLE not connected")
            onError("Bluetooth not connected to glasses")
            return
        }

        if (!binFile.isFile || !binFile.canRead()) {
            error("FAIL: DFU file is not a readable regular file")
            onError("Invalid DFU file: ${binFile.name}")
            return
        }

        val dfuTransport = try {
            transportFactory()
        } catch (error: Throwable) {
            error("Could not create DFU transport", error)
            onError("Could not initialize BLE DFU: ${error.message ?: error.javaClass.simpleName}")
            return
        }

        if (!beginTransfer(dfuTransport)) {
            warn("Ignoring request to start a second BLE DFU transfer")
            onError("A BLE DFU transfer is already active")
            return
        }

        info("Checking file...")
        val checkOk = try {
            // The official activity installs the vendor callback before checkFile().
            dfuTransport.initCallback()
            dfuTransport.checkFile(binFile.absolutePath)
        } catch (error: Throwable) {
            error("checkFile threw: ${error.message}", error)
            false
        }

        if (!checkOk) {
            error("FAIL: checkFile returned false (file too large or unreadable)")
            fail("Invalid DFU file: ${binFile.name}", onError)
            return
        }

        if (!advance(Phase.CHECKING_FILE, Phase.WAITING_FOR_START)) return
        info("File check passed, starting BLE transfer...")

        try {
            dfuTransport.start(object : DfuTransport.Callback {
                override fun onProgress(percent: Int) {
                    if (isTerminal()) return
                    debug("[BLE DFU] Progress: $percent%")
                    // The vendor emits 100 when the final data packet is acknowledged, before
                    // its type-3 callback. It must not complete the OTA here.
                    onProgress(percent.coerceIn(0, 100))
                }

                override fun onActionResult(type: Int, errorCode: Int) {
                    if (isTerminal()) return
                    info("[BLE DFU] Action result: type=$type status=$errorCode")

                    if (errorCode != 0) {
                        fail("DFU error at type=$type: status=$errorCode", onError)
                        return
                    }

                    when (type) {
                        1 -> runStep(
                            expected = Phase.WAITING_FOR_START,
                            next = Phase.WAITING_FOR_INIT,
                            label = "init",
                            action = dfuTransport::init,
                            onError = onError,
                        )
                        2 -> runStep(
                            expected = Phase.WAITING_FOR_INIT,
                            next = Phase.WAITING_FOR_PACKET_ACK,
                            label = "sendPacket",
                            action = dfuTransport::sendPacket,
                            onError = onError,
                        )
                        3 -> runStep(
                            expected = Phase.WAITING_FOR_PACKET_ACK,
                            next = Phase.WAITING_FOR_CHECK,
                            label = "check",
                            action = dfuTransport::check,
                            onError = onError,
                        )
                        4 -> finishAfterVerification(dfuTransport, onComplete, onError)
                        else -> fail("Unexpected DFU response type=$type", onError)
                    }
                }
            })
        } catch (error: Throwable) {
            fail("Could not start BLE DFU: ${error.message ?: error.javaClass.simpleName}", onError)
        }
    }

    /**
     * Stop reporting an in-progress DFU transfer.
     *
     * DfuHandle exposes no verified abort operation. Its only release method also sends the
     * type-5 finalization command, which the official app issues only after successful type 4.
     * Do not send that command for an incomplete image.
     */
    fun cancel() {
        val transportToClear = synchronized(lock) {
            if (terminal) {
                null
            } else {
                terminal = true
                phase = Phase.CANCELLED
                transport.also { transport = null }
            }
        }
        if (transportToClear != null) {
            clearCallback(transportToClear)
            info("BLE DFU cancelled; no unverified finalization command was sent")
        }
    }

    private fun beginTransfer(dfuTransport: DfuTransport): Boolean = synchronized(lock) {
        if (!terminal && phase != Phase.IDLE) {
            false
        } else {
            terminal = false
            phase = Phase.CHECKING_FILE
            transport = dfuTransport
            true
        }
    }

    private fun advance(expected: Phase, next: Phase): Boolean = synchronized(lock) {
        if (terminal || phase != expected) {
            false
        } else {
            phase = next
            true
        }
    }

    private fun runStep(
        expected: Phase,
        next: Phase,
        label: String,
        action: () -> Unit,
        onError: (String) -> Unit,
    ) {
        if (!advance(expected, next)) {
            if (!isTerminal()) {
                fail("Unexpected DFU response while waiting to $label", onError)
            }
            return
        }
        try {
            info("[BLE DFU] $label()")
            action()
        } catch (error: Throwable) {
            fail("DFU $label failed: ${error.message ?: error.javaClass.simpleName}", onError)
        }
    }

    private fun finishAfterVerification(
        dfuTransport: DfuTransport,
        onComplete: () -> Unit,
        onError: (String) -> Unit,
    ) {
        if (!advance(Phase.WAITING_FOR_CHECK, Phase.FINALIZING)) {
            if (!isTerminal()) {
                fail("Unexpected DFU verification response", onError)
            }
            return
        }

        try {
            // This is intentionally before success, matching OTAActivity$dfuOpResult$1.
            info("[BLE DFU] type=4 OK → endAndRelease()")
            dfuTransport.endAndRelease()
        } catch (error: Throwable) {
            fail("DFU finalization failed: ${error.message ?: error.javaClass.simpleName}", onError)
            return
        }

        val shouldNotify = synchronized(lock) {
            if (terminal) {
                false
            } else {
                terminal = true
                phase = Phase.COMPLETED
                transport = null
                true
            }
        }
        if (shouldNotify) {
            info("========== BLE DFU COMPLETE ==========")
            onComplete()
        }
    }

    private fun fail(message: String, onError: (String) -> Unit) {
        val transportToClear = synchronized(lock) {
            if (terminal) {
                null
            } else {
                terminal = true
                phase = Phase.FAILED
                transport.also { transport = null }
            }
        }
        if (transportToClear != null) {
            clearCallback(transportToClear)
            error(message)
            onError(message)
        }
    }

    private fun clearCallback(dfuTransport: DfuTransport) {
        try {
            dfuTransport.clearCallback()
        } catch (throwable: Throwable) {
            error("Could not clear BLE DFU callback", throwable)
        }
    }

    private fun isTerminal(): Boolean = synchronized(lock) { terminal }

    private fun debug(message: String) {
        if (emitLogs) Log.d(TAG, message)
    }

    private fun info(message: String) {
        if (emitLogs) Log.i(TAG, message)
    }

    private fun warn(message: String) {
        if (emitLogs) Log.w(TAG, message)
    }

    private fun error(message: String, throwable: Throwable? = null) {
        if (!emitLogs) return
        if (throwable == null) {
            Log.e(TAG, message)
        } else {
            Log.e(TAG, message, throwable)
        }
    }

    private enum class Phase {
        IDLE,
        CHECKING_FILE,
        WAITING_FOR_START,
        WAITING_FOR_INIT,
        WAITING_FOR_PACKET_ACK,
        WAITING_FOR_CHECK,
        FINALIZING,
        COMPLETED,
        FAILED,
        CANCELLED,
    }

    companion object {
        private const val TAG = "BleDfuManager"
    }
}

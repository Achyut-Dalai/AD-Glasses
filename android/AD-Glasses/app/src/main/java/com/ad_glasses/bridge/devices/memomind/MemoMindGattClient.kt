package com.ad_glasses.bridge.devices.memomind

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.ad_glasses.bridge.core.GlassesBridgeState
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * GATT client for MemoMind glasses.
 *
 * Provides connection management, command writing, and notification streaming
 * using raw Android BLE APIs. All GATT operations are serialised via a [Mutex]
 * to ensure thread-safe access to the underlying [BluetoothGatt] object.
 *
 * Tag: MemoMindGattClient
 */
class MemoMindGattClient(
    private val context: Context,
) {
    companion object {
        private const val TAG = "MemoMindGattClient"

        /** Enable notifications value for CCCD. */
        private val ENABLE_NOTIFICATION_VALUE: ByteArray = byteArrayOf(0x01, 0x00)

        /** Default ATT MTU – request 512 bytes for larger data transfers. */
        private const val REQUESTED_MTU = 512

        /** Timeout for each GATT operation (connect, discover, write, etc.). */
        private const val GATT_OPERATION_TIMEOUT = 10_000L
    }

    // ------------------------------------------------------------------
    // Observable state
    // ------------------------------------------------------------------

    private val _state = MutableStateFlow<GlassesBridgeState>(GlassesBridgeState.Disconnected)
    /** Current connection state of the GATT client. */
    val state: StateFlow<GlassesBridgeState> = _state.asStateFlow()

    private val _notifications = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    /** Stream of notification data received from the glasses. */
    val notifications: SharedFlow<ByteArray> = _notifications.asSharedFlow()

    /** Discovered GATT services (populated after successful service discovery). */
    @Volatile
    var discoveredServices: List<android.bluetooth.BluetoothGattService> = emptyList()
        private set

    // ------------------------------------------------------------------
    // Internal state
    // ------------------------------------------------------------------

    private val operationMutex = Mutex()

    @Volatile
    private var gatt: BluetoothGatt? = null

    private var commandWriteCharacteristic: BluetoothGattCharacteristic? = null
    private var commandNotifyCharacteristic: BluetoothGattCharacteristic? = null

    /** Flag to avoid re‑entering disconnect logic. */
    private val disconnectInProgress = AtomicBoolean(false)

    /** The remote device we are (or were) connected to. */
    private var remoteDevice: BluetoothDevice? = null

    // Continuation references (guarded by operationMutex).
    // These are set inside suspendCancellableCoroutine blocks and consumed
    // in the BluetoothGattCallback. They are nullable because on some devices
    // callbacks may fire after we have moved on.
    private var connectContinuation: CancellableContinuation<Unit>? = null
    private var disconnectContinuation: CancellableContinuation<Unit>? = null
    private var discoverServicesContinuation: CancellableContinuation<Unit>? = null
    private var cccdWriteContinuation: CancellableContinuation<Unit>? = null
    private var mtuRequestContinuation: CancellableContinuation<Unit>? = null

    /** Deferred that completes when [onCharacteristicWrite] fires. */
    private var pendingWriteDeferred: CompletableDeferred<Boolean>? = null

    // ------------------------------------------------------------------
    // GATT callback
    // ------------------------------------------------------------------

    @SuppressLint("MissingPermission")
    private val gattCallback: BluetoothGattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(
            gatt: BluetoothGatt,
            status: Int,
            newState: Int,
        ) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i(TAG, "GATT connected (status=$status)")
                    remoteDevice = gatt.device
                    resumeContinuation(connectContinuation)
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.i(TAG, "GATT disconnected (status=$status)")
                    remoteDevice = null

                    // Clear all GATT resources, characteristic refs, and stale services.
                    cleanup()

                    // If we were mid-disconnect, resume that continuation.
                    resumeContinuation(disconnectContinuation)

                    // If still in connecting state, fail the connect continuation.
                    if (_state.value is GlassesBridgeState.Connecting) {
                        resumeContinuationWithException(
                            connectContinuation,
                            IOException("Disconnected during connection (status=$status)"),
                        )
                    }

                    _state.value = GlassesBridgeState.Disconnected
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Services discovered (${gatt.services.size} services)")
                discoveredServices = gatt.services.toList()
                resolveCharacteristics(gatt)
                resumeContinuation(discoverServicesContinuation)
            } else {
                Log.w(TAG, "Service discovery failed with status=$status")
                resumeContinuationWithException(
                    discoverServicesContinuation,
                    IOException("Service discovery failed with status=$status"),
                )
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            val data = characteristic.value ?: ByteArray(0)
            Log.v(TAG, "Notification from ${characteristic.uuid}: ${data.size} bytes")
            _notifications.tryEmit(data)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            val success = status == BluetoothGatt.GATT_SUCCESS
            if (!success) {
                Log.w(TAG, "Characteristic write failed uuid=${characteristic.uuid} status=$status")
            }
            // Resume the pending writeCommand() coroutine.
            pendingWriteDeferred?.let {
                pendingWriteDeferred = null
                it.complete(success)
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Descriptor write succeeded: ${descriptor.uuid}")
                resumeContinuation(cccdWriteContinuation)
            } else {
                Log.w(TAG, "Descriptor write failed: ${descriptor.uuid} status=$status")
                resumeContinuationWithException(
                    cccdWriteContinuation,
                    IOException("CCCD write failed with status=$status"),
                )
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "MTU negotiated: $mtu")
            } else {
                Log.w(TAG, "MTU request failed with status=$status (defaulting to 23)")
            }
            // Resume the continuation regardless – MTU increase is best-effort.
            resumeContinuation(mtuRequestContinuation)
        }
    }

    // ------------------------------------------------------------------
    // Public methods
    // ------------------------------------------------------------------

    /**
     * Connect to a MemoMind glasses device by [address] (MAC).
     *
     * The full connection sequence is:
     * 1. Open GATT connection
     * 2. Discover services
     * 3. Locate command write (0x2001) and notify (0x2002) characteristics
     * 4. Enable notifications on 0x2002 (write 0x0001 to CCCD)
     * 5. Request MTU 512 (best-effort)
     *
     * All steps are serialised by [operationMutex].
     */
    @SuppressLint("MissingPermission")
    suspend fun connect(address: String): Result<Unit> {
        if (!hasConnectPermission()) {
            return Result.failure(SecurityException("BLUETOOTH_CONNECT permission is required"))
        }
        return try {
            operationMutex.withLock {
                if (_state.value is GlassesBridgeState.Connected) {
                    Log.i(TAG, "Already connected")
                    return@withLock
                }

                _state.value = GlassesBridgeState.Connecting

                val manager = context.getSystemService(Context.BLUETOOTH_SERVICE)
                    as? android.bluetooth.BluetoothManager
                    ?: throw IOException("BluetoothManager not available")

                val adapter = manager.adapter
                    ?: throw IOException("BluetoothAdapter not available")

                val device = adapter.getRemoteDevice(address)
                    ?: throw IOException("Invalid Bluetooth address: $address")

                remoteDevice = device

                // --- Step 1: Open GATT connection (with 10s timeout) ---
                Log.i(TAG, "Connecting GATT to $address…")
                try {
                    withTimeout(GATT_OPERATION_TIMEOUT) {
                        suspendCancellableCoroutine<Unit> { cont ->
                            connectContinuation = cont
                            cont.invokeOnCancellation {
                                connectContinuation = null
                                cleanup()
                            }
                            // connectGatt returns immediately; the callback signals completion.
                            val gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
                            } else {
                                @Suppress("DEPRECATION")
                                device.connectGatt(context, false, gattCallback)
                            }
                            this@MemoMindGattClient.gatt = gatt
                            if (gatt == null) {
                                cont.resumeWithException(IOException("connectGatt returned null"))
                            }
                        }
                    }
                } catch (_: TimeoutCancellationException) {
                    // invokeOnCancellation above already called cleanup()
                    throw IOException("GATT connection timed out after ${GATT_OPERATION_TIMEOUT}ms")
                }

                // --- Step 2: Discover services (with 10s timeout) ---
                Log.i(TAG, "Discovering services…")
                val gattObj = this@MemoMindGattClient.gatt
                    ?: throw IOException("GATT object is null during service discovery")
                try {
                    withTimeout(GATT_OPERATION_TIMEOUT) {
                        suspendCancellableCoroutine<Unit> { cont ->
                            discoverServicesContinuation = cont
                            cont.invokeOnCancellation {
                                discoverServicesContinuation = null
                                cleanup()
                            }
                            if (!gattObj.discoverServices()) {
                                cont.resumeWithException(IOException("discoverServices() returned false"))
                            }
                        }
                    }
                } catch (_: TimeoutCancellationException) {
                    cleanup()
                    throw IOException("Service discovery timed out after ${GATT_OPERATION_TIMEOUT}ms")
                }

                // --- Step 3: Locate characteristics ---
                val writeChar = commandWriteCharacteristic
                    ?: throw IOException("Command write characteristic (0x2001) not found")
                val notifyChar = commandNotifyCharacteristic
                    ?: throw IOException("Command notify characteristic (0x2002) not found")

                // --- Step 4: Enable notifications (with 10s timeout) ---
                Log.i(TAG, "Enabling notifications on 0x2002…")
                val cccdDescriptor = notifyChar.getDescriptor(MemoMindConstants.CCCD_UUID)
                    ?: throw IOException("CCCD descriptor not found on notify characteristic")
                try {
                    withTimeout(GATT_OPERATION_TIMEOUT) {
                        suspendCancellableCoroutine<Unit> { cont ->
                            cccdWriteContinuation = cont
                            cont.invokeOnCancellation {
                                cccdWriteContinuation = null
                                cleanup()
                            }
                            gattObj.setCharacteristicNotification(notifyChar, true)
                            cccdDescriptor.value = ENABLE_NOTIFICATION_VALUE
                            if (!gattObj.writeDescriptor(cccdDescriptor)) {
                                cont.resumeWithException(IOException("writeDescriptor(CCCD) returned false"))
                            }
                        }
                    }
                } catch (_: TimeoutCancellationException) {
                    cleanup()
                    throw IOException("CCCD write timed out after ${GATT_OPERATION_TIMEOUT}ms")
                }

                // --- Step 5: Request MTU (best-effort, with timeout) ---
                Log.i(TAG, "Requesting MTU $REQUESTED_MTU…")
                try {
                    withTimeout(GATT_OPERATION_TIMEOUT) {
                        suspendCancellableCoroutine<Unit> { cont ->
                            mtuRequestContinuation = cont
                            cont.invokeOnCancellation {
                                mtuRequestContinuation = null
                            }
                            if (!gattObj.requestMtu(REQUESTED_MTU)) {
                                // Non-fatal – continue with default MTU.
                                Log.w(TAG, "requestMtu() returned false")
                                cont.resume(Unit)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "MTU request failed (non-fatal)", e)
                }

                _state.value = GlassesBridgeState.Connected
                Log.i(TAG, "GATT connection fully established")
            }
            Result.success(Unit)
        } catch (e: IOException) {
            Log.e(TAG, "connect() failed", e)
            if (_state.value is GlassesBridgeState.Connecting) {
                _state.value = GlassesBridgeState.Error(e.message ?: "Connection failed")
            }
            Result.failure(e)
        }
    }

    /**
     * Disconnect from the glasses and release GATT resources.
     */
    @SuppressLint("MissingPermission")
    suspend fun disconnect() {
        if (disconnectInProgress.getAndSet(true)) return
        try {
            operationMutex.withLock {
                val currentGatt = gatt
                if (currentGatt == null) {
                    _state.value = GlassesBridgeState.Disconnected
                    return@withLock
                }

                Log.i(TAG, "Disconnecting GATT…")
                try {
                    withTimeout(GATT_OPERATION_TIMEOUT) {
                        suspendCancellableCoroutine<Unit> { cont ->
                            disconnectContinuation = cont
                            cont.invokeOnCancellation {
                                disconnectContinuation = null
                            }
                            currentGatt.disconnect()
                            // The onConnectionStateChange callback will close() and resume.
                        }
                    }
                } catch (_: TimeoutCancellationException) {
                    Log.w(TAG, "Disconnect timed out after ${GATT_OPERATION_TIMEOUT}ms, force-cleaning")
                    cleanup()
                }
            }
        } finally {
            disconnectInProgress.set(false)
        }
    }

    /**
     * Write a command payload to the command write characteristic (0x2001).
     *
     * Returns [Result.success] if the write was enqueued, or [Result.failure]
     * if not connected or the characteristic is missing.
     */
    @SuppressLint("MissingPermission")
    suspend fun writeCommand(data: ByteArray): Result<Unit> {
        if (!hasConnectPermission()) {
            return Result.failure(SecurityException("BLUETOOTH_CONNECT permission is required"))
        }
        val char = commandWriteCharacteristic
        if (char == null || gatt == null) {
            return Result.failure(IOException("Not connected – cannot write"))
        }
        return operationMutex.withLock {
            val gattObj = gatt ?: return@withLock Result.failure(IOException("GATT closed"))
            try {
                char.value = data
                char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

                // Create a deferred that will be completed in onCharacteristicWrite.
                val deferred = CompletableDeferred<Boolean>()
                pendingWriteDeferred = deferred

                val enqueued = gattObj.writeCharacteristic(char)
                if (!enqueued) {
                    pendingWriteDeferred = null
                    Log.w(TAG, "writeCommand: writeCharacteristic returned false")
                    return@withLock Result.failure(IOException("writeCharacteristic returned false"))
                }

                // Wait for the callback with a timeout (the mutex is held throughout).
                val success = withTimeout(GATT_OPERATION_TIMEOUT) { deferred.await() }

                if (success) {
                    Log.d(TAG, "writeCommand: ${data.size} bytes written")
                    Result.success(Unit)
                } else {
                    Log.w(TAG, "writeCommand: characteristic write reported failure")
                    Result.failure(IOException("Characteristic write failed"))
                }
            } catch (e: TimeoutCancellationException) {
                pendingWriteDeferred = null
                Log.e(TAG, "writeCommand timed out after ${GATT_OPERATION_TIMEOUT}ms", e)
                Result.failure(IOException("Write timed out after ${GATT_OPERATION_TIMEOUT}ms"))
            } catch (e: Exception) {
                pendingWriteDeferred = null
                Log.e(TAG, "writeCommand failed", e)
                Result.failure(e)
            }
        }
    }

    private fun hasConnectPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT,
            ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Returns true if the client is in the connected state.
     */
    fun isConnected(): Boolean = _state.value is GlassesBridgeState.Connected

    /**
     * Force-close the GATT connection and release all resources.
     * Non-suspend version for use in lifecycle cleanup (e.g. [MemoMindDeviceAdapter.destroy]).
     */
    fun close() {
        cleanup()
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    /**
     * Find the command write (0x2001) and notify (0x2002) characteristics
     * across all discovered services.
     */
    private fun resolveCharacteristics(gatt: BluetoothGatt) {
        for (service in gatt.services) {
            for (char in service.characteristics) {
                when (char.uuid) {
                    MemoMindConstants.COMMAND_WRITE_UUID -> {
                        commandWriteCharacteristic = char
                        Log.i(TAG, "Found command write char in service ${service.uuid}")
                    }
                    MemoMindConstants.COMMAND_NOTIFY_UUID -> {
                        commandNotifyCharacteristic = char
                        Log.i(TAG, "Found command notify char in service ${service.uuid}")
                    }
                }
            }
        }
        if (commandWriteCharacteristic == null) {
            Log.w(TAG, "Command write characteristic (0x2001) NOT found")
        }
        if (commandNotifyCharacteristic == null) {
            Log.w(TAG, "Command notify characteristic (0x2002) NOT found")
        }
    }

    /**
     * Release GATT resources and reset internal state.
     */
    private fun cleanup() {
        try {
            gatt?.close()
        } catch (_: SecurityException) {
            // BLUETOOTH_CONNECT may be revoked while lifecycle cleanup is running.
        } catch (_: Exception) {
        }
        gatt = null
        commandWriteCharacteristic = null
        commandNotifyCharacteristic = null
        discoveredServices = emptyList()
        _state.value = GlassesBridgeState.Disconnected
    }

    /**
     * Resume a [CancellableContinuation] with [Unit] if it is still active.
     */
    private fun resumeContinuation(cont: CancellableContinuation<Unit>?) {
        cont?.let {
            if (it.isActive) {
                it.resume(Unit)
            }
        }
    }

    /**
     * Resume a [CancellableContinuation] with an exception if it is still active.
     */
    private fun resumeContinuationWithException(
        cont: CancellableContinuation<Unit>?,
        exception: Throwable,
    ) {
        cont?.let {
            if (it.isActive) {
                it.resumeWithException(exception)
            }
        }
    }
}

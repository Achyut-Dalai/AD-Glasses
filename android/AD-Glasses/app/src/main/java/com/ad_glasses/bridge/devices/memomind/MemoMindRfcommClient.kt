package com.ad_glasses.bridge.devices.memomind

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.ad_glasses.bridge.core.GlassesBridgeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * RFCOMM client for MemoMind glasses.
 *
 * Opens the primary SPP socket and best-effort auxiliary sockets observed in the
 * official app, then exposes raw read chunks and complete `fa 00 ...` control frames.
 */
class MemoMindRfcommClient(
    private val context: Context,
) {
    companion object {
        private const val TAG = "MemoMindRfcommClient"
        private const val CONNECT_TIMEOUT_MS = 12_000L
        private const val READ_BUFFER_SIZE = 4096
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val operationMutex = Mutex()

    private val _state = MutableStateFlow<GlassesBridgeState>(GlassesBridgeState.Disconnected)
    val state: StateFlow<GlassesBridgeState> = _state.asStateFlow()

    private val _notifications = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    val notifications: SharedFlow<ByteArray> = _notifications.asSharedFlow()

    private val _controlFrames = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    val controlFrames: SharedFlow<ByteArray> = _controlFrames.asSharedFlow()

    @Volatile
    private var primarySocket: BluetoothSocket? = null

    @Volatile
    private var primaryOutput: OutputStream? = null

    private val sockets = mutableListOf<BluetoothSocket>()
    private val readJobs = mutableListOf<Job>()

    @Volatile
    var connectedChannels: List<String> = emptyList()
        private set

    fun isConnected(): Boolean = _state.value is GlassesBridgeState.Connected && primarySocket?.isConnected == true

    @SuppressLint("MissingPermission")
    suspend fun connect(address: String): Result<Unit> {
        if (!hasConnectPermission()) {
            return Result.failure(SecurityException("BLUETOOTH_CONNECT permission is required"))
        }
        return try {
            operationMutex.withLock {
                if (isConnected()) return@withLock

                disconnectLocked()
                _state.value = GlassesBridgeState.Connecting

                val adapter = bluetoothAdapter() ?: throw IOException("BluetoothAdapter not available")
                val device = adapter.getRemoteDevice(address) ?: throw IOException("Invalid Bluetooth address: $address")

                val openedChannels = mutableListOf<String>()

                val primary = connectSocket(device, MemoMindConstants.SPP_UUID, "primary")
                primarySocket = primary
                primaryOutput = primary.outputStream
                sockets += primary
                openedChannels += "primary:${MemoMindConstants.SPP_UUID}"
                startReader("primary", primary.inputStream)

                connectOptionalSocket(device, MemoMindConstants.EXTRA_RFCOMM_UUID, "extra")?.let { socket ->
                    sockets += socket
                    openedChannels += "extra:${MemoMindConstants.EXTRA_RFCOMM_UUID}"
                    startReader("extra", socket.inputStream)
                }

                connectOptionalSocket(device, MemoMindConstants.RECORD_RFCOMM_UUID, "record")?.let { socket ->
                    sockets += socket
                    openedChannels += "record:${MemoMindConstants.RECORD_RFCOMM_UUID}"
                    startReader("record", socket.inputStream)
                }

                connectedChannels = openedChannels
                _state.value = GlassesBridgeState.Connected
            }
            Result.success(Unit)
        } catch (t: Throwable) {
            Log.e(TAG, "RFCOMM connect failed", t)
            disconnect()
            _state.value = GlassesBridgeState.Error("RFCOMM connection failed: ${t.message}", t)
            Result.failure(t)
        }
    }

    suspend fun disconnect() {
        operationMutex.withLock {
            disconnectLocked()
        }
    }

    suspend fun writeCommand(packet: ByteArray): Result<Unit> {
        return try {
            operationMutex.withLock {
                val out = primaryOutput ?: throw IOException("Primary RFCOMM socket is not connected")
                withContext(Dispatchers.IO) {
                    out.write(packet)
                    out.flush()
                }
            }
            Result.success(Unit)
        } catch (t: Throwable) {
            Log.e(TAG, "RFCOMM write failed", t)
            Result.failure(t)
        }
    }

    fun close() {
        scope.cancel()
        try {
            sockets.forEach { it.close() }
        } catch (_: IOException) {
        }
        sockets.clear()
        readJobs.clear()
        primarySocket = null
        primaryOutput = null
        connectedChannels = emptyList()
        _state.value = GlassesBridgeState.Disconnected
    }

    private fun bluetoothAdapter(): BluetoothAdapter? {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return manager?.adapter
    }

    private fun hasConnectPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.BLUETOOTH_CONNECT,
            ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private suspend fun connectSocket(
        device: android.bluetooth.BluetoothDevice,
        uuid: java.util.UUID,
        label: String,
    ): BluetoothSocket {
        val socket = device.createRfcommSocketToServiceRecord(uuid)
        return try {
            withContext(Dispatchers.IO) {
                withTimeout(CONNECT_TIMEOUT_MS) {
                    socket.connect()
                }
            }
            Log.i(TAG, "Connected $label socket: $uuid")
            socket
        } catch (t: Throwable) {
            try {
                socket.close()
            } catch (_: IOException) {
            }
            throw IOException("Failed to connect $label RFCOMM socket $uuid", t)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun connectOptionalSocket(
        device: android.bluetooth.BluetoothDevice,
        uuid: java.util.UUID,
        label: String,
    ): BluetoothSocket? {
        return try {
            connectSocket(device, uuid, label)
        } catch (t: Throwable) {
            Log.w(TAG, "Optional $label socket unavailable: ${t.message}")
            null
        }
    }

    private fun startReader(label: String, inputStream: InputStream) {
        readJobs += scope.launch {
            val buffer = ByteArray(READ_BUFFER_SIZE)
            try {
                while (true) {
                    val read = inputStream.read(buffer)
                    if (read < 0) {
                        Log.i(TAG, "$label socket closed by remote")
                        break
                    }
                    if (read == 0) continue

                    val chunk = buffer.copyOf(read)
                    _notifications.tryEmit(chunk)
                    emitControlFrames(chunk)
                }
            } catch (e: IOException) {
                Log.w(TAG, "$label reader stopped: ${e.message}")
            }
        }
    }

    private fun emitControlFrames(chunk: ByteArray) {
        var offset = 0
        while (offset + 4 <= chunk.size) {
            if (chunk[offset] != 0xFA.toByte() || chunk[offset + 1] != 0x00.toByte()) {
                return
            }

            val frameLength = ((chunk[offset + 2].toInt() and 0xFF) shl 8) or (chunk[offset + 3].toInt() and 0xFF)
            if (frameLength <= 0 || offset + frameLength > chunk.size) {
                return
            }

            _controlFrames.tryEmit(chunk.copyOfRange(offset, offset + frameLength))
            offset += frameLength
        }
    }

    private fun disconnectLocked() {
        readJobs.forEach { it.cancel() }
        readJobs.clear()

        sockets.forEach {
            try {
                it.close()
            } catch (_: IOException) {
            }
        }
        sockets.clear()

        primarySocket = null
        primaryOutput = null
        connectedChannels = emptyList()
        _state.value = GlassesBridgeState.Disconnected
    }
}

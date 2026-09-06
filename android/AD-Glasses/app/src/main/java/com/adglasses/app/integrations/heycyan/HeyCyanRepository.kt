package com.adglasses.app.integrations.heycyan

import android.content.Context
import com.adglasses.app.core.model.ConnectionPhase
import com.adglasses.app.core.model.GlassesConnectionState
import com.adglasses.app.core.model.ScannedGlasses
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
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

class HeyCyanRepository(
    context: Context,
    private val transport: HeyCyanBleTransport,
) {
    companion object {
        private const val PREFS = "heycyan_connection"
        private const val KEY_ADDRESS = "remembered_address"
        private const val REQUEST_TIMEOUT_MS = 6_000L
    }

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val requestMutex = Mutex()
    private val streamDecoder = HeyCyanFrameStreamDecoder()
    private var pending: PendingRequest? = null
    private var reconnectJob: Job? = null
    private var shouldReconnect = true
    private var reconnectAttempt = 0
    private val discoveredByAddress = linkedMapOf<String, ScannedGlasses>()

    private val _state = MutableStateFlow(GlassesConnectionState())
    val state: StateFlow<GlassesConnectionState> = _state.asStateFlow()

    private val _discovered = MutableStateFlow<List<ScannedGlasses>>(emptyList())
    val discovered: StateFlow<List<ScannedGlasses>> = _discovered.asStateFlow()

    private val _events = MutableSharedFlow<HeyCyanDeviceEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<HeyCyanDeviceEvent> = _events.asSharedFlow()

    private val _glassesAudio = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    val glassesAudio: SharedFlow<ByteArray> = _glassesAudio.asSharedFlow()

    private data class PendingRequest(
        val command: HeyCyanCommand,
        val result: CompletableDeferred<HeyCyanFrame>,
    )

    init {
        transport.onEvent = ::handleTransportEvent
    }

    fun scan() {
        discoveredByAddress.clear()
        _discovered.value = emptyList()
        shouldReconnect = false
        transport.startScan()
    }

    fun connect(device: ScannedGlasses) = connect(device.address, device.name)

    fun connect(address: String, name: String? = null) {
        shouldReconnect = true
        reconnectAttempt = 0
        prefs.edit().putString(KEY_ADDRESS, address).apply()
        _state.value = _state.value.copy(
            phase = ConnectionPhase.Connecting,
            address = address,
            deviceName = name ?: _state.value.deviceName,
            detail = null,
        )
        transport.connect(address)
    }

    fun resumeRememberedConnection() {
        val address = prefs.getString(KEY_ADDRESS, null) ?: return
        if (_state.value.phase !in setOf(ConnectionPhase.Disconnected, ConnectionPhase.Error)) return
        shouldReconnect = true
        _state.value = _state.value.copy(
            phase = ConnectionPhase.Connecting,
            address = address,
            detail = "Reconnecting",
        )
        transport.connect(address)
    }

    fun disconnect() {
        shouldReconnect = false
        reconnectJob?.cancel()
        pending?.result?.cancel()
        pending = null
        transport.close()
        _state.value = GlassesConnectionState(
            phase = ConnectionPhase.Disconnected,
            address = prefs.getString(KEY_ADDRESS, null),
        )
    }

    fun forget() {
        disconnect()
        prefs.edit().remove(KEY_ADDRESS).apply()
        _state.value = GlassesConnectionState()
    }

    suspend fun takePhoto(): HeyCyanControlAck =
        HeyCyanResponseDecoder.controlAck(execute(HeyCyanCommand.TakePhoto), 0x01)

    suspend fun startVideo(): HeyCyanControlAck =
        HeyCyanResponseDecoder.controlAck(execute(HeyCyanCommand.StartVideo), 0x02)

    suspend fun stopVideo(): HeyCyanControlAck =
        HeyCyanResponseDecoder.controlAck(execute(HeyCyanCommand.StopVideo), 0x03)

    suspend fun startAudioRecording(): HeyCyanControlAck =
        HeyCyanResponseDecoder.controlAck(execute(HeyCyanCommand.StartAudioRecording), 0x08)

    suspend fun stopAudioRecording(): HeyCyanControlAck =
        HeyCyanResponseDecoder.controlAck(execute(HeyCyanCommand.StopAudioRecording), 0x0C)

    suspend fun requestAiPhoto(quality: Int = 3): HeyCyanControlAck =
        HeyCyanResponseDecoder.controlAck(execute(HeyCyanCommand.AiPhoto(quality)), 0x06)

    suspend fun prepareMedia(
        mode: HeyCyanNetworkMode = HeyCyanNetworkMode.AccessPoint,
    ): HeyCyanNetworkPreparation =
        HeyCyanResponseDecoder.networkPreparation(
            execute(HeyCyanCommand.PrepareMedia(mode), timeoutMs = 12_000),
        )

    suspend fun finishMedia() {
        runCatching {
            HeyCyanResponseDecoder.controlAck(execute(HeyCyanCommand.FinishMedia), 0x09)
        }
    }

    suspend fun execute(
        command: HeyCyanCommand,
        timeoutMs: Long = REQUEST_TIMEOUT_MS,
    ): HeyCyanFrame = requestMutex.withLock {
        check(_state.value.phase in setOf(ConnectionPhase.Ready, ConnectionPhase.Initializing)) {
            "Glasses session is not ready"
        }

        val result = CompletableDeferred<HeyCyanFrame>()
        pending = PendingRequest(command, result)
        val frame = HeyCyanFrameCodec.encode(command.family, command.payload)
        if (!transport.writeLargeData(frame)) {
            pending = null
            error("Could not enqueue the glasses command")
        }

        try {
            withTimeout(timeoutMs) { result.await() }
        } catch (timeout: TimeoutCancellationException) {
            pending = null
            throw IllegalStateException("Timed out waiting for the glasses response", timeout)
        } finally {
            if (pending?.result === result) pending = null
        }
    }

    private fun handleTransportEvent(event: HeyCyanBleTransport.Event) {
        when (event) {
            HeyCyanBleTransport.Event.Scanning -> {
                _state.value = _state.value.copy(phase = ConnectionPhase.Scanning, detail = null)
            }
            is HeyCyanBleTransport.Event.ScanResultFound -> {
                discoveredByAddress[event.device.address] = event.device
                _discovered.value = discoveredByAddress.values.sortedByDescending { it.rssi }
            }
            HeyCyanBleTransport.Event.Connecting -> {
                _state.value = _state.value.copy(phase = ConnectionPhase.Connecting, detail = null)
            }
            HeyCyanBleTransport.Event.Discovering -> {
                _state.value = _state.value.copy(
                    phase = ConnectionPhase.Discovering,
                    detail = "Discovering verified services",
                )
            }
            HeyCyanBleTransport.Event.Ready -> scope.launch { initializeSession() }
            is HeyCyanBleTransport.Event.Bytes -> handleIncomingBytes(event.bytes)
            is HeyCyanBleTransport.Event.Disconnected -> handleDisconnect(event.status)
            is HeyCyanBleTransport.Event.Error -> {
                pending?.result?.completeExceptionally(IllegalStateException(event.message))
                pending = null
                _state.value = _state.value.copy(
                    phase = ConnectionPhase.Error,
                    detail = event.message,
                )
            }
        }
    }

    private suspend fun initializeSession() {
        _state.value = _state.value.copy(
            phase = ConnectionPhase.Initializing,
            detail = "Synchronizing glasses",
        )
        try {
            execute(HeyCyanCommand.SyncTime())
            val battery = HeyCyanResponseDecoder.battery(execute(HeyCyanCommand.Battery))
            execute(HeyCyanCommand.DeviceInfo)
            runCatching { execute(HeyCyanCommand.ReadVolume) }
            execute(HeyCyanCommand.ClassicBluetooth)
            reconnectAttempt = 0
            _state.value = _state.value.copy(
                phase = ConnectionPhase.Ready,
                batteryPercent = battery.level,
                charging = battery.charging,
                detail = null,
            )
        } catch (error: Throwable) {
            _state.value = _state.value.copy(
                phase = ConnectionPhase.Error,
                detail = error.message ?: "Glasses initialization failed",
            )
        }
    }

    private fun handleIncomingBytes(bytes: ByteArray) {
        streamDecoder.append(bytes).forEach { frame ->
            val current = pending
            if (current != null && current.command.matches(frame) && current.result.complete(frame)) {
                pending = null
                return@forEach
            }

            when (frame.command) {
                0x73 -> runCatching {
                    HeyCyanResponseDecoder.deviceEvent(frame)
                }.getOrNull()?.let { event ->
                    if (event is HeyCyanDeviceEvent.Battery) {
                        _state.value = _state.value.copy(
                            batteryPercent = event.status.level,
                            charging = event.status.charging,
                        )
                    }
                    _events.tryEmit(event)
                }
                0x59 -> if (frame.payload.size == 40) {
                    _glassesAudio.tryEmit(frame.payload.copyOf())
                }
            }
        }
    }

    private fun handleDisconnect(status: Int) {
        pending?.result?.completeExceptionally(IllegalStateException("Glasses disconnected"))
        pending = null
        streamDecoder.reset()
        _state.value = _state.value.copy(
            phase = ConnectionPhase.Disconnected,
            detail = if (status == 0) null else "Bluetooth disconnected ($status)",
        )
        if (shouldReconnect) scheduleReconnect()
    }

    private fun scheduleReconnect() {
        val address = prefs.getString(KEY_ADDRESS, null) ?: return
        reconnectJob?.cancel()
        val delays = longArrayOf(2_000, 5_000, 10_000, 20_000, 30_000)
        val delayMs = delays[reconnectAttempt.coerceAtMost(delays.lastIndex)]
        reconnectAttempt = (reconnectAttempt + 1).coerceAtMost(delays.lastIndex)
        reconnectJob = scope.launch {
            delay(delayMs)
            if (shouldReconnect) {
                _state.value = _state.value.copy(
                    phase = ConnectionPhase.Connecting,
                    detail = "Reconnecting",
                )
                transport.connect(address)
            }
        }
    }
}

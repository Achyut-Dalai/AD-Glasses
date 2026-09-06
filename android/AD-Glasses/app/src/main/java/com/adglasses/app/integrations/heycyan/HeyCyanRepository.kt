package com.adglasses.app.integrations.heycyan

import android.content.Context
import android.util.Log
import com.adglasses.app.BuildConfig
import com.adglasses.app.core.model.ConnectionPhase
import com.adglasses.app.core.model.GlassesConnectionState
import com.adglasses.app.core.model.ScannedGlasses
import kotlinx.coroutines.CancellationException
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

sealed interface HeyCyanVoiceStreamEvent {
    data object Started : HeyCyanVoiceStreamEvent
    data class OpusPacket(val bytes: ByteArray) : HeyCyanVoiceStreamEvent
    data object Ended : HeyCyanVoiceStreamEvent
}

class HeyCyanRepository(
    context: Context,
    private val transport: HeyCyanBleTransport,
) {
    companion object {
        private const val PREFS = "heycyan_connection"
        private const val KEY_ADDRESS = "remembered_address"
        private const val REQUEST_TIMEOUT_MS = 10_000L
        private const val TAG = "AD/SESSION"
    }

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val requestMutex = Mutex()
    private val streamDecoder = HeyCyanFrameStreamDecoder()
    private var pending: PendingRequest? = null
    private var reconnectJob: Job? = null
    private var statusRefreshJob: Job? = null
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

    /** Ordered voice lifecycle + audio packets, emitted in protocol receive order. */
    private val _voiceStream = MutableSharedFlow<HeyCyanVoiceStreamEvent>(extraBufferCapacity = 128)
    val voiceStream: SharedFlow<HeyCyanVoiceStreamEvent> = _voiceStream.asSharedFlow()

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
        reconnectJob?.cancel()
        transport.startScan()
    }

    fun connect(device: ScannedGlasses) = connect(device.address, device.name)

    fun connect(address: String, name: String? = null) {
        shouldReconnect = true
        reconnectAttempt = 0
        reconnectJob?.cancel()
        statusRefreshJob?.cancel()
        prefs.edit().putString(KEY_ADDRESS, address).apply()
        _state.value = _state.value.copy(
            phase = ConnectionPhase.Connecting,
            address = address,
            deviceName = name ?: _state.value.deviceName,
            detail = null,
        )
        debug("connect requested name=${name ?: "unknown"}")
        transport.connect(address)
    }

    fun hasRememberedDevice(): Boolean = !prefs.getString(KEY_ADDRESS, null).isNullOrBlank()

    fun resumeRememberedConnection() {
        val address = prefs.getString(KEY_ADDRESS, null) ?: return
        if (_state.value.phase !in setOf(ConnectionPhase.Disconnected, ConnectionPhase.Error)) return
        shouldReconnect = true
        _state.value = _state.value.copy(
            phase = ConnectionPhase.Connecting,
            address = address,
            detail = "Reconnecting",
        )
        debug("reconnecting remembered glasses")
        transport.connect(address)
    }

    fun disconnect() {
        shouldReconnect = false
        reconnectJob?.cancel()
        statusRefreshJob?.cancel()
        pending?.result?.cancel()
        pending = null
        streamDecoder.reset()
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
        check(_state.value.phase == ConnectionPhase.Ready) {
            "Glasses control session is not ready"
        }

        val result = CompletableDeferred<HeyCyanFrame>()
        pending = PendingRequest(command, result)
        val frame = HeyCyanFrameCodec.encode(command.family, command.payload)
        debug("tx family=0x${command.family.toString(16).padStart(2, '0')} payload=${command.payload.size}")
        if (!transport.writeLargeData(frame)) {
            pending = null
            error("Could not enqueue glasses command 0x${command.family.toString(16).padStart(2, '0')}")
        }

        try {
            withTimeout(timeoutMs) { result.await() }
        } catch (timeout: TimeoutCancellationException) {
            pending = null
            val family = command.family.toString(16).padStart(2, '0')
            debug("timeout family=0x$family after ${timeoutMs}ms")
            throw IllegalStateException("Timed out waiting for glasses response to 0x$family", timeout)
        } finally {
            if (pending?.result === result) pending = null
        }
    }

    private fun handleTransportEvent(event: HeyCyanBleTransport.Event) {
        when (event) {
            HeyCyanBleTransport.Event.Scanning -> {
                debug("state=scanning")
                _state.value = _state.value.copy(phase = ConnectionPhase.Scanning, detail = null)
            }
            is HeyCyanBleTransport.Event.ScanResultFound -> {
                discoveredByAddress[event.device.address] = event.device
                _discovered.value = discoveredByAddress.values.sortedByDescending { it.rssi }
            }
            HeyCyanBleTransport.Event.Connecting -> {
                debug("state=connecting")
                _state.value = _state.value.copy(phase = ConnectionPhase.Connecting, detail = null)
            }
            HeyCyanBleTransport.Event.Discovering -> {
                debug("state=discovering")
                _state.value = _state.value.copy(
                    phase = ConnectionPhase.Discovering,
                    detail = "Discovering verified services",
                )
            }
            HeyCyanBleTransport.Event.Ready -> {
                // Match the working iOS boundary: GATT + both verified notification subscriptions
                // define a ready control transport. Status synchronization below is best effort and
                // must never turn a valid BLE link into a fake connection failure.
                reconnectAttempt = 0
                debug("state=ready; starting best-effort status refresh")
                _state.value = _state.value.copy(
                    phase = ConnectionPhase.Ready,
                    detail = null,
                )
                refreshKnownDeviceStatus()
            }
            is HeyCyanBleTransport.Event.Bytes -> when (event.channel) {
                HeyCyanBleTransport.NotificationChannel.LargeData -> handleIncomingBytes(event.bytes)
                HeyCyanBleTransport.NotificationChannel.Primary -> {
                    // The verified 0xBC application framing is carried by the serial/large-data
                    // stream. Keep the independent base notification stream out of its reassembly
                    // buffer exactly as the current iOS transport does.
                    debug("rx primary-unparsed bytes=${event.bytes.size}")
                }
            }
            is HeyCyanBleTransport.Event.Disconnected -> handleDisconnect(event.status)
            is HeyCyanBleTransport.Event.Error -> {
                debug("transport error=${event.message}")
                statusRefreshJob?.cancel()
                pending?.result?.completeExceptionally(IllegalStateException(event.message))
                pending = null
                _state.value = _state.value.copy(
                    phase = ConnectionPhase.Error,
                    detail = event.message,
                )
            }
        }
    }

    private fun refreshKnownDeviceStatus() {
        statusRefreshJob?.cancel()
        statusRefreshJob = scope.launch {
            bestEffortStatus("clock", HeyCyanCommand.SyncTime())
            bestEffortStatus("battery", HeyCyanCommand.Battery) { frame ->
                val battery = HeyCyanResponseDecoder.battery(frame)
                _state.value = _state.value.copy(
                    batteryPercent = battery.level,
                    charging = battery.charging,
                )
            }
            bestEffortStatus("device-info", HeyCyanCommand.DeviceInfo)
            bestEffortStatus("volume", HeyCyanCommand.ReadVolume)
            bestEffortStatus("classic-bluetooth", HeyCyanCommand.ClassicBluetooth)
        }
    }

    private suspend fun bestEffortStatus(
        label: String,
        command: HeyCyanCommand,
        onSuccess: (HeyCyanFrame) -> Unit = {},
    ) {
        if (_state.value.phase != ConnectionPhase.Ready) return
        try {
            val frame = execute(command)
            onSuccess(frame)
            debug("status $label ok")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            debug("status $label failed: ${error.message}")
        }
    }

    private fun handleIncomingBytes(bytes: ByteArray) {
        debug("rx large bytes=${bytes.size}")
        streamDecoder.append(bytes).forEach { frame ->
            debug("frame family=0x${frame.command.toString(16).padStart(2, '0')} payload=${frame.payload.size}")
            val current = pending
            if (current != null && current.command.matches(frame) && current.result.complete(frame)) {
                debug("matched response family=0x${frame.command.toString(16).padStart(2, '0')}")
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
                    when (event) {
                        HeyCyanDeviceEvent.AssistantListeningStarted ->
                            _voiceStream.tryEmit(HeyCyanVoiceStreamEvent.Started)
                        HeyCyanDeviceEvent.AssistantListeningEnded ->
                            _voiceStream.tryEmit(HeyCyanVoiceStreamEvent.Ended)
                        else -> Unit
                    }
                    _events.tryEmit(event)
                }
                0x59 -> if (frame.payload.size == 40) {
                    val packet = frame.payload.copyOf()
                    _glassesAudio.tryEmit(packet)
                    _voiceStream.tryEmit(HeyCyanVoiceStreamEvent.OpusPacket(packet))
                }
            }
        }
    }

    private fun handleDisconnect(status: Int) {
        debug("disconnected status=$status")
        statusRefreshJob?.cancel()
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
        debug("reconnect scheduled in ${delayMs}ms")
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

    private fun debug(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }
}

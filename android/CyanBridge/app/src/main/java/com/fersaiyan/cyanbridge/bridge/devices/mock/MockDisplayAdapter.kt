package com.fersaiyan.cyanbridge.bridge.devices.mock

import android.util.Log
import com.fersaiyan.cyanbridge.bridge.core.BridgeError
import com.fersaiyan.cyanbridge.bridge.core.DeviceInfo
import com.fersaiyan.cyanbridge.bridge.core.DisplayCommand
import com.fersaiyan.cyanbridge.bridge.core.GlassesBridgeState
import com.fersaiyan.cyanbridge.bridge.core.GlassesCapability
import com.fersaiyan.cyanbridge.bridge.core.GlassesDeviceAdapter
import com.fersaiyan.cyanbridge.bridge.core.InputEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Mock glasses adapter for testing the bridge without real hardware.
 * Stores all commands in memory and exposes them for verification.
 */
class MockDisplayAdapter : GlassesDeviceAdapter {

    companion object {
        private const val TAG = "MockDisplayAdapter"
    }

    override val adapterId: String = "mock"
    override val displayName: String = "Mock Glasses (Debug)"

    override val capabilities: Set<GlassesCapability> = setOf(
        GlassesCapability.TEXT_DISPLAY,
        GlassesCapability.LINE_DISPLAY,
        GlassesCapability.CARD_DISPLAY,
        GlassesCapability.CLEAR_DISPLAY,
        GlassesCapability.BATTERY_STATUS,
        GlassesCapability.BRIGHTNESS_CONTROL,
    )

    private val _state = MutableStateFlow<GlassesBridgeState>(GlassesBridgeState.Disconnected)
    override val state: StateFlow<GlassesBridgeState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<InputEvent>(extraBufferCapacity = 16)
    override val events: Flow<InputEvent> = _events.asSharedFlow()

    // --- Observable state for UI preview ---

    private val _lastDisplayCommand = MutableStateFlow<DisplayCommand?>(null)
    val lastDisplayCommand: StateFlow<DisplayCommand?> = _lastDisplayCommand.asStateFlow()

    private val _displayHistory = MutableStateFlow<List<DisplayCommand>>(emptyList())
    /** All display commands received, most recent last. */
    val displayHistory: StateFlow<List<DisplayCommand>> = _displayHistory.asStateFlow()

    private val _brightness = MutableStateFlow(80)
    val brightness: StateFlow<Int> = _brightness.asStateFlow()

    private var mockBattery = 72

    // --- Connection ---

    override suspend fun scan(): List<DeviceInfo> {
        Log.i(TAG, "scan() — returning mock device")
        delay(500)
        return listOf(
            DeviceInfo(
                id = "mock-001",
                name = "Mock Glasses",
                address = "00:11:22:33:44:55",
                adapterId = adapterId,
                rssi = -45,
                batteryLevel = mockBattery,
            )
        )
    }

    override suspend fun connect(device: DeviceInfo) {
        Log.i(TAG, "connect(${device.name})")
        _state.value = GlassesBridgeState.Connecting
        delay(300)
        _state.value = GlassesBridgeState.Connected
    }

    override suspend fun disconnect() {
        Log.i(TAG, "disconnect()")
        _state.value = GlassesBridgeState.Disconnected
    }

    // --- Display ---

    override suspend fun showText(command: DisplayCommand.Text): Result<Unit> {
        Log.i(TAG, "showText: \"${command.text}\" (priority=${command.priority}, ttl=${command.ttlMs})")
        recordCommand(command)
        return Result.success(Unit)
    }

    override suspend fun showLines(command: DisplayCommand.Lines): Result<Unit> {
        Log.i(TAG, "showLines: ${command.lines.size} lines, page=${command.page}/${command.totalPages}")
        recordCommand(command)
        return Result.success(Unit)
    }

    override suspend fun showCard(command: DisplayCommand.Card): Result<Unit> {
        Log.i(TAG, "showCard: title=\"${command.title}\", body=\"${command.body}\"")
        recordCommand(command)
        return Result.success(Unit)
    }

    override suspend fun clearDisplay(): Result<Unit> {
        Log.i(TAG, "clearDisplay()")
        recordCommand(DisplayCommand.Clear)
        return Result.success(Unit)
    }

    // --- Device ---

    override suspend fun setBrightness(level: Int): Result<Unit> {
        val clamped = level.coerceIn(0, 100)
        Log.i(TAG, "setBrightness($clamped)")
        _brightness.value = clamped
        return Result.success(Unit)
    }

    override suspend fun requestBattery(): Result<Int> {
        Log.i(TAG, "requestBattery() → $mockBattery")
        return Result.success(mockBattery)
    }

    // --- Audio ---

    override suspend fun startMic(): Result<Unit> {
        Log.i(TAG, "startMic() — not supported in mock")
        return Result.failure(BridgeError.UnsupportedCapability(GlassesCapability.MICROPHONE_AUDIO))
    }

    override suspend fun stopMic(): Result<Unit> {
        Log.i(TAG, "stopMic() — not supported in mock")
        return Result.failure(BridgeError.UnsupportedCapability(GlassesCapability.MICROPHONE_AUDIO))
    }

    // --- Internal ---

    private fun recordCommand(command: DisplayCommand) {
        _lastDisplayCommand.value = command
        _displayHistory.value = _displayHistory.value + command
    }

    /** Simulate an input event (for testing). */
    fun simulateInput(event: InputEvent) {
        _events.tryEmit(event)
    }

    /** Set mock battery level (for testing). */
    fun setMockBattery(level: Int) {
        mockBattery = level.coerceIn(0, 100)
    }
}

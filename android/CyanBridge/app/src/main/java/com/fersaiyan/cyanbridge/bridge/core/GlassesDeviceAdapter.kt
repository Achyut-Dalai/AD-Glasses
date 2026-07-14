package com.fersaiyan.cyanbridge.bridge.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface that all glasses device adapters must implement.
 * Each adapter translates [DisplayCommand]s into device-specific BLE/protocol calls
 * and emits [InputEvent]s from the device.
 *
 * Unsupported methods should return [Result.failure] with a descriptive error.
 */
interface GlassesDeviceAdapter {

    /** Unique identifier for this adapter (e.g. "memomind", "heycyan", "mock"). */
    val adapterId: String

    /** Human-readable name shown in the UI. */
    val displayName: String

    /** Set of capabilities this adapter supports. */
    val capabilities: Set<GlassesCapability>

    /** Current connection state. */
    val state: StateFlow<GlassesBridgeState>

    /** Stream of input events from the glasses. */
    val events: Flow<InputEvent>

    // --- Connection ---

    /** Scan for available devices. */
    suspend fun scan(): List<DeviceInfo>

    /** Connect to a specific device. */
    suspend fun connect(device: DeviceInfo)

    /** Disconnect from the current device. */
    suspend fun disconnect()

    // --- Display ---

    /** Show a text command on the glasses. */
    suspend fun showText(command: DisplayCommand.Text): Result<Unit>

    /** Show multiple lines on the glasses. */
    suspend fun showLines(command: DisplayCommand.Lines): Result<Unit>

    /** Show a structured card on the glasses. */
    suspend fun showCard(command: DisplayCommand.Card): Result<Unit>

    /** Clear the glasses display. */
    suspend fun clearDisplay(): Result<Unit>

    // --- Device ---

    /** Set brightness level (0-100). */
    suspend fun setBrightness(level: Int): Result<Unit>

    /** Request current battery level. */
    suspend fun requestBattery(): Result<Int>

    // --- Audio ---

    /** Start microphone capture on the glasses. */
    suspend fun startMic(): Result<Unit>

    /** Stop microphone capture on the glasses. */
    suspend fun stopMic(): Result<Unit>
}

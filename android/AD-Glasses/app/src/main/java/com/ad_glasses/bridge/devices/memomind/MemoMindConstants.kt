package com.ad_glasses.bridge.devices.memomind

import java.util.UUID

/**
 * MemoMind transport identifiers derived from Frida live capture and decompiled analysis.
 */
object MemoMindConstants {

    // ---- Command service characteristics ----

    /** Write characteristic for sending commands to the glasses. */
    val COMMAND_WRITE_UUID: UUID = UUID.fromString("00002001-0000-1000-8000-00805F9B34FB")

    /** Notify characteristic for receiving responses / events from the glasses. */
    val COMMAND_NOTIFY_UUID: UUID = UUID.fromString("00002002-0000-1000-8000-00805F9B34FB")

    // ---- CCCD descriptor (standard) ----

    /** Client Characteristic Configuration Descriptor – used to enable notifications. */
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

    // ---- Record service characteristics ----

    /** Record data characteristic (0x2020). */
    val RECORD_DATA_UUID: UUID = UUID.fromString("00002020-0000-1000-8000-00805F9B34FB")

    /** Record notify characteristic (0x2024). */
    val RECORD_NOTIFY_UUID: UUID = UUID.fromString("00002024-0000-1000-8000-00805F9B34FB")

    /** Record write characteristic (0x2025). */
    val RECORD_WRITE_UUID: UUID = UUID.fromString("00002025-0000-1000-8000-00805F9B34FB")

    /** Record extra / auxiliary characteristic (0x2026). */
    val RECORD_EXTRA_UUID: UUID = UUID.fromString("00002026-0000-1000-8000-00805F9B34FB")

    // ---- OTA ----

    /** OTA firmware update characteristic (0x7033). */
    val OTA_UUID: UUID = UUID.fromString("00007033-0000-1000-8000-00805F9B34FB")

    // ---- SPP / RFCOMM ----

    /** Primary RFCOMM control socket UUID. */
    val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    /** Secondary RFCOMM data/control socket UUID. */
    val EXTRA_RFCOMM_UUID: UUID = UUID.fromString("00002026-0000-1000-8000-00805F9B34FB")

    /** Record/audio RFCOMM socket UUID. */
    val RECORD_RFCOMM_UUID: UUID = UUID.fromString("00002024-0000-1000-8000-00805F9B34FB")

    // ---- RFCOMM frame group/opcode constants ----

    const val GROUP_DEVICE: Int = 0x01
    const val GROUP_CARDS: Int = 0x02
    const val GROUP_UTILITY_MENU: Int = 0x03
    const val GROUP_TELEPROMPTER: Int = 0x04
    const val GROUP_NOTIFICATIONS: Int = 0x05
    const val GROUP_RECORDER: Int = 0x0C

    const val OP_DEVICE_INFO: Int = 0x02
    const val OP_APP_VERSION: Int = 0x03
    const val OP_BATTERY_SETTINGS: Int = 0x06
    const val OP_SET_BRIGHTNESS: Int = 0x0B
    const val OP_SET_WAKE_ANGLE: Int = 0x0C
    const val OP_SET_FONT_SIZE: Int = 0x0E
    const val OP_SET_AUTO_BRIGHTNESS: Int = 0x0F

    const val TYPE_REQUEST: Int = 0x01
    const val TYPE_RESPONSE: Int = 0x02
    const val TYPE_ACK: Int = 0x06
    const val TYPE_PUSH: Int = 0x08

    // ---- Device name patterns for BLE scanning ----

    /** List of substrings used to identify MemoMind glasses during scan. */
    val NAME_PATTERNS: List<String> = listOf("memomind", "memo-mind", "memo mind", "aphrodite", "xgimi", "aimb", "heycyan", "cyan")
}

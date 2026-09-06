package com.adglasses.app.integrations.heycyan

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

private const val FRAME_MARKER: Int = 0xBC
private const val HEADER_LENGTH = 6

data class HeyCyanFrame(
    val command: Int,
    val payload: ByteArray,
    val crc: Int,
    val raw: ByteArray,
)

enum class HeyCyanNetworkMode(val wireValue: Int) {
    PeerToPeer(0x01),
    AccessPoint(0x02);

    companion object {
        fun fromWire(value: Int): HeyCyanNetworkMode? = entries.firstOrNull { it.wireValue == value }
    }
}

data class HeyCyanNetworkPreparation(
    val responseCode: Int,
    val mode: HeyCyanNetworkMode,
    val ssid: String,
    val passphrase: String,
)

data class HeyCyanBatteryStatus(val level: Int, val charging: Boolean)

data class HeyCyanControlAck(
    val responseCode: Int,
    val workType: Int,
    val errorCode: Int,
    val activeWorkType: Int?,
)

sealed interface HeyCyanDeviceEvent {
    data object AiPhotoReady : HeyCyanDeviceEvent
    data object AssistantListeningStarted : HeyCyanDeviceEvent
    data object AssistantListeningEnded : HeyCyanDeviceEvent
    data class Battery(val status: HeyCyanBatteryStatus) : HeyCyanDeviceEvent
    data class WifiAddress(val address: String) : HeyCyanDeviceEvent
    data class WifiError(val code: Int) : HeyCyanDeviceEvent
    data class Unknown(val type: Int, val payload: ByteArray) : HeyCyanDeviceEvent
}

sealed interface HeyCyanCommand {
    val family: Int
    val payload: ByteArray

    data class SyncTime(val record: ByteArray = buildTimeSyncRecord()) : HeyCyanCommand {
        override val family = 0x40
        override val payload = record
    }

    data object Battery : HeyCyanCommand {
        override val family = 0x42
        override val payload = byteArrayOf(0, 0)
    }

    data object DeviceInfo : HeyCyanCommand {
        override val family = 0x43
        override val payload = byteArrayOf(0, 0)
    }

    data object ClassicBluetooth : HeyCyanCommand {
        override val family = 0x49
        override val payload = byteArrayOf(0x02, 0x01)
    }

    data object ReadVoiceWake : HeyCyanCommand {
        override val family = 0x44
        override val payload = byteArrayOf(0x01, 0x00)
    }

    data class SetVoiceWake(val enabled: Boolean) : HeyCyanCommand {
        override val family = 0x44
        override val payload = byteArrayOf(0x02, if (enabled) 0x01 else 0x00)
    }

    data object ReadVolume : HeyCyanCommand {
        override val family = 0x51
        override val payload = byteArrayOf(0x01)
    }

    data object TakePhoto : Control(0x01)
    data object StartVideo : Control(0x02)
    data object StopVideo : Control(0x03)
    data object StartAudioRecording : Control(0x08)
    data object StopAudioRecording : Control(0x0C)

    data object ReadMediaCounts : HeyCyanCommand {
        override val family = 0x41
        override val payload = byteArrayOf(0x02, 0x04)
    }

    data class AiPhoto(val quality: Int) : HeyCyanCommand {
        init { require(quality in 0..5) }
        override val family = 0x41
        override val payload = byteArrayOf(0x02, 0x01, 0x06, quality.toByte(), quality.toByte())
    }

    data class PrepareMedia(val mode: HeyCyanNetworkMode) : HeyCyanCommand {
        override val family = 0x41
        override val payload = byteArrayOf(0x02, 0x01, 0x04, mode.wireValue.toByte())
    }

    data object FinishMedia : Control(0x09)

    data object StartTranslationHeartbeat : HeyCyanCommand {
        override val family = 0x41
        override val payload = byteArrayOf(0x02, 0x0C, 0x01)
    }

    data object StopTranslationHeartbeat : HeyCyanCommand {
        override val family = 0x41
        override val payload = byteArrayOf(0x02, 0x0C, 0x02)
    }

    abstract class Control(private val workType: Int) : HeyCyanCommand {
        override val family = 0x41
        override val payload: ByteArray get() = byteArrayOf(0x02, 0x01, workType.toByte())
    }
}

object HeyCyanFrameCodec {
    fun encode(command: Int, payload: ByteArray = byteArrayOf()): ByteArray {
        require(command in 0..255)
        require(payload.size <= 0xFFFF)
        val checksum = if (payload.isEmpty()) 0xFFFF else crc16Modbus(payload)
        return ByteArray(HEADER_LENGTH + payload.size).also { out ->
            out[0] = FRAME_MARKER.toByte()
            out[1] = command.toByte()
            out[2] = (payload.size and 0xFF).toByte()
            out[3] = ((payload.size ushr 8) and 0xFF).toByte()
            out[4] = (checksum and 0xFF).toByte()
            out[5] = ((checksum ushr 8) and 0xFF).toByte()
            payload.copyInto(out, destinationOffset = HEADER_LENGTH)
        }
    }

    fun decode(frame: ByteArray): HeyCyanFrame {
        require(frame.size >= HEADER_LENGTH) { "Frame is shorter than the 6-byte header" }
        require(frame[0].u8() == FRAME_MARKER) { "Invalid frame marker" }
        val length = frame[2].u8() or (frame[3].u8() shl 8)
        require(frame.size == HEADER_LENGTH + length) { "Frame length mismatch" }
        val payload = frame.copyOfRange(HEADER_LENGTH, frame.size)
        val receivedCrc = frame[4].u8() or (frame[5].u8() shl 8)
        val expectedCrc = if (payload.isEmpty()) 0xFFFF else crc16Modbus(payload)
        require(receivedCrc == expectedCrc) { "CRC mismatch" }
        return HeyCyanFrame(frame[1].u8(), payload, receivedCrc, frame.copyOf())
    }

    fun crc16Modbus(data: ByteArray): Int {
        var crc = 0xFFFF
        data.forEach { byte ->
            crc = crc xor byte.u8()
            repeat(8) {
                crc = if ((crc and 1) != 0) (crc ushr 1) xor 0xA001 else crc ushr 1
            }
        }
        return crc and 0xFFFF
    }
}

class HeyCyanFrameStreamDecoder(private val maximumBufferedBytes: Int = 256 * 1024) {
    private var buffer = ByteArray(0)

    fun append(chunk: ByteArray): List<HeyCyanFrame> {
        if (chunk.isEmpty()) return emptyList()
        buffer += chunk
        if (buffer.size > maximumBufferedBytes) {
            buffer = buffer.copyOfRange(buffer.size - maximumBufferedBytes, buffer.size)
        }
        val frames = mutableListOf<HeyCyanFrame>()
        while (buffer.isNotEmpty()) {
            val markerIndex = buffer.indexOfFirst { it.u8() == FRAME_MARKER }
            if (markerIndex < 0) {
                buffer = byteArrayOf()
                break
            }
            if (markerIndex > 0) buffer = buffer.copyOfRange(markerIndex, buffer.size)
            if (buffer.size < HEADER_LENGTH) break
            val length = buffer[2].u8() or (buffer[3].u8() shl 8)
            val frameSize = HEADER_LENGTH + length
            if (buffer.size < frameSize) break
            val candidate = buffer.copyOfRange(0, frameSize)
            try {
                frames += HeyCyanFrameCodec.decode(candidate)
                buffer = buffer.copyOfRange(frameSize, buffer.size)
            } catch (_: IllegalArgumentException) {
                buffer = buffer.copyOfRange(1, buffer.size)
            }
        }
        return frames
    }

    fun reset() {
        buffer = byteArrayOf()
    }
}

object HeyCyanResponseDecoder {
    fun battery(frame: HeyCyanFrame): HeyCyanBatteryStatus {
        require(frame.command == 0x42 && frame.payload.size >= 2)
        val level = frame.payload[0].u8()
        val charging = frame.payload[1].u8()
        require(level in 0..100 && charging in 0..1)
        return HeyCyanBatteryStatus(level, charging == 1)
    }

    fun controlAck(frame: HeyCyanFrame, expectedWorkType: Int): HeyCyanControlAck {
        require(frame.command == 0x41 && frame.payload.size >= 4)
        require(frame.payload[1].u8() == 0x01)
        require(frame.payload[2].u8() == expectedWorkType)
        val error = frame.payload[3].u8()
        require(error == 0 || error == 0xFF) { "Glasses rejected command with error $error" }
        return HeyCyanControlAck(
            responseCode = frame.payload[0].u8(),
            workType = expectedWorkType,
            errorCode = error,
            activeWorkType = if (error == 0 && frame.payload.size > 4) frame.payload[4].u8() else null,
        )
    }

    fun networkPreparation(frame: HeyCyanFrame): HeyCyanNetworkPreparation {
        require(frame.command == 0x41 && frame.payload.size >= 8)
        val p = frame.payload
        require(p[1].u8() == 1 && p[2].u8() == 4)
        val mode = HeyCyanNetworkMode.fromWire(p[3].u8())
            ?: error("Glasses rejected or returned unknown network mode")
        val ssidLength = p[4].u8() or (p[5].u8() shl 8)
        val passLength = p[6].u8() or (p[7].u8() shl 8)
        val required = 8 + ssidLength + passLength
        require(ssidLength > 0 && passLength > 0 && p.size >= required)
        val ssid = String(p, 8, ssidLength, StandardCharsets.UTF_8)
        val passphrase = String(p, 8 + ssidLength, passLength, StandardCharsets.UTF_8)
        return HeyCyanNetworkPreparation(p[0].u8(), mode, ssid, passphrase)
    }

    fun deviceEvent(frame: HeyCyanFrame): HeyCyanDeviceEvent {
        require(frame.command == 0x73 && frame.payload.isNotEmpty())
        val p = frame.payload
        return when (p[0].u8()) {
            0x02 -> HeyCyanDeviceEvent.AiPhotoReady
            0x03 -> if (p.size >= 2 && p[1].u8() == 1) {
                HeyCyanDeviceEvent.AssistantListeningStarted
            } else {
                HeyCyanDeviceEvent.Unknown(0x03, p.copyOf())
            }
            0x05 -> {
                require(p.size >= 3)
                val level = p[1].u8()
                val charging = p[2].u8()
                require(level in 0..100 && charging in 0..1)
                HeyCyanDeviceEvent.Battery(HeyCyanBatteryStatus(level, charging == 1))
            }
            0x08 -> {
                require(p.size >= 5)
                HeyCyanDeviceEvent.WifiAddress(
                    p.copyOfRange(1, 5).joinToString(".") { it.u8().toString() },
                )
            }
            0x09 -> {
                require(p.size >= 2)
                HeyCyanDeviceEvent.WifiError(p[1].u8())
            }
            0x0A -> if (p.size >= 2 && p[1].u8() == 1) {
                HeyCyanDeviceEvent.AssistantListeningEnded
            } else {
                HeyCyanDeviceEvent.Unknown(0x0A, p.copyOf())
            }
            else -> HeyCyanDeviceEvent.Unknown(p[0].u8(), p.copyOf())
        }
    }
}

fun HeyCyanCommand.matches(frame: HeyCyanFrame): Boolean {
    if (frame.command != family) return false
    return when (this) {
        is HeyCyanCommand.SyncTime -> frame.payload.firstOrNull()?.u8() == 0
        HeyCyanCommand.Battery, HeyCyanCommand.DeviceInfo -> true
        HeyCyanCommand.ClassicBluetooth -> frame.payload.contentEquals(payload)
        HeyCyanCommand.ReadVoiceWake -> frame.payload.firstOrNull()?.u8() == 1
        is HeyCyanCommand.SetVoiceWake -> frame.payload.firstOrNull()?.u8() == 2
        HeyCyanCommand.ReadVolume -> frame.payload.firstOrNull()?.u8() == 1
        HeyCyanCommand.TakePhoto -> frame.matchesControl(0x01)
        HeyCyanCommand.StartVideo -> frame.matchesControl(0x02)
        HeyCyanCommand.StopVideo -> frame.matchesControl(0x03)
        HeyCyanCommand.StartAudioRecording -> frame.matchesControl(0x08)
        HeyCyanCommand.StopAudioRecording -> frame.matchesControl(0x0C)
        is HeyCyanCommand.AiPhoto -> frame.matchesControl(0x06)
        is HeyCyanCommand.PrepareMedia -> frame.matchesControl(0x04) && frame.payload.size >= 4
        HeyCyanCommand.FinishMedia -> frame.matchesControl(0x09)
        HeyCyanCommand.ReadMediaCounts -> frame.payload.size >= 8 && frame.payload[1].u8() == 0x04
        HeyCyanCommand.StartTranslationHeartbeat,
        HeyCyanCommand.StopTranslationHeartbeat -> frame.payload.size >= 3 && frame.payload[1].u8() == 0x0C
        else -> false
    }
}

private fun HeyCyanFrame.matchesControl(workType: Int): Boolean =
    payload.size >= 3 && payload[1].u8() == 1 && payload[2].u8() == workType

private fun buildTimeSyncRecord(
    instant: Instant = Instant.now().plusSeconds(1),
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): ByteArray {
    val local = instant.atZone(zoneId)
    val offsetHours = zoneId.rules.getOffset(instant).totalSeconds.toDouble() / 3600.0
    val wrapped = (24.0 + offsetHours) % 24.0
    val timezone = (wrapped * 2.0 + 1.0).toInt().coerceIn(0, 255)
    return byteArrayOf(
        bcd(local.year % 2000),
        bcd(local.monthValue),
        bcd(local.dayOfMonth),
        bcd(local.hour),
        bcd(local.minute),
        bcd(local.second),
        languageCode(locale),
        timezone.toByte(),
        0x01,
    )
}

private fun languageCode(locale: Locale): Byte {
    val code = when (locale.language.lowercase(Locale.ROOT)) {
        "zh" -> if (locale.country.uppercase(Locale.ROOT) in setOf("HK", "TW")) 2 else 0
        "en" -> 1
        "el" -> 3
        "fr" -> 4
        "de" -> 5
        "it" -> 6
        "es" -> 7
        "nl" -> 8
        "pt" -> 9
        "ru" -> 10
        "tr" -> 11
        "ja" -> 12
        "ko" -> 13
        "pl" -> 14
        "ro" -> 15
        "ar" -> 16
        "th" -> 17
        "vi" -> 18
        "id", "in" -> 19
        "hi" -> 20
        "cs" -> 21
        "sk" -> 22
        "hu" -> 23
        "he", "iw" -> 24
        "hr" -> 25
        "sl" -> 26
        else -> 1
    }
    return code.toByte()
}

private fun bcd(value: Int): Byte = (((value / 10) shl 4) or (value % 10)).toByte()

internal fun Byte.u8(): Int = toInt() and 0xFF

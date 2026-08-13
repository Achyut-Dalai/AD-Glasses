package com.achyut.adglasses.bridge.devices.memomind

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicInteger

/**
 * Encodes RFCOMM control frames for MemoMind glasses.
 *
 * The confirmed wire format is:
 * `fa 00 <len:uint16 BE> <seq> <group> <opcode> <type> ...`
 */
object MemoMindPacketEncoder {

    private const val FRAME_PREFIX_0: Int = 0xFA
    private const val FRAME_PREFIX_1: Int = 0x00

    private const val TYPE_REQUEST: Int = 0x01
    private const val TYPE_RESPONSE: Int = 0x02
    private const val TYPE_PUSH: Int = 0x08

    private const val GROUP_DEVICE: Int = 0x01
    private const val GROUP_CARDS: Int = 0x02
    private const val GROUP_NOTIFICATIONS: Int = 0x05

    private const val OP_DEVICE_INFO: Int = 0x02
    private const val OP_BATTERY: Int = 0x06
    private const val OP_CARD_PUSH: Int = 0x08
    private const val OP_NOTIFICATION_PUSH: Int = 0x01
    private const val OP_SET_BRIGHTNESS: Int = 0x0B
    private const val OP_SET_WAKE_ANGLE: Int = 0x0C
    private const val OP_SET_FONT_SIZE: Int = 0x0E
    private const val OP_SET_AUTO_BRIGHTNESS: Int = 0x0F

    private const val COMPONENT_STOCK: Int = 0x04
    private const val COMPONENT_NEWS: Int = 0x06
    private const val COMPONENT_SCHEDULE: Int = 0x09
    private const val COMPONENT_CALENDAR: Int = 0x0A
    private const val COMPONENT_NOTIFICATION: Int = 0x01

    private const val COMPONENT_ACTION_PUSH: Int = 0x05

    private val sequenceCounter = AtomicInteger(1)
    private val nextNotificationId = AtomicInteger(10_000)

    data class ScheduleItem(
        val title: String,
        val body: String = "",
        val timestamp: String = "",
        val completed: Int = 0,
        val id: Int = nextNotificationId.getAndIncrement(),
    )

    data class CalendarItem(
        val title: String,
        val timestamp: String,
        val body: String = "",
        val id: Int = nextNotificationId.getAndIncrement(),
    )

    data class NewsItem(
        val content: String,
        val id: Int = nextNotificationId.getAndIncrement(),
        val source: String = "AD Glasses",
        val title: String = "",
    )

    fun encodeDeviceInfoRequest(): ByteArray = encodeSimpleRequest(GROUP_DEVICE, OP_DEVICE_INFO)

    fun encodeBatteryRequest(): ByteArray = encodeSimpleRequest(GROUP_DEVICE, OP_BATTERY)

    fun encodeShowText(text: String): ByteArray {
        return encodeScheduleItems(
            listOf(
                ScheduleItem(title = text.trim().ifEmpty { "AD Glasses" }),
            ),
        )
    }

    fun encodeLines(lines: List<String>): ByteArray {
        if (lines.isEmpty()) return encodeEmptySchedule()
        val items = lines.filter { it.isNotBlank() }.map {
            ScheduleItem(title = it)
        }
        return encodeScheduleItems(items.ifEmpty { listOf(ScheduleItem(title = "AD Glasses")) })
    }

    fun encodeScheduleCard(title: String, body: String): ByteArray {
        return encodeScheduleItems(listOf(ScheduleItem(title = title.ifBlank { "AD Glasses" }, body = body)))
    }

    fun encodeEmptySchedule(): ByteArray = encodeScheduleItems(emptyList())

    fun encodeScheduleItems(items: List<ScheduleItem>): ByteArray {
        val payload = JSONArray().apply {
            items.forEach { item ->
                put(
                    JSONObject()
                        .put("id", item.id)
                        .put("ti", item.title)
                        .put("ts", item.timestamp)
                        .put("c", item.body)
                        .put("do", item.completed),
                )
            }
        }.toString()
        return encodeComponentPush(COMPONENT_SCHEDULE, payload)
    }

    fun encodeCalendarItems(items: List<CalendarItem>): ByteArray {
        val payload = JSONArray().apply {
            items.forEach { item ->
                put(
                    JSONObject()
                        .put("id", item.id)
                        .put("ti", item.title)
                        .put("ts", item.timestamp)
                        .put("c", item.body),
                )
            }
        }.toString()
        return encodeComponentPush(COMPONENT_CALENDAR, payload)
    }

    fun encodeNewsItems(items: List<NewsItem>): ByteArray {
        val payload = JSONArray().apply {
            items.forEach { item ->
                put(
                    JSONObject()
                        .put("c", item.content)
                        .put("id", item.id)
                        .put("src", item.source)
                        .put("t", item.title),
                )
            }
        }.toString()
        return encodeComponentPush(COMPONENT_NEWS, payload)
    }

    fun encodeNotification(
        title: String,
        body: String,
        appName: String = "AD Glasses",
        packageName: String = "com.achyut.adglasses",
    ): ByteArray {
        val payload = JSONObject()
            .put("id", nextNotificationId.getAndIncrement())
            .put("a", appName)
            .put("type", 0)
            .put("ts", System.currentTimeMillis() / 1000L)
            .put("c", body)
            .put("ti", title)
            .put("pkg_name", packageName)
            .toString()
        return encodePushFrame(
            group = GROUP_NOTIFICATIONS,
            opcode = OP_NOTIFICATION_PUSH,
            componentId = COMPONENT_NOTIFICATION,
            actionId = COMPONENT_ACTION_PUSH,
            payload = payload,
        )
    }

    fun encodeSetBrightness(level: Int): ByteArray {
        val clamped = level.coerceIn(0, 10)
        return encodeSettingsWrite(OP_SET_BRIGHTNESS, clamped)
    }

    fun encodeSetAutoBrightness(enabled: Boolean): ByteArray {
        return encodeSettingsWrite(OP_SET_AUTO_BRIGHTNESS, if (enabled) 0x00 else 0x01)
    }

    fun encodeSetWakeAngle(degrees: Int): ByteArray {
        val clamped = degrees.coerceIn(0, 60)
        return encodeSettingsWrite(OP_SET_WAKE_ANGLE, clamped)
    }

    fun encodeSetFontSize(size: Int): ByteArray {
        val clamped = size.coerceIn(1, 6)
        return encodeSettingsWrite(OP_SET_FONT_SIZE, clamped)
    }

    fun extractJsonPayload(frame: ByteArray, expectedGroup: Int, expectedOpcode: Int): String? {
        if (frame.size < 13) return null
        if ((frame[0].toInt() and 0xFF) != FRAME_PREFIX_0 || (frame[1].toInt() and 0xFF) != FRAME_PREFIX_1) return null

        val group = frame[5].toInt() and 0xFF
        val opcode = frame[6].toInt() and 0xFF
        val type = frame[7].toInt() and 0xFF
        if (group != expectedGroup || opcode != expectedOpcode || type != TYPE_RESPONSE) return null

        val payloadLength = ((frame[8].toInt() and 0xFF) shl 16) or
            ((frame[9].toInt() and 0xFF) shl 8) or
            (frame[10].toInt() and 0xFF)
        val payloadStart = 11
        val payloadEnd = payloadStart + payloadLength
        if (payloadEnd + 2 > frame.size) return null

        return frame.copyOfRange(payloadStart, payloadEnd).toString(Charsets.UTF_8)
    }

    private fun encodeComponentPush(componentId: Int, payload: String): ByteArray {
        return encodePushFrame(
            group = GROUP_CARDS,
            opcode = OP_CARD_PUSH,
            componentId = componentId,
            actionId = COMPONENT_ACTION_PUSH,
            payload = payload,
        )
    }

    /**
     * Encode a single-value settings write.
     *
     * Observed wire format:
     * `fa 00 00 0e <seq> 01 <opcode> 08 00 00 01 <value> 01 <sum>`
     */
    private fun encodeSettingsWrite(opcode: Int, value: Int): ByteArray {
        val seq = nextSequence()
        val frame = byteArrayOf(
            FRAME_PREFIX_0.toByte(),
            FRAME_PREFIX_1.toByte(),
            0x00, 0x0E,
            seq.toByte(),
            GROUP_DEVICE.toByte(),
            opcode.toByte(),
            TYPE_PUSH.toByte(),
            0x00, 0x00, 0x01,
            value.toByte(),
            0x01,
            0x00, // placeholder for checksum
        )
        val sum = frame.take(13).fold(0) { acc, b -> (acc + (b.toInt() and 0xFF)) and 0xFFFF }
        frame[13] = sum.toByte()
        return frame
    }

    private fun encodeSimpleRequest(group: Int, opcode: Int): ByteArray {
        val seq = nextSequence()
        val checksum = (seq + group + opcode + TYPE_REQUEST + 0x02) and 0xFF
        return byteArrayOf(
            FRAME_PREFIX_0.toByte(),
            FRAME_PREFIX_1.toByte(),
            0x00,
            0x09,
            seq.toByte(),
            group.toByte(),
            opcode.toByte(),
            TYPE_REQUEST.toByte(),
            checksum.toByte(),
        )
    }

    private fun encodePushFrame(
        group: Int,
        opcode: Int,
        componentId: Int,
        actionId: Int,
        payload: String,
    ): ByteArray {
        val payloadBytes = payload.toByteArray(Charsets.UTF_8)
        val seq = nextSequence()

        val frame = ByteArrayOutputStream()
        frame.write(FRAME_PREFIX_0)
        frame.write(FRAME_PREFIX_1)
        frame.write(0x00)
        frame.write(0x00)
        frame.write(seq)
        frame.write(group)
        frame.write(opcode)
        frame.write(TYPE_PUSH)
        frame.write(0x00)
        frame.write(0x00)
        frame.write(0x01)
        frame.write(componentId)
        frame.write(actionId)
        frame.write(0x00)
        frame.write((payloadBytes.size shr 8) and 0xFF)
        frame.write(payloadBytes.size and 0xFF)
        frame.write(payloadBytes)
        frame.write(0x00)

        val withoutChecksum = frame.toByteArray()
        val checksum = withoutChecksum.fold(0) { acc, byte ->
            (acc + (byte.toInt() and 0xFF)) and 0xFFFF
        }

        frame.write((checksum shr 8) and 0xFF)
        frame.write(checksum and 0xFF)

        val complete = frame.toByteArray()
        val totalLength = complete.size
        complete[2] = ((totalLength shr 8) and 0xFF).toByte()
        complete[3] = (totalLength and 0xFF).toByte()
        return complete
    }

    private fun nextSequence(): Int {
        val raw = sequenceCounter.getAndIncrement()
        val seq = raw and 0xFF
        return if (seq == 0) 1 else seq
    }
}

package com.fersaiyan.cyanbridge.devices.eyevue

import android.util.Log
import com.oudmon.ble.base.bluetooth.BleOperateManager

/**
 * Encapsulates the Eyevue smart glasses protocol based on reverse-engineered sources.
 *
 * Eyevue Datagram Format:
 * [0xAB, 0x55, len_hi, len_lo, commandId, payload..., crc]
 * Where:
 * - len = 1 + payload.size (2 bytes big-endian)
 * - crc = (commandId + sum(payload)) & 0xFF
 */
object EyevueProtocol {
    private const val TAG = "EyevueProtocol"

    const val SOF_HI = 0xAB.toByte()
    const val SOF_LO = 0x55.toByte()

    // Command IDs
    const val CMD_TAKE_PHOTO = 34
    const val CMD_RECORD_VIDEO = 35
    const val CMD_STOP_RECORD = 36
    const val CMD_RECORD_AUDIO = 52
    const val CMD_APP_LIVE = 103
    const val CMD_FILE_DOWNLOAD_FINISH = 68
    const val CMD_GET_BATTERY = 23
    const val CMD_GET_WIFI_INFO = 57

    // Parameter Constants
    const val PARAM_LIVE_AP = 0x30.toByte()    // '0'
    const val PARAM_LIVE_P2P = 0x31.toByte()   // '1'
    const val PARAM_DOWNLOAD_FINISH = 0x30.toByte()

    /**
     * Builds a full Eyevue BLE datagram packet.
     */
    fun buildDatagram(commandId: Int, payload: ByteArray = byteArrayOf()): ByteArray {
        val len = 1 + payload.size
        val packet = ByteArray(5 + payload.size + 1)
        packet[0] = SOF_HI
        packet[1] = SOF_LO
        packet[2] = ((len shr 8) and 0xFF).toByte()
        packet[3] = (len and 0xFF).toByte()
        packet[4] = (commandId and 0xFF).toByte()

        var crc = commandId and 0xFF
        for (i in payload.indices) {
            packet[5 + i] = payload[i]
            crc += (payload[i].toInt() and 0xFF)
        }
        packet[packet.size - 1] = (crc and 0xFF).toByte()
        return packet
    }

    /** Start Live streaming over AP mode. */
    fun buildStartLiveApPacket(): ByteArray = buildDatagram(CMD_APP_LIVE, byteArrayOf(PARAM_LIVE_AP))

    /** Start Live streaming over P2P mode. */
    fun buildStartLiveP2pPacket(): ByteArray = buildDatagram(CMD_APP_LIVE, byteArrayOf(PARAM_LIVE_P2P))

    /** Stop live stream / complete file transfer. */
    fun buildExitLivePacket(): ByteArray = buildDatagram(CMD_FILE_DOWNLOAD_FINISH, byteArrayOf(PARAM_DOWNLOAD_FINISH, 0x01))

    /** Trigger photo snapshot. */
    fun buildTakePhotoPacket(): ByteArray = buildDatagram(CMD_TAKE_PHOTO, byteArrayOf(0x31.toByte())) // High quality

    /** Start video recording. */
    fun buildRecordVideoPacket(): ByteArray = buildDatagram(CMD_RECORD_VIDEO, byteArrayOf(0x01.toByte()))

    /** Stop video recording. */
    fun buildStopRecordPacket(): ByteArray = buildDatagram(CMD_STOP_RECORD, byteArrayOf(0x00.toByte()))

    /** Send a built packet over active BLE connection. */
    fun sendPacket(packet: ByteArray): Boolean {
        if (!BleOperateManager.getInstance().isConnected) {
            Log.w(TAG, "Cannot send Eyevue packet: BLE not connected")
            return false
        }
        val hex = packet.joinToString(" ") { "%02X".format(it) }
        Log.i(TAG, "Sending Eyevue BLE Datagram: [$hex]")
        com.oudmon.ble.base.communication.LargeDataHandler.getInstance().glassesControl(packet) { _, _ -> }
        return true
    }
}

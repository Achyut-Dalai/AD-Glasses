package com.achyut.adglasses.devices.eyevue

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EyevueProtocolTest {
    @Test
    fun livePacketsMatchVendorFrames() {
        assertArrayEquals(
            byteArrayOf(0xAB.toByte(), 0x55, 0x00, 0x03, 0x67, 0x30, 0x97.toByte()),
            EyevueProtocol.buildStartLiveApPacket(),
        )
        assertArrayEquals(
            byteArrayOf(0xAB.toByte(), 0x55, 0x00, 0x04, 0x44, 0x30, 0x01, 0x75),
            EyevueProtocol.buildFinishTransferPacket(),
        )
    }

    @Test
    fun decoderHandlesFragmentedFrames() {
        val decoder = EyevueFrameDecoder()
        val packet = EyevueProtocol.buildStartLiveP2pPacket()

        assertTrue(decoder.append(packet.copyOfRange(0, 3)).isEmpty())
        val frames = decoder.append(packet.copyOfRange(3, packet.size))

        assertEquals(1, frames.size)
        assertEquals(EyevueProtocol.CMD_APP_LIVE, frames.single().commandId)
        assertArrayEquals(byteArrayOf(0x31), frames.single().payload)
    }

    @Test
    fun parserRejectsCorruptCrc() {
        val packet = EyevueProtocol.buildStartLiveApPacket().also { it[it.lastIndex] = 0 }

        runCatching { EyevueProtocol.parseDatagram(packet) }
            .onSuccess { error("Corrupt packet was accepted") }
    }

    @Test
    fun parsesBatteryAndWifiResponses() {
        val battery = EyevueProtocol.parseBattery(
            EyevueFrame(EyevueProtocol.CMD_GET_BATTERY, byteArrayOf(0x07, 0x05, 0x01)),
        )
        assertEquals(75, battery?.percent)
        assertTrue(battery?.isCharging == true)

        val wifi = EyevueProtocol.parseWifiSsid(
            EyevueFrame(EyevueProtocol.CMD_RECEIVE_WIFI_INFO, "Eyevue-AP\u0000".toByteArray()),
        )
        assertEquals("Eyevue-AP", wifi)
    }

    @Test
    fun voiceAssistantPacketsMatchVendorBitFlags() {
        assertArrayEquals(
            EyevueProtocol.valuePacket(EyevueProtocol.CMD_SET_VOICE_ASSISTANT_STATUS, 1),
            EyevueProtocol.buildSetVoiceAssistantStatusPacket(
                localOfflineSpeechEnabled = false,
                aiWakeWordEnabled = true,
            ),
        )
        val status = EyevueProtocol.parseVoiceAssistantStatus(
            EyevueFrame(EyevueProtocol.CMD_GET_VOICE_ASSISTANT_STATUS, byteArrayOf(1)),
        )
        assertEquals(false, status?.localOfflineSpeechEnabled)
        assertEquals(true, status?.aiWakeWordEnabled)
    }

    @Test
    fun photoAssemblerRemovesChunkAddressAndJoinsImageBytes() {
        val assembler = EyevuePhotoAssembler()
        assembler.append(photoPacket(EyevueProtocol.CMD_RECEIVE_PHOTO_DATA_START))
        assembler.append(photoPacket(EyevueProtocol.CMD_RECEIVE_PHOTO_DATA, byteArrayOf(0, 0, 0, 1, 0xFF.toByte(), 0xD8.toByte())))
        assembler.append(photoPacket(EyevueProtocol.CMD_RECEIVE_PHOTO_DATA, byteArrayOf(0, 0, 0, 2, 0xFF.toByte(), 0xD9.toByte())))

        val image = assembler.append(photoPacket(EyevueProtocol.CMD_RECEIVE_PHOTO_DATA_END))

        assertArrayEquals(
            byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte()),
            image,
        )
    }

    private fun photoPacket(commandId: Int, payload: ByteArray = byteArrayOf()): ByteArray =
        byteArrayOf(0xAB.toByte(), 0x55, 0, 0, commandId.toByte()) + payload + byteArrayOf(0, 0, 0)
}

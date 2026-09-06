package com.adglasses.app.integrations.heycyan

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class HeyCyanProtocolTest {
    @Test fun photoFrameMatchesCapturedVector() {
        val frame = HeyCyanFrameCodec.encode(0x41, byteArrayOf(0x02, 0x01, 0x01))
        assertArrayEquals(byteArrayOf(0xBC.toByte(), 0x41, 0x03, 0x00, 0x10, 0x50, 0x02, 0x01, 0x01), frame)
    }

    @Test fun emptyPayloadUsesProductionFfffCrc() {
        assertArrayEquals(byteArrayOf(0xBC.toByte(), 0x42, 0x00, 0x00, 0xFF.toByte(), 0xFF.toByte()), HeyCyanFrameCodec.encode(0x42))
    }

    @Test fun crcMatchesBatteryPayloadVector() {
        assertEquals(0xB001, HeyCyanFrameCodec.crc16Modbus(byteArrayOf(0x00, 0x00)))
    }

    @Test fun streamDecoderReassemblesAcrossNotifications() {
        val expected = HeyCyanFrameCodec.encode(0x41, byteArrayOf(0x02, 0x01, 0x01))
        val decoder = HeyCyanFrameStreamDecoder()
        assertEquals(0, decoder.append(expected.copyOfRange(0, 4)).size)
        val frames = decoder.append(expected.copyOfRange(4, expected.size))
        assertEquals(1, frames.size)
        assertArrayEquals(byteArrayOf(0x02, 0x01, 0x01), frames.single().payload)
    }
}

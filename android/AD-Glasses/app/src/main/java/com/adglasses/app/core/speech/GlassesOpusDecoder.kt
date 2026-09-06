package com.adglasses.app.core.speech

import io.github.jaredmdobson.concentus.OpusDecoder

/**
 * Decoder for the physically captured HeyCyan assistant-audio contract.
 *
 * The BLE frame parser has already removed the protocol frame. What arrives here is exactly one
 * raw 40-byte Opus packet; there is no Ogg/RTP/application header to strip. Output is 16-kHz mono
 * signed PCM, matching both the official app decoder configuration and Moonshine's preferred ASR
 * input rate.
 */
class GlassesOpusDecoder {
    companion object {
        const val SAMPLE_RATE = 16_000
        const val CHANNELS = 1
        const val EXPECTED_PACKET_BYTES = 40
        private const val MAX_PACKET_SAMPLES = SAMPLE_RATE * 120 / 1_000 // Opus maximum: 120 ms
    }

    private var decoder = OpusDecoder(SAMPLE_RATE, CHANNELS)

    @Synchronized
    fun reset() {
        decoder = OpusDecoder(SAMPLE_RATE, CHANNELS)
    }

    @Synchronized
    fun decode(packet: ByteArray): ShortArray {
        require(packet.size == EXPECTED_PACKET_BYTES) {
            "Unexpected glasses audio packet size ${packet.size}; expected $EXPECTED_PACKET_BYTES"
        }
        val output = ShortArray(MAX_PACKET_SAMPLES)
        val samples = decoder.decode(
            packet,
            0,
            packet.size,
            output,
            0,
            MAX_PACKET_SAMPLES,
            false,
        )
        require(samples > 0) { "The glasses Opus packet decoded to no audio" }
        return output.copyOf(samples)
    }

    fun toFloatPcm(samples: ShortArray): FloatArray =
        FloatArray(samples.size) { index -> samples[index] / 32768.0f }
}

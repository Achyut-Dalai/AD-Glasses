package com.achyut.adglasses.bridge.audio

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import java.nio.ByteBuffer

/**
 * Opus audio decoder using Android MediaCodec.
 *
 * Decodes Opus frames to 16-bit PCM at 24000 Hz mono.
 * This matches the MemoMind glasses recording format.
 *
 * ## Usage
 *
 * ```kotlin
 * val decoder = OpusDecoderWrapper()
 * decoder.configure()
 *
 * // For each Opus frame from the glasses:
 * val pcm: ByteArray? = decoder.decode(opusFrame)
 * if (pcm != null) {
 *     // Feed pcm to AudioTrack or save to file
 * }
 *
 * decoder.release()
 * ```
 *
 * ## Platform notes
 *
 * - MediaCodec Opus decoder is available from API 21 (Lollipop).
 * - The decoder expects **individual Opus frames** (not Ogg container).
 * - Each frame produces 20ms of audio by default at 24000 Hz (480 samples).
 *
 * Tag: OpusDecoderWrapper
 */
class OpusDecoderWrapper {

    companion object {
        private const val TAG = "OpusDecoderWrapper"

        /** Sample rate of the Opus stream (24000 Hz). */
        private const val SAMPLE_RATE = 24000

        /** Number of audio channels (mono). */
        private const val CHANNELS = 1

        /** MIME type for Opus audio. */
        private const val MIME_OPUS = "audio/opus"

        /** Timeout for dequeue operations in microseconds. */
        private const val TIMEOUT_US = 10_000L
    }

    private var decoder: MediaCodec? = null
    private var isConfigured = false

    /**
     * Configure and start the Opus decoder.
     *
     * Must be called once before [decode]. Can be called again after [release]
     * to reuse the wrapper.
     */
    fun configure() {
        try {
            val format = MediaFormat.createAudioFormat(MIME_OPUS, SAMPLE_RATE, CHANNELS)
            // Opus frames are NOT ADTS (no framing layer needed for raw frames)
            format.setInteger(MediaFormat.KEY_IS_ADTS, 0)

            decoder = MediaCodec.createDecoderByType(MIME_OPUS)
            decoder?.configure(format, null /* surface */, null /* crypto */, 0 /* flags */)
            decoder?.start()
            isConfigured = true
            Log.i(TAG, "Opus decoder configured: ${SAMPLE_RATE}Hz, ${CHANNELS}ch")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to configure Opus decoder", e)
            isConfigured = false
        }
    }

    /**
     * Decode a single Opus frame to PCM.
     *
     * @param opusFrame Raw Opus-encoded audio data (single frame)
     * @return Decoded 16-bit PCM byte array, or null if decoding fails
     */
    fun decode(opusFrame: ByteArray): ByteArray? {
        val codec = decoder ?: return null
        if (!isConfigured) return null

        try {
            // --- Input ---
            val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
            if (inputIndex < 0) {
                Log.v(TAG, "dequeueInputBuffer timed out")
                return null
            }

            val inputBuffer: ByteBuffer = codec.getInputBuffer(inputIndex)
                ?: return null
            inputBuffer.clear()
            inputBuffer.put(opusFrame)
            // Flags: 0 for a normal frame; CODEC_CONFIG for CSD, END_OF_STREAM for EOS.
            codec.queueInputBuffer(
                inputIndex,
                0 /* offset */,
                opusFrame.size,
                0 /* presentationTimeUs */,
                0 /* flags */,
            )

            // --- Output ---
            val bufferInfo = MediaCodec.BufferInfo()
            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)

            if (outputIndex < 0) {
                when (outputIndex) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Log.v(TAG, "dequeueOutputBuffer: try again later")
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Log.d(TAG, "Output format changed")
                    else -> Log.v(TAG, "dequeueOutputBuffer returned $outputIndex")
                }
                return null
            }

            val outputBuffer: ByteBuffer = codec.getOutputBuffer(outputIndex)
                ?: return null

            val pcmData = ByteArray(bufferInfo.size)
            outputBuffer.get(pcmData)
            codec.releaseOutputBuffer(outputIndex, false /* render */)

            return pcmData
        } catch (e: Exception) {
            Log.e(TAG, "Opus decode error", e)
            return null
        }
    }

    /**
     * Stop and release the decoder.
     *
     * After calling this, [configure] must be called again before the next
     * [decode] cycle.
     */
    fun release() {
        try {
            decoder?.stop()
            decoder?.release()
        } catch (_: Exception) {
        }
        decoder = null
        isConfigured = false
        Log.i(TAG, "Opus decoder released")
    }
}

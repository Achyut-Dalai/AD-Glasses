package com.ad_glasses.ai.transcription.moonshine

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Content-free PCM quality telemetry for Moonshine input.
 *
 * This never retains audio samples. It accumulates only signal statistics so device testing can
 * distinguish weak input, close-mic clipping, and decoder/backlog problems without changing the
 * microphone source or applying device-specific gain/processing.
 */
internal class MoonshineAudioDiagnostics {
    private var sampleCount: Long = 0L
    private var sumSquares: Double = 0.0
    private var peakAbsolute: Int = 0
    private var clippedSamples: Long = 0L

    fun add(samples: ShortArray, count: Int) {
        val safeCount = count.coerceIn(0, samples.size)
        for (index in 0 until safeCount) {
            val value = samples[index].toInt()
            val absolute = abs(value)
            sampleCount++
            sumSquares += value.toDouble() * value.toDouble()
            if (absolute > peakAbsolute) peakAbsolute = absolute
            if (absolute >= CLIP_THRESHOLD) clippedSamples++
        }
    }

    fun snapshot(): MoonshineAudioSnapshot {
        val rms = if (sampleCount == 0L) 0.0 else sqrt(sumSquares / sampleCount) / PCM_SCALE
        val peak = peakAbsolute / PCM_SCALE
        val clippingRatio = if (sampleCount == 0L) 0.0 else clippedSamples.toDouble() / sampleCount
        return MoonshineAudioSnapshot(
            sampleCount = sampleCount,
            rmsDbfs = amplitudeToDbfs(rms),
            peakDbfs = amplitudeToDbfs(peak),
            clippingPpm = (clippingRatio * 1_000_000.0).toLong(),
        )
    }

    private fun amplitudeToDbfs(amplitude: Double): Double =
        20.0 * log10(max(amplitude, MIN_AMPLITUDE))

    private companion object {
        const val PCM_SCALE = 32_768.0
        const val CLIP_THRESHOLD = 32_700
        const val MIN_AMPLITUDE = 1e-9
    }
}

internal data class MoonshineAudioSnapshot(
    val sampleCount: Long,
    val rmsDbfs: Double,
    val peakDbfs: Double,
    /** Samples at/near PCM saturation per million captured samples. */
    val clippingPpm: Long,
)

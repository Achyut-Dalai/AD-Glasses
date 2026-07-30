package com.fersaiyan.cyanbridge.plugins.walkingaid

import android.content.Context
import android.graphics.Bitmap
import kotlin.math.abs

/** Estimates global image translation so head turns do not become object motion. */
class WalkingAidCameraMotionEstimator(@Suppress("UNUSED_PARAMETER") context: Context) {
    private data class MotionFrame(
        val timestampMs: Long,
        val luminance: IntArray,
    )

    private val lock = Any()
    private var previousFrame: MotionFrame? = null

    fun start(): Boolean {
        synchronized(lock) { previousFrame = null }
        return true
    }

    fun stop() {
        synchronized(lock) { previousFrame = null }
    }

    fun motionForFrame(bitmap: Bitmap, capturedAtMs: Long): CameraMotionEstimate {
        val current = MotionFrame(capturedAtMs, downsampleLuminance(bitmap))
        synchronized(lock) {
            val previous = previousFrame
            previousFrame = current
            if (previous == null || current.timestampMs <= previous.timestampMs) {
                return CameraMotionEstimate.NONE
            }

            var bestDx = 0
            var bestDy = 0
            var bestError = Float.MAX_VALUE
            var zeroShiftError = Float.MAX_VALUE
            for (dy in -MAX_SHIFT_PIXELS..MAX_SHIFT_PIXELS) {
                for (dx in -MAX_SHIFT_PIXELS..MAX_SHIFT_PIXELS) {
                    val error = translationError(previous.luminance, current.luminance, dx, dy)
                    if (dx == 0 && dy == 0) zeroShiftError = error
                    if (error < bestError) {
                        bestError = error
                        bestDx = dx
                        bestDy = dy
                    }
                }
            }

            if (bestError == Float.MAX_VALUE || zeroShiftError <= 0f) {
                return CameraMotionEstimate.NONE
            }
            val improvement = ((zeroShiftError - bestError) / zeroShiftError).coerceIn(0f, 1f)
            if (improvement < MIN_CONFIDENCE) return CameraMotionEstimate.NONE
            return CameraMotionEstimate(
                deltaXNormalized = bestDx.toFloat() / SAMPLE_WIDTH,
                deltaYNormalized = bestDy.toFloat() / SAMPLE_HEIGHT,
                confidence = improvement,
            )
        }
    }

    private fun downsampleLuminance(bitmap: Bitmap): IntArray {
        val scaled = Bitmap.createScaledBitmap(bitmap, SAMPLE_WIDTH, SAMPLE_HEIGHT, true)
        val colors = IntArray(SAMPLE_WIDTH * SAMPLE_HEIGHT)
        scaled.getPixels(colors, 0, SAMPLE_WIDTH, 0, 0, SAMPLE_WIDTH, SAMPLE_HEIGHT)
        if (scaled !== bitmap) scaled.recycle()
        return IntArray(colors.size) { index ->
            val color = colors[index]
            val red = color shr 16 and 0xFF
            val green = color shr 8 and 0xFF
            val blue = color and 0xFF
            (red * 30 + green * 59 + blue * 11) / 100
        }
    }

    private fun translationError(previous: IntArray, current: IntArray, dx: Int, dy: Int): Float {
        val startX = maxOf(MARGIN, MARGIN - dx)
        val endX = minOf(SAMPLE_WIDTH - MARGIN, SAMPLE_WIDTH - MARGIN - dx)
        val startY = maxOf(MARGIN, MARGIN - dy)
        val endY = minOf(SAMPLE_HEIGHT - MARGIN, SAMPLE_HEIGHT - MARGIN - dy)
        if (startX >= endX || startY >= endY) return Float.MAX_VALUE

        var totalError = 0L
        var samples = 0
        var y = startY
        while (y < endY) {
            var x = startX
            while (x < endX) {
                val previousValue = previous[y * SAMPLE_WIDTH + x]
                val currentValue = current[(y + dy) * SAMPLE_WIDTH + (x + dx)]
                totalError += abs(previousValue - currentValue)
                samples++
                x += SAMPLE_STEP
            }
            y += SAMPLE_STEP
        }
        return if (samples == 0) Float.MAX_VALUE else totalError.toFloat() / samples
    }

    companion object {
        private const val SAMPLE_WIDTH = 48
        private const val SAMPLE_HEIGHT = 36
        private const val SAMPLE_STEP = 2
        private const val MARGIN = 4
        private const val MAX_SHIFT_PIXELS = 8
        private const val MIN_CONFIDENCE = 0.08f
    }
}

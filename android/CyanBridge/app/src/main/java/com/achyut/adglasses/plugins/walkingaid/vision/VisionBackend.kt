package com.achyut.adglasses.plugins.walkingaid.vision

import android.graphics.Bitmap
import android.graphics.RectF

enum class AcceleratorType {
    NPU_COMPILED,
    NPU_ON_DEVICE,
    GPU_DELEGATE,
    XNNPACK_CPU,
}

data class AcceleratorInfo(
    val type: AcceleratorType,
    val delegatedOperators: Int,
    val totalOperators: Int,
    val details: String,
) {
    val delegationPercentage: Float
        get() = if (totalOperators > 0) (delegatedOperators.toFloat() / totalOperators) * 100f else 0f
}

data class VisionFrame(
    val bitmap: Bitmap,
    val timestampMs: Long = System.currentTimeMillis(),
    val captureIndex: Int = 0,
    val sourcePath: String? = null,
)

data class DetectedObject(
    val label: String,
    val confidence: Float,
    val boundingBox: RectF, // Normalized coordinates [0..1] (left, top, right, bottom)
    val position: String,   // "left", "center", "right"
    val relativeDepth: Float? = null,
    val approaching: Boolean = false,
)

data class DetectionResult(
    val objects: List<DetectedObject>,
    val acquisitionTimeMs: Long = 0L,
    val preprocessTimeMs: Long = 0L,
    val inferenceTimeMs: Long = 0L,
    val postprocessTimeMs: Long = 0L,
    val isError: Boolean = false,
    val errorMessage: String? = null,
) {
    val totalTimeMs: Long
        get() = acquisitionTimeMs + preprocessTimeMs + inferenceTimeMs + postprocessTimeMs
}

data class DepthResult(
    val relativeDepthSummary: String,
    val groundDiscontinuityDetected: Boolean,
    val closestRegion: String, // "left", "center", "right"
    val inferenceTimeMs: Long = 0L,
)

interface VisionBackend {
    suspend fun detect(frame: VisionFrame): DetectionResult
    suspend fun estimateDepth(frame: VisionFrame): DepthResult?
    fun acceleratorInfo(): AcceleratorInfo
    fun close()
}

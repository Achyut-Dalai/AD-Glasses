package com.fersaiyan.cyanbridge.plugins.walkingaid

import android.graphics.RectF
import com.fersaiyan.cyanbridge.plugins.walkingaid.vision.DetectedObject
import com.fersaiyan.cyanbridge.plugins.walkingaid.vision.HazardMotionState
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class CameraMotionEstimate(
    /** Expected prior-to-current object displacement caused by camera rotation. */
    val deltaXNormalized: Float = 0f,
    val deltaYNormalized: Float = 0f,
    val confidence: Float = 0f,
) {
    companion object {
        val NONE = CameraMotionEstimate()
    }
}

data class HazardTrackingResult(
    val objects: List<DetectedObject>,
    val clearedTrackIds: List<Long>,
)

/**
 * Lightweight alpha-beta tracker for sparse glasses snapshots.
 *
 * Association uses predicted boxes, class identity, overlap, and center distance. Motion state
 * and TTC use timestamp-normalized angular expansion rather than a per-frame size threshold.
 */
class WalkingAidHazardTracker(
    private val maxMissedFrames: Int = 2,
    private val maxTrackAgeMs: Long = 15_000L,
    private val approachingGrowthRatePerSecond: Float = 0.10f,
    private val recedingGrowthRatePerSecond: Float = -0.10f,
) {
    private data class Track(
        val id: Long,
        val label: String,
        var box: RectF,
        var lastSeenAtMs: Long,
        var velocityXPerSecond: Float = 0f,
        var velocityYPerSecond: Float = 0f,
        var logScaleRatePerSecond: Float = 0f,
        var observationCount: Int = 1,
        var missedFrames: Int = 0,
        var state: HazardMotionState = HazardMotionState.NEW,
    )

    private val tracks = linkedMapOf<Long, Track>()
    private var nextTrackId = 1L

    @Synchronized
    fun update(
        detections: List<DetectedObject>,
        capturedAtMs: Long,
        cameraMotion: CameraMotionEstimate = CameraMotionEstimate.NONE,
    ): HazardTrackingResult {
        val associations = mutableListOf<Triple<Float, Long, Int>>()
        tracks.values.forEach { track ->
            val predicted = predict(track, capturedAtMs, cameraMotion)
            detections.forEachIndexed { index, detection ->
                if (!track.label.equals(detection.label, ignoreCase = true)) return@forEachIndexed
                val iou = intersectionOverUnion(predicted, detection.boundingBox)
                val distance = centerDistance(predicted, detection.boundingBox)
                if (iou >= MIN_ASSOCIATION_IOU || distance <= MAX_CENTER_DISTANCE) {
                    associations += Triple(iou * 2f - distance, track.id, index)
                }
            }
        }

        val assignedTracks = mutableSetOf<Long>()
        val assignedDetections = mutableSetOf<Int>()
        val detectionTrackIds = mutableMapOf<Int, Long>()
        associations.sortedByDescending { it.first }.forEach { (_, trackId, detectionIndex) ->
            if (trackId !in assignedTracks && detectionIndex !in assignedDetections) {
                assignedTracks += trackId
                assignedDetections += detectionIndex
                detectionTrackIds[detectionIndex] = trackId
            }
        }

        val trackedObjects = detections.mapIndexed { index, detection ->
            val trackId = detectionTrackIds[index]
            if (trackId == null) {
                createTrack(detection, capturedAtMs)
            } else {
                updateTrack(requireNotNull(tracks[trackId]), detection, capturedAtMs, cameraMotion)
            }
        }

        val cleared = mutableListOf<Long>()
        tracks.values.toList().forEach { track ->
            if (track.id in assignedTracks || trackedObjects.any { it.trackId == track.id && it.motionState == HazardMotionState.NEW }) {
                return@forEach
            }
            track.missedFrames++
            if (track.missedFrames > maxMissedFrames || capturedAtMs - track.lastSeenAtMs > maxTrackAgeMs) {
                track.state = HazardMotionState.CLEARED
                cleared += track.id
                tracks.remove(track.id)
            }
        }

        return HazardTrackingResult(trackedObjects, cleared)
    }

    @Synchronized
    fun reset() {
        tracks.clear()
        nextTrackId = 1L
    }

    private fun createTrack(detection: DetectedObject, capturedAtMs: Long): DetectedObject {
        val id = nextTrackId++
        tracks[id] = Track(
            id = id,
            label = detection.label,
            box = RectF(detection.boundingBox),
            lastSeenAtMs = capturedAtMs,
        )
        return detection.copy(
            trackId = id,
            approaching = false,
            motionState = HazardMotionState.NEW,
            timeToCollisionSeconds = null,
        )
    }

    private fun updateTrack(
        track: Track,
        detection: DetectedObject,
        capturedAtMs: Long,
        cameraMotion: CameraMotionEstimate,
    ): DetectedObject {
        val elapsedSeconds = ((capturedAtMs - track.lastSeenAtMs).coerceAtLeast(1L) / 1000f)
            .coerceAtMost(MAX_MOTION_INTERVAL_SECONDS)
        val previousBox = RectF(track.box)
        val cameraAdjustedPrevious = offset(
            previousBox,
            cameraMotion.deltaXNormalized * cameraMotion.confidence,
            cameraMotion.deltaYNormalized * cameraMotion.confidence,
        )
        val measuredVelocityX = (centerX(detection.boundingBox) - centerX(cameraAdjustedPrevious)) / elapsedSeconds
        val measuredVelocityY = (centerY(detection.boundingBox) - centerY(cameraAdjustedPrevious)) / elapsedSeconds
        track.velocityXPerSecond = blend(track.velocityXPerSecond, measuredVelocityX, VELOCITY_ALPHA)
        track.velocityYPerSecond = blend(track.velocityYPerSecond, measuredVelocityY, VELOCITY_ALPHA)

        val previousScale = sqrt(area(previousBox).coerceAtLeast(MIN_BOX_AREA))
        val currentScale = sqrt(area(detection.boundingBox).coerceAtLeast(MIN_BOX_AREA))
        val measuredGrowthRate = ln(currentScale / previousScale) / elapsedSeconds
        track.logScaleRatePerSecond = if (track.observationCount <= 1) {
            measuredGrowthRate
        } else {
            blend(track.logScaleRatePerSecond, measuredGrowthRate, SCALE_RATE_ALPHA)
        }

        track.state = when {
            detection.position == "center" && track.logScaleRatePerSecond >= approachingGrowthRatePerSecond ->
                HazardMotionState.APPROACHING
            track.logScaleRatePerSecond <= recedingGrowthRatePerSecond -> HazardMotionState.RECEDING
            else -> HazardMotionState.PERSISTENT
        }
        val ttcSeconds = if (track.state == HazardMotionState.APPROACHING) {
            (1f / track.logScaleRatePerSecond).coerceIn(MIN_TTC_SECONDS, MAX_TTC_SECONDS)
        } else {
            null
        }

        track.box = RectF(detection.boundingBox)
        track.lastSeenAtMs = capturedAtMs
        track.observationCount++
        track.missedFrames = 0

        return detection.copy(
            trackId = track.id,
            approaching = track.state == HazardMotionState.APPROACHING,
            motionState = track.state,
            timeToCollisionSeconds = ttcSeconds,
        )
    }

    private fun predict(track: Track, capturedAtMs: Long, cameraMotion: CameraMotionEstimate): RectF {
        val elapsedSeconds = ((capturedAtMs - track.lastSeenAtMs).coerceAtLeast(0L) / 1000f)
            .coerceAtMost(MAX_MOTION_INTERVAL_SECONDS)
        val scale = exp(track.logScaleRatePerSecond * elapsedSeconds)
            .coerceIn(MIN_PREDICTED_SCALE, MAX_PREDICTED_SCALE)
        val width = track.box.width() * scale
        val height = track.box.height() * scale
        val cx = centerX(track.box) + track.velocityXPerSecond * elapsedSeconds +
            cameraMotion.deltaXNormalized * cameraMotion.confidence
        val cy = centerY(track.box) + track.velocityYPerSecond * elapsedSeconds +
            cameraMotion.deltaYNormalized * cameraMotion.confidence
        return RectF(cx - width / 2f, cy - height / 2f, cx + width / 2f, cy + height / 2f)
    }

    private fun offset(box: RectF, dx: Float, dy: Float): RectF =
        RectF(box.left + dx, box.top + dy, box.right + dx, box.bottom + dy)

    private fun intersectionOverUnion(a: RectF, b: RectF): Float {
        val left = max(a.left, b.left)
        val top = max(a.top, b.top)
        val right = min(a.right, b.right)
        val bottom = min(a.bottom, b.bottom)
        if (right <= left || bottom <= top) return 0f
        val intersection = (right - left) * (bottom - top)
        return intersection / (area(a) + area(b) - intersection).coerceAtLeast(MIN_BOX_AREA)
    }

    private fun centerDistance(a: RectF, b: RectF): Float =
        hypot(centerX(a) - centerX(b), centerY(a) - centerY(b))

    private fun centerX(box: RectF): Float = (box.left + box.right) / 2f
    private fun centerY(box: RectF): Float = (box.top + box.bottom) / 2f
    private fun area(box: RectF): Float = box.width().coerceAtLeast(0f) * box.height().coerceAtLeast(0f)
    private fun blend(previous: Float, measured: Float, alpha: Float): Float = previous * (1f - alpha) + measured * alpha

    companion object {
        private const val MIN_ASSOCIATION_IOU = 0.08f
        private const val MAX_CENTER_DISTANCE = 0.30f
        private const val MAX_MOTION_INTERVAL_SECONDS = 10f
        private const val VELOCITY_ALPHA = 0.55f
        private const val SCALE_RATE_ALPHA = 0.65f
        private const val MIN_PREDICTED_SCALE = 0.5f
        private const val MAX_PREDICTED_SCALE = 2f
        private const val MIN_BOX_AREA = 0.0001f
        private const val MIN_TTC_SECONDS = 0.25f
        private const val MAX_TTC_SECONDS = 30f
    }
}

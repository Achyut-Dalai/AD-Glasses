package com.fersaiyan.cyanbridge.plugins.walkingaid

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import androidx.test.core.app.ApplicationProvider
import com.fersaiyan.cyanbridge.plugins.walkingaid.vision.DetectedObject
import com.fersaiyan.cyanbridge.plugins.walkingaid.vision.HazardMotionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WalkingAidHazardTrackerTest {

    @Test
    fun keepsStableIdAcrossFrames() {
        val tracker = WalkingAidHazardTracker()
        val first = tracker.update(
            listOf(detection(RectF(0.35f, 0.2f, 0.55f, 0.6f))),
            capturedAtMs = 1_000L,
        ).objects.single()
        val second = tracker.update(
            listOf(detection(RectF(0.37f, 0.2f, 0.57f, 0.6f))),
            capturedAtMs = 5_000L,
        ).objects.single()

        assertEquals(first.trackId, second.trackId)
        assertEquals(HazardMotionState.PERSISTENT, second.motionState)
    }

    @Test
    fun estimatesApproachAndTimeToCollisionUsingElapsedTime() {
        val tracker = WalkingAidHazardTracker()
        tracker.update(
            listOf(detection(RectF(0.40f, 0.35f, 0.60f, 0.65f))),
            capturedAtMs = 1_000L,
        )

        val approaching = tracker.update(
            listOf(detection(RectF(0.35f, 0.25f, 0.65f, 0.75f))),
            capturedAtMs = 3_000L,
        ).objects.single()

        assertEquals(HazardMotionState.APPROACHING, approaching.motionState)
        assertTrue(approaching.approaching)
        assertNotNull(approaching.timeToCollisionSeconds)
        assertTrue(requireNotNull(approaching.timeToCollisionSeconds) in 1f..10f)
    }

    @Test
    fun cameraTranslationCompensationPreservesTrack() {
        val tracker = WalkingAidHazardTracker()
        val first = tracker.update(
            listOf(detection(RectF(0.10f, 0.30f, 0.20f, 0.60f))),
            capturedAtMs = 1_000L,
        ).objects.single()

        val shifted = tracker.update(
            detections = listOf(detection(RectF(0.45f, 0.30f, 0.55f, 0.60f))),
            capturedAtMs = 3_000L,
            cameraMotion = CameraMotionEstimate(deltaXNormalized = 0.35f, confidence = 1f),
        ).objects.single()

        assertEquals(first.trackId, shifted.trackId)
    }

    @Test
    fun reportsTrackClearedAfterConfiguredMisses() {
        val tracker = WalkingAidHazardTracker(maxMissedFrames = 1)
        val trackId = tracker.update(
            listOf(detection(RectF(0.35f, 0.2f, 0.55f, 0.6f))),
            capturedAtMs = 1_000L,
        ).objects.single().trackId

        assertTrue(tracker.update(emptyList(), capturedAtMs = 2_000L).clearedTrackIds.isEmpty())
        assertEquals(listOf(trackId), tracker.update(emptyList(), capturedAtMs = 3_000L).clearedTrackIds)
    }

    @Test
    fun imageMotionEstimatorDetectsGlobalHorizontalShift() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val estimator = WalkingAidCameraMotionEstimator(context)
        val first = patternedBitmap(shiftX = 0)
        val second = patternedBitmap(shiftX = 3)

        assertEquals(CameraMotionEstimate.NONE, estimator.motionForFrame(first, 1_000L))
        val motion = estimator.motionForFrame(second, 2_000L)

        assertTrue(motion.deltaXNormalized > 0.03f)
        assertTrue(motion.confidence > 0f)
        first.recycle()
        second.recycle()
    }

    private fun detection(box: RectF): DetectedObject = DetectedObject(
        label = "person",
        confidence = 0.9f,
        boundingBox = box,
        position = "center",
    )

    private fun patternedBitmap(shiftX: Int): Bitmap {
        val width = 48
        val height = 36
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val sourceX = x - shiftX
                val value = if (sourceX in 0 until width) {
                    (sourceX * 37 + y * 61 + sourceX * y * 3) and 0xFF
                } else {
                    0
                }
                bitmap.setPixel(x, y, Color.rgb(value, value, value))
            }
        }
        return bitmap
    }
}

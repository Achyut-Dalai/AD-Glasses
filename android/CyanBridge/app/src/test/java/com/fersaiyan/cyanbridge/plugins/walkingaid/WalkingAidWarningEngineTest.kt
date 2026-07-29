package com.fersaiyan.cyanbridge.plugins.walkingaid

import android.graphics.RectF
import com.fersaiyan.cyanbridge.plugins.walkingaid.vision.DetectedObject
import com.fersaiyan.cyanbridge.plugins.walkingaid.vision.DetectionResult
import org.junit.Assert.assertTrue
import org.junit.Assert.assertArrayEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WalkingAidWarningEngineTest {

    @Before
    fun setUp() {
        WalkingAidWarningEngine.reset()
    }

    @Test
    fun pluralWatchlistTermMatchesSingularCocoLabel() {
        val detection = DetectionResult(
            objects = listOf(
                DetectedObject(
                    label = "cat",
                    confidence = 0.9f,
                    boundingBox = RectF(0.25f, 0.2f, 0.75f, 0.8f),
                    position = "center",
                )
            )
        )

        val decision = WalkingAidWarningEngine.evaluate(
            detection,
            depth = null,
            focusDescription = "Please pay extra attention to cats",
        )

        assertTrue(decision.shouldWarn)
    }

    @Test
    fun qualityThreeUsesChatAiPacketWithoutHomeModeSuffix() {
        val packet = WalkingAidImageCapture.buildChatAiCaptureCommand(3)

        assertArrayEquals(byteArrayOf(0x02, 0x01, 0x06, 0x03, 0x03), packet)
    }
}

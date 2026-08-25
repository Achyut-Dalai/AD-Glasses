package com.ad_glasses.ai.router

import org.junit.Assert.assertEquals
import org.junit.Test

class CloudVisionImagePreprocessorTest {
    @Test
    fun twelve_megapixel_frame_is_subsampled_before_exact_scaling() {
        assertEquals(
            2,
            CloudVisionImagePreprocessor.calculateInSampleSize(
                width = 4_000,
                height = 3_000,
                maxDimension = 1_024,
            ),
        )
    }

    @Test
    fun text_detail_keeps_more_source_pixels_than_standard_scene_mode() {
        // Use a large enough frame that the power-of-two decoder sampling itself differs. A
        // 4000x3000 frame happens to decode at 1/2 for both targets and differs only in exact scale.
        val standard = CloudVisionImagePreprocessor.calculateInSampleSize(5_000, 4_000, 1_024)
        val textDetail = CloudVisionImagePreprocessor.calculateInSampleSize(5_000, 4_000, 1_600)

        assertEquals(4, standard)
        assertEquals(2, textDetail)
    }

    @Test
    fun already_small_image_does_not_request_subsampling() {
        assertEquals(1, CloudVisionImagePreprocessor.calculateInSampleSize(800, 600, 1_024))
    }
}

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
        val standard = CloudVisionImagePreprocessor.calculateInSampleSize(4_000, 3_000, 1_024)
        val textDetail = CloudVisionImagePreprocessor.calculateInSampleSize(4_000, 3_000, 1_600)

        assertEquals(2, standard)
        assertEquals(1, textDetail)
    }

    @Test
    fun already_small_image_does_not_request_subsampling() {
        assertEquals(1, CloudVisionImagePreprocessor.calculateInSampleSize(800, 600, 1_024))
    }
}

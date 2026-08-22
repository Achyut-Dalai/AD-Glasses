package com.ad_glasses.ai.image

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageQuestionContractsTest {
    @Test
    fun detailedBlePreviewIsDefaultAndFallbackRequiresAnExplicitChoice() {
        assertEquals(ImageQuestionSource.FAST_PREVIEW, ImageQuestionSourcePolicy.defaultSource())
        assertEquals(ImageThumbnailQuality.DETAILED, ImageQuestionSourcePolicy.defaultThumbnailQuality())
        assertEquals(
            ImageSourceResolution.AWAITING_EXPLICIT_FALLBACK_CHOICE,
            ImageQuestionSourcePolicy.onHighQualityFailure(),
        )
        assertEquals(
            ImageSourceResolution.FAST_PREVIEW,
            ImageQuestionSourcePolicy.resolveHighQualityFailure(HighQualityFailureChoice.USE_FAST_PREVIEW),
        )
    }

    @Test
    fun officialBleThumbnailChoicesCoverTheVerifiedZeroToFiveRange() {
        assertEquals(
            listOf(0, 1, 2, 3, 4, 5),
            ImageThumbnailQuality.entries.map(ImageThumbnailQuality::sdkValue),
        )
    }
}

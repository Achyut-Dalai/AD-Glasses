package com.fersaiyan.cyanbridge.ai.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun automationCallbacksMustArriveInOrderBeforeAnswerIsReady() {
        val idle = ExternalImageAutomationState()
        val started = ExternalImageAutomationStateMachine.transition(
            idle,
            ExternalImageAutomationStage.IMAGE_STARTED,
        )
        val attached = ExternalImageAutomationStateMachine.transition(
            started,
            ExternalImageAutomationStage.IMAGE_ATTACHED,
        )
        val sent = ExternalImageAutomationStateMachine.transition(
            attached,
            ExternalImageAutomationStage.PROMPT_SENT,
        )
        val answered = ExternalImageAutomationStateMachine.transition(
            sent,
            ExternalImageAutomationStage.ANSWER_READY,
        )

        assertEquals(ExternalImageAutomationStage.ANSWER_READY, answered.stage)
        assertEquals(idle, ExternalImageAutomationStateMachine.transition(idle, ExternalImageAutomationStage.ANSWER_READY))
        assertNull(answered.error)
    }
}

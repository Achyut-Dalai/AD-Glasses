package com.ad_glasses.shared.ai

import kotlin.test.Test
import kotlin.test.assertEquals

class AiTurnPolicyTest {
    @Test
    fun ordinary_conversation_stays_concise() {
        assertEquals(AiReasoningMode.CONCISE, AiTurnPolicy.reasoningMode("Java vs Python"))
        assertEquals(AiReasoningMode.CONCISE, AiTurnPolicy.reasoningMode("What is a computer?"))
        assertEquals(AiReasoningMode.CONCISE, AiTurnPolicy.reasoningMode("12 times 3"))
        assertEquals(AiReasoningMode.CONCISE, AiTurnPolicy.reasoningMode("Compare Java and Python for beginners"))
        assertEquals(AiResponseMode.CONCISE, AiTurnPolicy.responseMode("Java vs Python"))
    }

    @Test
    fun deeper_reasoning_is_only_selected_by_explicit_intent() {
        assertEquals(
            AiReasoningMode.REASONED,
            AiTurnPolicy.reasoningMode("Think carefully and compare the tradeoffs before deciding."),
        )
        assertEquals(
            AiReasoningMode.REASONED,
            AiTurnPolicy.reasoningMode("Reason step by step about why this race condition occurs."),
        )
        assertEquals(
            AiReasoningMode.REASONED,
            AiTurnPolicy.reasoningMode("anything", forceReasoning = true),
        )
    }

    @Test
    fun explicit_full_text_extraction_is_separate_from_reasoning() {
        val prompt = "Read all the text on this receipt"

        assertEquals(AiResponseMode.TEXT_EXTRACTION, AiTurnPolicy.responseMode(prompt, hasImage = true))
        assertEquals(AiReasoningMode.CONCISE, AiTurnPolicy.reasoningMode(prompt))
        assertEquals(AiResponseMode.CONCISE, AiTurnPolicy.responseMode(prompt, hasImage = false))
    }

    @Test
    fun short_visual_reading_stays_concise() {
        assertEquals(
            AiResponseMode.CONCISE,
            AiTurnPolicy.responseMode("What does this sign say?", hasImage = true),
        )
        assertEquals(AiVisionDetail.TEXT_DETAIL, AiTurnPolicy.visionDetail("What does this sign say?"))
    }

    @Test
    fun normal_lens_questions_use_standard_detail() {
        assertEquals(AiVisionDetail.STANDARD, AiTurnPolicy.visionDetail("What am I looking at?"))
        assertEquals(AiVisionDetail.STANDARD, AiTurnPolicy.visionDetail("What color is this object?"))
    }

    @Test
    fun text_and_document_lens_questions_keep_more_detail() {
        assertEquals(AiVisionDetail.TEXT_DETAIL, AiTurnPolicy.visionDetail("Read all the text on this menu"))
        assertEquals(AiVisionDetail.TEXT_DETAIL, AiTurnPolicy.visionDetail("What does this screen say?"))
        assertEquals(AiVisionDetail.TEXT_DETAIL, AiTurnPolicy.visionDetail("Extract text from this receipt"))
        assertEquals(AiVisionDetail.TEXT_DETAIL, AiTurnPolicy.visionDetail("Describe every detail in this diagram"))
    }
}

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
    fun normal_lens_questions_use_standard_detail() {
        assertEquals(AiVisionDetail.STANDARD, AiTurnPolicy.visionDetail("What am I looking at?"))
        assertEquals(AiVisionDetail.STANDARD, AiTurnPolicy.visionDetail("What color is this object?"))
    }

    @Test
    fun text_and_document_lens_questions_keep_more_detail() {
        assertEquals(AiVisionDetail.TEXT_DETAIL, AiTurnPolicy.visionDetail("Read all the text on this menu"))
        assertEquals(AiVisionDetail.TEXT_DETAIL, AiTurnPolicy.visionDetail("What does this screen say?"))
        assertEquals(AiVisionDetail.TEXT_DETAIL, AiTurnPolicy.visionDetail("Extract text from this receipt"))
    }
}

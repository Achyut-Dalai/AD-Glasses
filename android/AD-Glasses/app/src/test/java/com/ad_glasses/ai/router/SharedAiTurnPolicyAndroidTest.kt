package com.ad_glasses.ai.router

import com.ad_glasses.shared.ai.AiReasoningMode
import com.ad_glasses.shared.ai.AiResponseMode
import com.ad_glasses.shared.ai.AiTurnPolicy
import com.ad_glasses.shared.ai.AiVisionDetail
import org.junit.Assert.assertEquals
import org.junit.Test

class SharedAiTurnPolicyAndroidTest {
    @Test
    fun ordinary_chat_and_comparison_do_not_enable_reasoning() {
        assertEquals(AiReasoningMode.CONCISE, AiTurnPolicy.reasoningMode("Java vs Python"))
        assertEquals(AiReasoningMode.CONCISE, AiTurnPolicy.reasoningMode("What is a computer?"))
        assertEquals(AiReasoningMode.CONCISE, AiTurnPolicy.reasoningMode("12 times 3"))
    }

    @Test
    fun explicit_deep_reasoning_gets_reasoned_mode() {
        assertEquals(
            AiReasoningMode.REASONED,
            AiTurnPolicy.reasoningMode("Think carefully and reason step by step about this race condition"),
        )
    }

    @Test
    fun lens_text_detail_and_output_size_are_independent() {
        assertEquals(AiVisionDetail.TEXT_DETAIL, AiTurnPolicy.visionDetail("What does this sign say?"))
        assertEquals(
            AiResponseMode.CONCISE,
            AiTurnPolicy.responseMode("What does this sign say?", hasImage = true),
        )
        assertEquals(
            AiResponseMode.TEXT_EXTRACTION,
            AiTurnPolicy.responseMode("Read all the text on this receipt", hasImage = true),
        )
    }
}

package com.ad_glasses.ai.router

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiWireBudgetTest {
    @Test
    fun gemini3_normal_turn_keeps_product_budget_but_gets_bounded_wire_headroom() {
        val profile = googleProfile("gemini-3.7-flash")
        val visible = CloudModelPolicy.generationTokenLimit(CloudGenerationMode.CONCISE_CONVERSATION)

        assertEquals(512, visible)
        assertEquals(
            4_096,
            geminiWireMaxOutputTokens(
                profile = profile,
                generationMode = CloudGenerationMode.CONCISE_CONVERSATION,
                visibleOutputTokens = visible,
            ),
        )
    }

    @Test
    fun gemini3_reasoned_turn_has_larger_but_bounded_wire_runway() {
        val profile = googleProfile("gemini-3.7-flash")
        val visible = CloudModelPolicy.generationTokenLimit(CloudGenerationMode.REASONED_CONVERSATION)

        assertEquals(2_048, visible)
        assertEquals(
            8_192,
            geminiWireMaxOutputTokens(
                profile = profile,
                generationMode = CloudGenerationMode.REASONED_CONVERSATION,
                visibleOutputTokens = visible,
            ),
        )
    }

    @Test
    fun gemini25_numeric_thinking_budget_is_added_to_visible_answer_budget() {
        val profile = googleProfile("gemini-2.5-flash")

        assertEquals(
            2_048,
            geminiWireMaxOutputTokens(
                profile = profile,
                generationMode = CloudGenerationMode.CONCISE_CONVERSATION,
                visibleOutputTokens = 512,
            ),
        )
        assertEquals(
            7_168,
            geminiWireMaxOutputTokens(
                profile = profile,
                generationMode = CloudGenerationMode.REASONED_CONVERSATION,
                visibleOutputTokens = 2_048,
            ),
        )
    }

    @Test
    fun default_current_gemini_models_get_bounded_headroom_without_forced_thinking_control() {
        val profile = googleProfile("gemini-3.5-flash")

        assertEquals(
            4_096,
            geminiWireMaxOutputTokens(
                profile = profile,
                generationMode = CloudGenerationMode.DEFAULT,
                visibleOutputTokens = 2_048,
            ),
        )
    }

    @Test
    fun unknown_legacy_gemini_model_does_not_get_guessed_headroom() {
        val profile = googleProfile("gemini-1.5-flash")

        assertEquals(
            512,
            geminiWireMaxOutputTokens(
                profile = profile,
                generationMode = CloudGenerationMode.CONCISE_CONVERSATION,
                visibleOutputTokens = 512,
            ),
        )
    }

    @Test
    fun regression_thinking_budget_can_no_longer_consume_entire_visible_ceiling() {
        val profile = googleProfile("gemini-2.5-pro")
        val wire = geminiWireMaxOutputTokens(
            profile = profile,
            generationMode = CloudGenerationMode.CONCISE_CONVERSATION,
            visibleOutputTokens = 512,
        )
        val thinking = CloudModelPolicy.requestTuning(
            profile,
            CloudGenerationMode.CONCISE_CONVERSATION,
        ).geminiThinkingBudget ?: 0

        assertTrue(wire > thinking)
        assertTrue(wire - thinking >= 512)
    }

    @Test
    fun gemini_wire_budget_never_exceeds_adapter_hard_cap() {
        val profile = googleProfile("gemini-2.5-pro")

        assertEquals(
            16_384,
            geminiWireMaxOutputTokens(
                profile = profile,
                generationMode = CloudGenerationMode.REASONED_CONVERSATION,
                visibleOutputTokens = 20_000,
            ),
        )
    }

    private fun googleProfile(model: String): CloudAiProfile = CloudAiProfile(
        id = "gemini-test",
        name = "Gemini Test",
        provider = ApiProvider.GOOGLE,
        baseUrl = ApiProvider.GOOGLE.defaultBaseUrl,
        model = model,
    )
}

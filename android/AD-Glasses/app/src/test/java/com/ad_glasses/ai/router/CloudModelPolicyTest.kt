package com.ad_glasses.ai.router

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudModelPolicyTest {
    private val concise = CloudGenerationMode.CONCISE_CONVERSATION
    private val reasoned = CloudGenerationMode.REASONED_CONVERSATION

    @Test
    fun generation_budget_depends_only_on_product_intent() {
        assertEquals(512, CloudModelPolicy.generationTokenLimit(concise))
        assertEquals(2_048, CloudModelPolicy.generationTokenLimit(reasoned))
        assertEquals(512, CloudModelPolicy.generationTokenLimit(CloudGenerationMode.DEFAULT))
    }

    @Test
    fun provider_specific_token_field_is_transport_shape_not_budget_policy() {
        assertEquals(
            "max_completion_tokens",
            CloudModelPolicy.requestTuning(profile(ApiProvider.GROQ, "openai/gpt-oss-20b"), concise)
                .completionTokenField,
        )
        assertEquals(
            "max_completion_tokens",
            CloudModelPolicy.requestTuning(profile(ApiProvider.OPENAI, "gpt-5.6-terra"), concise)
                .completionTokenField,
        )
        assertEquals(
            "max_tokens",
            CloudModelPolicy.requestTuning(profile(ApiProvider.OPENROUTER, "openai/gpt-5.6-terra"), concise)
                .completionTokenField,
        )
    }

    @Test
    fun openai_reasoning_model_gets_light_normal_reasoning_without_forced_verbosity() {
        val profile = profile(ApiProvider.OPENAI, "gpt-5.6-terra")

        val normal = CloudModelPolicy.requestTuning(profile, concise)
        val deep = CloudModelPolicy.requestTuning(profile, reasoned)

        assertEquals("low", normal.reasoningEffort)
        assertEquals("medium", deep.reasoningEffort)
        assertNull(normal.responseVerbosity)
        assertNull(deep.responseVerbosity)
    }

    @Test
    fun groq_qwen36_stays_non_reasoning_normally_and_hides_reasoning_when_requested() {
        val profile = profile(ApiProvider.GROQ, "qwen/qwen3.6-27b")

        val normal = CloudModelPolicy.requestTuning(profile, concise)
        val deep = CloudModelPolicy.requestTuning(profile, reasoned)
        assertEquals("none", normal.reasoningEffort)
        assertEquals("default", deep.reasoningEffort)
        assertEquals("hidden", normal.reasoningFormat)
        assertEquals("hidden", deep.reasoningFormat)
        assertNull(normal.includeReasoning)
        assertNull(deep.includeReasoning)

        val heartbeat = CloudModelPolicy.requestTuning(
            profile = profile,
            mode = reasoned,
            includeReasoningActivity = true,
        )
        assertEquals("parsed", heartbeat.reasoningFormat)
    }

    @Test
    fun groq_gpt_oss_uses_light_normal_and_medium_explicit_reasoning() {
        val profile = profile(ApiProvider.GROQ, "openai/gpt-oss-20b")

        val normal = CloudModelPolicy.requestTuning(profile, concise)
        val deep = CloudModelPolicy.requestTuning(profile, reasoned)
        assertEquals("low", normal.reasoningEffort)
        assertEquals("medium", deep.reasoningEffort)
        assertNull(normal.reasoningFormat)
        assertNull(deep.reasoningFormat)
        assertEquals(false, normal.includeReasoning)
        assertEquals(false, deep.includeReasoning)

        val heartbeat = CloudModelPolicy.requestTuning(
            profile = profile,
            mode = concise,
            includeReasoningActivity = true,
        )
        assertEquals(true, heartbeat.includeReasoning)
    }

    @Test
    fun gemini3_reasoning_controls_change_without_changing_generation_budget() {
        val profile = profile(ApiProvider.GOOGLE, "gemini-3.7-flash")

        assertEquals(512, CloudModelPolicy.generationTokenLimit(concise))
        assertEquals(2_048, CloudModelPolicy.generationTokenLimit(reasoned))
        assertEquals("low", CloudModelPolicy.requestTuning(profile, concise).geminiThinkingLevel)
        assertEquals("medium", CloudModelPolicy.requestTuning(profile, reasoned).geminiThinkingLevel)
    }

    @Test
    fun gemini25_keeps_reasoning_bounded_by_product_intent() {
        val flash = profile(ApiProvider.GOOGLE, "gemini-2.5-flash")
        val pro = profile(ApiProvider.GOOGLE, "gemini-2.5-pro")

        listOf(flash, pro).forEach { candidate ->
            assertEquals(1_024, CloudModelPolicy.requestTuning(candidate, concise).geminiThinkingBudget)
            assertEquals(4_096, CloudModelPolicy.requestTuning(candidate, reasoned).geminiThinkingBudget)
        }
    }

    @Test
    fun deepseek_keeps_thinking_on_but_changes_effort_by_product_intent() {
        val profile = profile(ApiProvider.DEEPSEEK, "deepseek-v4-flash")

        val normal = CloudModelPolicy.requestTuning(profile, concise)
        val deep = CloudModelPolicy.requestTuning(profile, reasoned)
        assertEquals("enabled", normal.deepSeekThinkingType)
        assertEquals("low", normal.reasoningEffort)
        assertEquals("enabled", deep.deepSeekThinkingType)
        assertEquals("high", deep.reasoningEffort)
    }

    @Test
    fun openrouter_reasoning_controls_remain_capability_mapping_only() {
        val profile = profile(ApiProvider.OPENROUTER, "openai/gpt-5.6-terra")

        val normal = CloudModelPolicy.requestTuning(profile, concise)
        val deep = CloudModelPolicy.requestTuning(
            profile = profile,
            mode = reasoned,
            includeReasoningActivity = true,
        )
        assertEquals("low", normal.openRouterReasoningEffort)
        assertTrue(normal.excludeReasoning)
        assertEquals("medium", deep.openRouterReasoningEffort)
        assertFalse(deep.excludeReasoning)
    }

    @Test
    fun custom_model_gets_no_guessed_reasoning_controls() {
        val profile = profile(ApiProvider.CUSTOM, "my-private-model")

        val tuning = CloudModelPolicy.requestTuning(profile, concise)
        assertEquals("max_tokens", tuning.completionTokenField)
        assertNull(tuning.reasoningEffort)
        assertNull(tuning.reasoningFormat)
        assertNull(tuning.includeReasoning)
        assertNull(tuning.openRouterReasoningEffort)
        assertNull(tuning.deepSeekThinkingType)
    }

    @Test
    fun default_generation_mode_does_not_force_reasoning_policy() {
        val profile = profile(ApiProvider.OPENAI, "gpt-5")

        val tuning = CloudModelPolicy.requestTuning(profile, CloudGenerationMode.DEFAULT)

        assertNull(tuning.reasoningEffort)
        assertNull(tuning.responseVerbosity)
        assertEquals("max_completion_tokens", tuning.completionTokenField)
    }

    private fun profile(provider: ApiProvider, model: String): CloudAiProfile = CloudAiProfile(
        id = "test",
        name = "Test",
        provider = provider,
        baseUrl = provider.defaultBaseUrl,
        model = model,
    )
}

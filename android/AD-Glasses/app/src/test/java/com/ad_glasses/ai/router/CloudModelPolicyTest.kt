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
    fun ordinary_groq_model_uses_96_token_concise_ceiling_without_reasoning() {
        val profile = profile(ApiProvider.GROQ, "llama-3.3-70b-versatile")

        assertEquals(96, CloudModelPolicy.generationTokenLimit(profile, concise))
        val tuning = CloudModelPolicy.requestTuning(profile, concise)
        assertEquals("max_completion_tokens", tuning.completionTokenField)
        assertNull(tuning.reasoningEffort)
        assertNull(tuning.reasoningFormat)
        assertNull(tuning.includeReasoning)
    }

    @Test
    fun gpt5_base_uses_minimal_reasoning_only_because_it_cannot_disable_it() {
        val profile = profile(ApiProvider.OPENAI, "gpt-5")

        assertEquals(256, CloudModelPolicy.generationTokenLimit(profile, concise))
        val tuning = CloudModelPolicy.requestTuning(profile, concise)
        assertEquals("minimal", tuning.reasoningEffort)
        assertEquals("low", tuning.responseVerbosity)
    }

    @Test
    fun newer_gpt5_family_disables_reasoning_for_normal_conversation() {
        val profile = profile(ApiProvider.OPENAI, "gpt-5.6-terra")

        assertEquals(96, CloudModelPolicy.generationTokenLimit(profile, concise))
        val tuning = CloudModelPolicy.requestTuning(profile, concise)
        assertEquals("none", tuning.reasoningEffort)
        assertEquals("low", tuning.responseVerbosity)
    }

    @Test
    fun openai_chat_model_does_not_receive_reasoning_parameter() {
        val profile = profile(ApiProvider.OPENAI, "gpt-5-chat-latest")

        assertEquals(96, CloudModelPolicy.generationTokenLimit(profile, concise))
        assertNull(CloudModelPolicy.requestTuning(profile, concise).reasoningEffort)
    }

    @Test
    fun explicit_reasoning_raises_shared_budget_and_effort_for_gpt5() {
        val profile = profile(ApiProvider.OPENAI, "gpt-5.6-terra")

        assertEquals(1_024, CloudModelPolicy.generationTokenLimit(profile, reasoned))
        val tuning = CloudModelPolicy.requestTuning(profile, reasoned)
        assertEquals("medium", tuning.reasoningEffort)
        assertEquals("low", tuning.responseVerbosity)
    }

    @Test
    fun pro_model_is_never_mistaken_for_a_cheap_conversational_model() {
        val profile = profile(ApiProvider.OPENAI, "gpt-5.4-pro")

        assertEquals(512, CloudModelPolicy.generationTokenLimit(profile, concise))
        assertEquals(2_048, CloudModelPolicy.generationTokenLimit(profile, reasoned))
        assertNull(CloudModelPolicy.requestTuning(profile, concise).reasoningEffort)
    }

    @Test
    fun groq_qwen36_switches_between_non_thinking_and_reasoning_modes() {
        val profile = profile(ApiProvider.GROQ, "qwen/qwen3.6-27b")

        assertEquals(96, CloudModelPolicy.generationTokenLimit(profile, concise))
        val normal = CloudModelPolicy.requestTuning(profile, concise)
        val deep = CloudModelPolicy.requestTuning(profile, reasoned)
        assertEquals("none", normal.reasoningEffort)
        assertEquals("default", deep.reasoningEffort)
        assertEquals("hidden", normal.reasoningFormat)
        assertEquals("hidden", deep.reasoningFormat)
        assertNull(normal.includeReasoning)
        assertNull(deep.includeReasoning)
    }

    @Test
    fun groq_qwen36_reasoned_stream_can_return_parsed_reasoning_only_for_heartbeat() {
        val profile = profile(ApiProvider.GROQ, "qwen/qwen3.6-27b")

        val tuning = CloudModelPolicy.requestTuning(
            profile = profile,
            mode = reasoned,
            includeReasoningActivity = true,
        )

        assertEquals("default", tuning.reasoningEffort)
        assertEquals("parsed", tuning.reasoningFormat)
    }

    @Test
    fun groq_gpt_oss_uses_include_reasoning_instead_of_unsupported_reasoning_format() {
        val profile = profile(ApiProvider.GROQ, "openai/gpt-oss-20b")

        assertEquals(256, CloudModelPolicy.generationTokenLimit(profile, concise))
        val normal = CloudModelPolicy.requestTuning(profile, concise)
        val deep = CloudModelPolicy.requestTuning(profile, reasoned)
        assertEquals("low", normal.reasoningEffort)
        assertEquals("medium", deep.reasoningEffort)
        assertNull(normal.reasoningFormat)
        assertNull(deep.reasoningFormat)
        assertEquals(false, normal.includeReasoning)
        assertEquals(false, deep.includeReasoning)
    }

    @Test
    fun groq_gpt_oss_stream_exposes_reasoning_only_to_activity_watchdog() {
        val profile = profile(ApiProvider.GROQ, "openai/gpt-oss-20b")

        val tuning = CloudModelPolicy.requestTuning(
            profile = profile,
            mode = concise,
            includeReasoningActivity = true,
        )

        assertNull(tuning.reasoningFormat)
        assertEquals(true, tuning.includeReasoning)
    }

    @Test
    fun deepseek_v4_turns_thinking_off_for_normal_conversation() {
        val profile = profile(ApiProvider.DEEPSEEK, "deepseek-v4-flash")

        assertEquals(96, CloudModelPolicy.generationTokenLimit(profile, concise))
        val normal = CloudModelPolicy.requestTuning(profile, concise)
        assertEquals("disabled", normal.deepSeekThinkingType)
        assertNull(normal.reasoningEffort)

        val deep = CloudModelPolicy.requestTuning(profile, reasoned)
        assertEquals("enabled", deep.deepSeekThinkingType)
        assertEquals("high", deep.reasoningEffort)
    }

    @Test
    fun openrouter_default_gemma_stays_cheap_and_non_reasoning() {
        val profile = profile(ApiProvider.OPENROUTER, "google/gemma-4-26b-a4b-it:free")

        assertEquals(96, CloudModelPolicy.generationTokenLimit(profile, concise))
        val tuning = CloudModelPolicy.requestTuning(profile, concise)
        assertNull(tuning.openRouterReasoningEffort)
        assertFalse(tuning.excludeReasoning)
    }

    @Test
    fun openrouter_non_reasoning_model_is_not_forced_into_reasoning_on_deep_request() {
        val profile = profile(ApiProvider.OPENROUTER, "google/gemma-4-26b-a4b-it:free")

        val tuning = CloudModelPolicy.requestTuning(profile, reasoned)

        assertNull(tuning.openRouterReasoningEffort)
        assertFalse(tuning.excludeReasoning)
    }

    @Test
    fun openrouter_newer_gpt5_disables_reasoning_for_normal_chat() {
        val profile = profile(ApiProvider.OPENROUTER, "openai/gpt-5.6-terra")

        assertEquals(96, CloudModelPolicy.generationTokenLimit(profile, concise))
        val tuning = CloudModelPolicy.requestTuning(profile, concise)
        assertEquals("none", tuning.openRouterReasoningEffort)
        assertTrue(tuning.excludeReasoning)
    }

    @Test
    fun openrouter_reasoning_stream_can_return_structured_reasoning_for_heartbeat() {
        val profile = profile(ApiProvider.OPENROUTER, "openai/gpt-5.6-terra")

        val tuning = CloudModelPolicy.requestTuning(
            profile = profile,
            mode = reasoned,
            includeReasoningActivity = true,
        )

        assertEquals("medium", tuning.openRouterReasoningEffort)
        assertFalse(tuning.excludeReasoning)
    }

    @Test
    fun gemini37_keeps_96_visible_tokens_while_thinking_level_changes() {
        val profile = profile(ApiProvider.GOOGLE, "gemini-3.7-flash")

        assertEquals(96, CloudModelPolicy.generationTokenLimit(profile, concise))
        assertEquals(96, CloudModelPolicy.generationTokenLimit(profile, reasoned))
        assertEquals("low", CloudModelPolicy.requestTuning(profile, concise).geminiThinkingLevel)
        assertEquals("medium", CloudModelPolicy.requestTuning(profile, reasoned).geminiThinkingLevel)
    }

    @Test
    fun gemini36_uses_minimal_but_keeps_same_visible_output_ceiling() {
        val profile = profile(ApiProvider.GOOGLE, "gemini-3.6-flash")

        assertEquals(96, CloudModelPolicy.generationTokenLimit(profile, concise))
        assertEquals(96, CloudModelPolicy.generationTokenLimit(profile, reasoned))
        assertEquals("minimal", CloudModelPolicy.requestTuning(profile, concise).geminiThinkingLevel)
    }

    @Test
    fun gemini31_flash_lite_image_never_receives_unsupported_medium_thinking() {
        val profile = profile(ApiProvider.GOOGLE, "gemini-3.1-flash-lite-image")

        assertEquals(96, CloudModelPolicy.generationTokenLimit(profile, concise))
        assertEquals("minimal", CloudModelPolicy.requestTuning(profile, concise).geminiThinkingLevel)
        assertEquals("high", CloudModelPolicy.requestTuning(profile, reasoned).geminiThinkingLevel)
    }

    @Test
    fun gemini25_flash_disables_thinking_and_keeps_96_visible_tokens() {
        val profile = profile(ApiProvider.GOOGLE, "gemini-2.5-flash")

        assertEquals(96, CloudModelPolicy.generationTokenLimit(profile, concise))
        assertEquals(96, CloudModelPolicy.generationTokenLimit(profile, reasoned))
        assertEquals(0, CloudModelPolicy.requestTuning(profile, concise).geminiThinkingBudget)
        assertEquals(1_024, CloudModelPolicy.requestTuning(profile, reasoned).geminiThinkingBudget)
    }

    @Test
    fun gemini25_pro_keeps_96_visible_tokens_and_separate_minimum_thinking_budget() {
        val profile = profile(ApiProvider.GOOGLE, "gemini-2.5-pro")

        assertEquals(96, CloudModelPolicy.generationTokenLimit(profile, concise))
        assertEquals(96, CloudModelPolicy.generationTokenLimit(profile, reasoned))
        assertEquals(128, CloudModelPolicy.requestTuning(profile, concise).geminiThinkingBudget)
    }

    @Test
    fun custom_model_gets_no_guessed_reasoning_controls() {
        val profile = profile(ApiProvider.CUSTOM, "my-private-model")

        assertEquals(128, CloudModelPolicy.generationTokenLimit(profile, concise))
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

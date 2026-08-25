package com.ad_glasses.ai.router

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudModelPolicyTest {
    @Test
    fun ordinary_groq_model_gets_small_visible_answer_ceiling_without_reasoning_fields() {
        val profile = profile(ApiProvider.GROQ, "llama-3.3-70b-versatile")

        assertEquals(128, CloudModelPolicy.conciseConversationTokenLimit(profile))
        val tuning = CloudModelPolicy.requestTuning(profile, 128)
        assertEquals("max_completion_tokens", tuning.completionTokenField)
        assertNull(tuning.reasoningEffort)
        assertNull(tuning.reasoningFormat)
    }

    @Test
    fun openai_reasoning_model_keeps_headroom_but_requests_low_reasoning() {
        val profile = profile(ApiProvider.OPENAI, "gpt-5")

        assertEquals(512, CloudModelPolicy.conciseConversationTokenLimit(profile))
        val tuning = CloudModelPolicy.requestTuning(profile, 512)
        assertEquals("max_completion_tokens", tuning.completionTokenField)
        assertEquals("low", tuning.reasoningEffort)
        assertEquals("low", tuning.responseVerbosity)
    }

    @Test
    fun groq_qwen36_disables_reasoning_so_small_ceiling_is_safe() {
        val profile = profile(ApiProvider.GROQ, "qwen/qwen3.6-27b")

        assertEquals(128, CloudModelPolicy.conciseConversationTokenLimit(profile))
        val tuning = CloudModelPolicy.requestTuning(profile, 128)
        assertEquals("none", tuning.reasoningEffort)
        assertEquals("hidden", tuning.reasoningFormat)
    }

    @Test
    fun groq_gpt_oss_uses_low_hidden_reasoning_and_keeps_headroom() {
        val profile = profile(ApiProvider.GROQ, "openai/gpt-oss-20b")

        assertEquals(512, CloudModelPolicy.conciseConversationTokenLimit(profile))
        val tuning = CloudModelPolicy.requestTuning(profile, 512)
        assertEquals("low", tuning.reasoningEffort)
        assertEquals("hidden", tuning.reasoningFormat)
    }

    @Test
    fun default_openrouter_gemma_stays_small_and_does_not_invent_reasoning_controls() {
        val profile = profile(ApiProvider.OPENROUTER, "google/gemma-4-26b-a4b-it:free")

        assertEquals(128, CloudModelPolicy.conciseConversationTokenLimit(profile))
        val tuning = CloudModelPolicy.requestTuning(profile, 128)
        assertNull(tuning.openRouterReasoningEffort)
        assertFalse(tuning.excludeReasoning)
    }

    @Test
    fun openrouter_reasoning_model_requests_low_reasoning_and_excludes_it_from_output() {
        val profile = profile(ApiProvider.OPENROUTER, "openai/gpt-5")

        assertEquals(512, CloudModelPolicy.conciseConversationTokenLimit(profile))
        val tuning = CloudModelPolicy.requestTuning(profile, 512)
        assertEquals("low", tuning.openRouterReasoningEffort)
        assertTrue(tuning.excludeReasoning)
    }

    @Test
    fun gemini37_uses_supported_low_thinking_not_unsupported_minimal() {
        val profile = profile(ApiProvider.GOOGLE, "gemini-3.7-flash")

        assertEquals(512, CloudModelPolicy.conciseConversationTokenLimit(profile))
        val tuning = CloudModelPolicy.requestTuning(profile, 512)
        assertEquals("low", tuning.geminiThinkingLevel)
        assertNull(tuning.geminiThinkingBudget)
    }

    @Test
    fun gemini25_flash_disables_thinking_and_can_use_small_ceiling() {
        val profile = profile(ApiProvider.GOOGLE, "gemini-2.5-flash")

        assertEquals(128, CloudModelPolicy.conciseConversationTokenLimit(profile))
        val tuning = CloudModelPolicy.requestTuning(profile, 128)
        assertEquals(0, tuning.geminiThinkingBudget)
        assertNull(tuning.geminiThinkingLevel)
    }

    @Test
    fun gemini25_pro_uses_documented_minimum_thinking_budget_and_keeps_headroom() {
        val profile = profile(ApiProvider.GOOGLE, "gemini-2.5-pro")

        assertEquals(512, CloudModelPolicy.conciseConversationTokenLimit(profile))
        val tuning = CloudModelPolicy.requestTuning(profile, 512)
        assertEquals(128, tuning.geminiThinkingBudget)
    }

    @Test
    fun custom_model_stays_conservative_and_receives_no_guessed_reasoning_controls() {
        val profile = profile(ApiProvider.CUSTOM, "my-private-model")

        assertEquals(512, CloudModelPolicy.conciseConversationTokenLimit(profile))
        val tuning = CloudModelPolicy.requestTuning(profile, 512)
        assertEquals("max_tokens", tuning.completionTokenField)
        assertNull(tuning.reasoningEffort)
        assertNull(tuning.openRouterReasoningEffort)
    }

    @Test
    fun large_non_conversational_request_does_not_force_low_reasoning_policy() {
        val profile = profile(ApiProvider.OPENAI, "gpt-5")

        val tuning = CloudModelPolicy.requestTuning(profile, 2_048)

        assertNull(tuning.reasoningEffort)
        assertNull(tuning.responseVerbosity)
    }

    private fun profile(provider: ApiProvider, model: String): CloudAiProfile = CloudAiProfile(
        id = "test",
        name = "Test",
        provider = provider,
        baseUrl = provider.defaultBaseUrl,
        model = model,
    )
}

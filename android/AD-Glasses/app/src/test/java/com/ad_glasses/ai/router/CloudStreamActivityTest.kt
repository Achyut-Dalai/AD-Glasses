package com.ad_glasses.ai.router

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudStreamActivityTest {
    @Test
    fun gemini37_concise_stream_requests_reasoning_heartbeat_because_thinking_remains_on() {
        assertTrue(
            shouldRequestReasoningHeartbeat(
                profile(ApiProvider.GOOGLE, "gemini-3.7-flash"),
                CloudGenerationMode.CONCISE_CONVERSATION,
            ),
        )
    }

    @Test
    fun gemini25_flash_concise_stream_skips_reasoning_heartbeat_when_thinking_budget_is_zero() {
        assertFalse(
            shouldRequestReasoningHeartbeat(
                profile(ApiProvider.GOOGLE, "gemini-2.5-flash"),
                CloudGenerationMode.CONCISE_CONVERSATION,
            ),
        )
    }

    @Test
    fun explicit_reasoned_stream_always_requests_reasoning_heartbeat() {
        assertTrue(
            shouldRequestReasoningHeartbeat(
                profile(ApiProvider.OPENROUTER, "any/reasoning-model"),
                CloudGenerationMode.REASONED_CONVERSATION,
            ),
        )
    }

    @Test
    fun normal_groq_concise_stream_does_not_request_extra_reasoning_metadata() {
        assertFalse(
            shouldRequestReasoningHeartbeat(
                profile(ApiProvider.GROQ, "llama-3.3-70b-versatile"),
                CloudGenerationMode.CONCISE_CONVERSATION,
            ),
        )
    }

    @Test
    fun openrouter_reasoning_only_concise_failure_gets_one_bounded_recovery() {
        assertTrue(
            shouldRetryOpenRouterReasoningOnly(
                provider = ApiProvider.OPENROUTER,
                mode = CloudGenerationMode.CONCISE_CONVERSATION,
                requestedTokens = 96,
                reasoningSeen = true,
                visibleText = "",
            ),
        )
    }

    @Test
    fun openrouter_recovery_never_retries_after_mandatory_reasoning_ceiling() {
        assertFalse(
            shouldRetryOpenRouterReasoningOnly(
                provider = ApiProvider.OPENROUTER,
                mode = CloudGenerationMode.CONCISE_CONVERSATION,
                requestedTokens = CloudModelPolicy.CONCISE_MANDATORY_REASONING_TOKENS,
                reasoningSeen = true,
                visibleText = "",
            ),
        )
    }

    @Test
    fun reasoning_only_recovery_does_not_affect_other_providers_or_successful_answers() {
        assertFalse(
            shouldRetryOpenRouterReasoningOnly(
                provider = ApiProvider.GROQ,
                mode = CloudGenerationMode.CONCISE_CONVERSATION,
                requestedTokens = 96,
                reasoningSeen = true,
                visibleText = "",
            ),
        )
        assertFalse(
            shouldRetryOpenRouterReasoningOnly(
                provider = ApiProvider.OPENROUTER,
                mode = CloudGenerationMode.CONCISE_CONVERSATION,
                requestedTokens = 96,
                reasoningSeen = true,
                visibleText = "Paris.",
            ),
        )
    }

    @Test
    fun deepseek_reasoning_content_counts_as_reasoning_activity() {
        assertTrue(
            openAiCompatibleHasReasoningActivity(
                reasoningContent = "private reasoning",
                reasoning = null,
                reasoningDetailsCount = 0,
                visibleContent = null,
            ),
        )
    }

    @Test
    fun openrouter_reasoning_details_count_as_reasoning_activity() {
        assertTrue(
            openAiCompatibleHasReasoningActivity(
                reasoningContent = null,
                reasoning = null,
                reasoningDetailsCount = 1,
                visibleContent = null,
            ),
        )
    }

    @Test
    fun inline_think_wrapper_counts_as_reasoning_activity() {
        assertTrue(
            openAiCompatibleHasReasoningActivity(
                reasoningContent = null,
                reasoning = null,
                reasoningDetailsCount = 0,
                visibleContent = "<think>private work",
            ),
        )
    }

    @Test
    fun ordinary_visible_answer_is_not_misclassified_as_reasoning() {
        assertFalse(
            openAiCompatibleHasReasoningActivity(
                reasoningContent = null,
                reasoning = null,
                reasoningDetailsCount = 0,
                visibleContent = "Paris is the capital of France.",
            ),
        )
    }

    @Test
    fun openai_reasoning_stream_event_counts_as_reasoning_activity() {
        assertTrue(
            openAiResponsesHasReasoningActivity(
                eventType = "response.reasoning_summary_text.delta",
                outputItemType = null,
            ),
        )
        assertTrue(
            openAiResponsesHasReasoningActivity(
                eventType = "response.output_item.added",
                outputItemType = "reasoning",
            ),
        )
    }

    @Test
    fun openai_output_text_event_is_not_reasoning_activity() {
        assertFalse(
            openAiResponsesHasReasoningActivity(
                eventType = "response.output_text.delta",
                outputItemType = "message",
            ),
        )
    }

    private fun profile(provider: ApiProvider, model: String): CloudAiProfile = CloudAiProfile(
        id = "test",
        name = "Test",
        provider = provider,
        baseUrl = provider.defaultBaseUrl,
        model = model,
    )
}

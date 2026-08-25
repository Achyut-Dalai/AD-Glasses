package com.ad_glasses.ai.router

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudStreamActivityTest {
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
}

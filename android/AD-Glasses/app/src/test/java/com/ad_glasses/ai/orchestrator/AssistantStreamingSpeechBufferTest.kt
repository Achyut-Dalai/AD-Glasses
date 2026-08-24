package com.ad_glasses.ai.orchestrator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantStreamingSpeechBufferTest {
    @Test
    fun emits_first_complete_sentence_before_generation_finishes() {
        val buffer = AssistantStreamingSpeechBuffer()

        assertTrue(buffer.accept("Paris is the capital").isEmpty())
        assertEquals(
            listOf("Paris is the capital of France."),
            buffer.accept(" of France. It is known"),
        )
        assertEquals(
            listOf("It is known for the Eiffel Tower."),
            buffer.finish("Paris is the capital of France. It is known for the Eiffel Tower."),
        )
    }

    @Test
    fun emits_first_long_sentence_at_earlier_natural_phrase_boundary() {
        val buffer = AssistantStreamingSpeechBuffer()

        assertEquals(
            listOf("The fastest way to get started is to open the settings screen"),
            buffer.accept(
                "The fastest way to get started is to open the settings screen and choose",
            ),
        )
        assertEquals(
            listOf("and choose your preferred provider while keeping the app connected."),
            buffer.accept(" your preferred provider while keeping the app connected."),
        )
    }

    @Test
    fun never_emits_unfinished_reasoning_block() {
        val buffer = AssistantStreamingSpeechBuffer()

        assertTrue(buffer.accept("<think>I should reason about this.").isEmpty())
        assertTrue(buffer.accept(" More hidden reasoning.</think>").isEmpty())
        assertEquals(
            listOf("The answer is 42."),
            buffer.accept("The answer is 42."),
        )
        assertTrue(buffer.finish("<think>I should reason about this. More hidden reasoning.</think>The answer is 42.").isEmpty())
    }

    @Test
    fun reasoning_label_waits_for_final_answer_label() {
        val buffer = AssistantStreamingSpeechBuffer()

        assertTrue(buffer.accept("Reasoning: inspect the options. Option one looks plausible.").isEmpty())
        assertEquals(
            listOf("Use option two."),
            buffer.accept(" Final answer: Use option two."),
        )
    }

    @Test
    fun finalization_flushes_short_tail_without_sentence_punctuation() {
        val buffer = AssistantStreamingSpeechBuffer()

        assertTrue(buffer.accept("Short answer").isEmpty())
        assertEquals(listOf("Short answer"), buffer.finish("Short answer"))
    }

    @Test
    fun partial_compact_system_prompt_echo_is_not_spoken() {
        val buffer = AssistantStreamingSpeechBuffer()

        assertTrue(buffer.accept("You are AD.").isEmpty())
        assertTrue(
            buffer.accept(" Answer directly and concisely. Return only the final answer.").isEmpty(),
        )
    }
}

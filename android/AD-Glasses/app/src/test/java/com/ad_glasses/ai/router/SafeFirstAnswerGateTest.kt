package com.ad_glasses.ai.router

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeFirstAnswerGateTest {
    @Test
    fun normal_answer_is_visible_immediately() {
        val gate = SafeFirstAnswerGate()

        assertEquals("Paris", gate.accept("Paris"))
    }

    @Test
    fun reasoning_wrapper_does_not_satisfy_first_answer_until_final_text_arrives() {
        val gate = SafeFirstAnswerGate()

        assertTrue(gate.accept("<think>I should compare the options.").isBlank())
        assertTrue(gate.accept(" More internal work.</think>").isBlank())
        assertEquals("Java is usually faster", gate.accept("Java is usually faster"))
    }

    @Test
    fun reasoning_label_waits_for_explicit_final_answer() {
        val gate = SafeFirstAnswerGate()

        assertTrue(gate.accept("Reasoning: compare runtime and ergonomics.").isBlank())
        assertEquals(
            "Python is usually simpler to write.",
            gate.accept(" Final answer: Python is usually simpler to write."),
        )
    }

    @Test
    fun current_shared_system_prompt_prefix_never_counts_as_safe_answer() {
        val gate = SafeFirstAnswerGate()

        assertTrue(gate.accept("You are AD. Answer the latest user request").isBlank())
        assertTrue(gate.accept(" directly in plain text.").isBlank())
    }

    @Test
    fun historical_voice_system_prompt_prefix_remains_blocked() {
        val gate = SafeFirstAnswerGate()

        assertTrue(gate.accept("You are AD, a voice assistant").isBlank())
        assertTrue(gate.accept(" for smart glasses.").isBlank())
    }

    @Test
    fun current_visible_preserves_safe_partial_answer_for_runaway_cap() {
        val gate = SafeFirstAnswerGate()

        gate.accept("<think>internal work</think>")
        gate.accept("Java is faster for many workloads")

        assertEquals("Java is faster for many workloads", gate.currentVisible())
    }

    @Test
    fun concise_voice_keeps_fast_first_answer_deadline() {
        val timeouts = AgentInferenceRouter.wearableTimeouts(CloudGenerationMode.CONCISE_CONVERSATION)

        assertEquals(6_000L, timeouts.firstSafeAnswerMs)
        assertEquals(30_000L, timeouts.totalGenerationMs)
    }

    @Test
    fun explicit_reasoning_voice_gets_longer_thinking_runway() {
        val timeouts = AgentInferenceRouter.wearableTimeouts(CloudGenerationMode.REASONED_CONVERSATION)

        assertEquals(15_000L, timeouts.firstSafeAnswerMs)
        assertEquals(45_000L, timeouts.totalGenerationMs)
    }

    @Test
    fun low_latency_history_stops_at_budget_boundary_instead_of_resurrecting_older_turns() {
        val messages = listOf(
            mapOf("role" to "user", "content" to "old"),
            mapOf("role" to "assistant", "content" to "b".repeat(50)),
            mapOf("role" to "user", "content" to "c".repeat(100)),
            mapOf("role" to "assistant", "content" to "d".repeat(300)),
            mapOf("role" to "user", "content" to "e".repeat(300)),
        )

        val bounded = AgentInferenceRouter.boundedLowLatencyHistory(messages)

        assertEquals(listOf(100, 300, 300), bounded.map { it["content"].orEmpty().length })
        assertFalse(bounded.any { it["content"] == "old" })
    }
}

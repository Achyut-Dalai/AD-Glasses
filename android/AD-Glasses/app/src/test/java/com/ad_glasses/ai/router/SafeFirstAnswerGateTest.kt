package com.ad_glasses.ai.router

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun analysis_wrapper_is_never_treated_as_a_safe_tts_answer() {
        val gate = SafeFirstAnswerGate()

        assertTrue(gate.accept("<analysis>Private chain of thought").isBlank())
        assertTrue(gate.accept(" stays hidden.</analysis>").isBlank())
        assertEquals("The final answer is safe.", gate.accept("The final answer is safe."))
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
    fun concise_voice_uses_bounded_activity_extensions() {
        val timeouts = AgentInferenceRouter.wearableTimeouts(CloudGenerationMode.CONCISE_CONVERSATION)

        assertEquals(6_000L, timeouts.firstSafeAnswerMs)
        assertEquals(10_000L, timeouts.activeTransportAnswerMs)
        assertEquals(15_000L, timeouts.activeReasoningAnswerMs)
        assertEquals(30_000L, timeouts.totalGenerationMs)
    }

    @Test
    fun explicit_reasoning_voice_gets_longer_bounded_runway() {
        val timeouts = AgentInferenceRouter.wearableTimeouts(CloudGenerationMode.REASONED_CONVERSATION)

        assertEquals(15_000L, timeouts.firstSafeAnswerMs)
        assertEquals(20_000L, timeouts.activeTransportAnswerMs)
        assertEquals(30_000L, timeouts.activeReasoningAnswerMs)
        assertEquals(45_000L, timeouts.totalGenerationMs)
    }

    @Test
    fun dead_provider_does_not_extend_first_answer_deadline() {
        val timeouts = AgentInferenceRouter.wearableTimeouts(CloudGenerationMode.CONCISE_CONVERSATION)

        assertNull(
            AgentInferenceRouter.nextFirstAnswerDeadline(
                currentDeadlineMs = timeouts.firstSafeAnswerMs,
                timeouts = timeouts,
                providerActivitySeen = false,
                reasoningActivitySeen = false,
            ),
        )
    }

    @Test
    fun active_transport_extends_only_to_transport_ceiling() {
        val timeouts = AgentInferenceRouter.wearableTimeouts(CloudGenerationMode.CONCISE_CONVERSATION)

        assertEquals(
            10_000L,
            AgentInferenceRouter.nextFirstAnswerDeadline(
                currentDeadlineMs = 6_000L,
                timeouts = timeouts,
                providerActivitySeen = true,
                reasoningActivitySeen = false,
            ),
        )
        assertNull(
            AgentInferenceRouter.nextFirstAnswerDeadline(
                currentDeadlineMs = 10_000L,
                timeouts = timeouts,
                providerActivitySeen = true,
                reasoningActivitySeen = false,
            ),
        )
    }

    @Test
    fun observed_reasoning_can_extend_from_transport_to_reasoning_ceiling() {
        val timeouts = AgentInferenceRouter.wearableTimeouts(CloudGenerationMode.CONCISE_CONVERSATION)

        assertEquals(
            15_000L,
            AgentInferenceRouter.nextFirstAnswerDeadline(
                currentDeadlineMs = 10_000L,
                timeouts = timeouts,
                providerActivitySeen = true,
                reasoningActivitySeen = true,
            ),
        )
        assertNull(
            AgentInferenceRouter.nextFirstAnswerDeadline(
                currentDeadlineMs = 15_000L,
                timeouts = timeouts,
                providerActivitySeen = true,
                reasoningActivitySeen = true,
            ),
        )
    }

    @Test
    fun low_latency_history_uses_1500_char_budget_caps_each_message_and_keeps_short_older_context() {
        val messages = listOf(
            mapOf("role" to "user", "content" to "old"),
            mapOf("role" to "assistant", "content" to "a".repeat(500)),
            mapOf("role" to "user", "content" to "b".repeat(500)),
            mapOf("role" to "assistant", "content" to "c".repeat(500)),
            mapOf("role" to "user", "content" to "d".repeat(500)),
        )

        val bounded = AgentInferenceRouter.boundedLowLatencyHistory(messages)

        // Four newest messages are individually capped to 360 = 1,440 chars total. The older
        // three-character turn still fits inside the 1,500-character contiguous history budget.
        assertEquals(listOf(3, 360, 360, 360, 360), bounded.map { it["content"].orEmpty().length })
        assertTrue(bounded.first()["content"] == "old")
        assertFalse(bounded.sumOf { it["content"].orEmpty().length } > 1_500)
    }
}

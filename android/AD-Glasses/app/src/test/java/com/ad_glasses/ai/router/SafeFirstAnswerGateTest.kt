package com.ad_glasses.ai.router

import org.junit.Assert.assertEquals
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
    fun current_system_prompt_prefix_never_counts_as_safe_answer() {
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
}

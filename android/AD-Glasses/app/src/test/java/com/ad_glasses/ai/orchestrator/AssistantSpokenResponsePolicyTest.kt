package com.ad_glasses.ai.orchestrator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantSpokenResponsePolicyTest {
    @Test
    fun short_answer_is_spoken_in_full() {
        assertEquals("Three gluten-free options are available.", AssistantSpokenResponsePolicy.forGlasses(
            "Three gluten-free options are available.",
        ))
    }

    @Test
    fun long_answer_is_shortened_and_points_to_chats() {
        val rich = buildString {
            append("I found three suitable options. ")
            repeat(30) { append("This contains precise information you may want to review later. ") }
        }

        val spoken = AssistantSpokenResponsePolicy.forGlasses(rich)

        assertTrue(spoken.endsWith("Full details are in Chats."))
        assertTrue(spoken.length < rich.length)
        assertFalse(spoken.contains("```"))
    }
}

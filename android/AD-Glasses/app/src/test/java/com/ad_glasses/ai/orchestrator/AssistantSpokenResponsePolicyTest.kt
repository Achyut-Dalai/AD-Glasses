package com.ad_glasses.ai.orchestrator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantSpokenResponsePolicyTest {
    @Test
    fun short_answer_is_spoken_in_full() {
        assertEquals(
            "Three gluten-free options are available.",
            AssistantSpokenResponsePolicy.forGlasses("Three gluten-free options are available."),
        )
    }

    @Test
    fun markdown_formatting_is_removed_before_tts() {
        val rich = "**Paris** is the *capital* of _France_. See [details](https://example.com)."

        assertEquals(
            "Paris is the capital of France. See details.",
            AssistantSpokenResponsePolicy.normalizeForSpeech(rich),
        )
    }

    @Test
    fun large_asterisk_runs_are_never_spoken() {
        val rich = "Answer *************************************** complete."

        val spoken = AssistantSpokenResponsePolicy.normalizeForSpeech(rich)

        assertEquals("Answer complete.", spoken)
        assertFalse(spoken.contains('*'))
    }

    @Test
    fun markdown_lists_become_natural_sentences() {
        val rich = "**Top picks:**\n- **Alpha**\n- Beta\n- [Gamma](https://example.com)"

        assertEquals(
            "Top picks. Alpha. Beta. Gamma",
            AssistantSpokenResponsePolicy.normalizeForSpeech(rich),
        )
    }

    @Test
    fun mathematical_asterisk_is_preserved_as_times() {
        assertEquals(
            "2 times 3 = 6.",
            AssistantSpokenResponsePolicy.normalizeForSpeech("2 * 3 = 6."),
        )
    }

    @Test
    fun long_answer_is_shortened_and_points_to_chats() {
        val rich = buildString {
            append("I found three suitable options. ")
            repeat(30) { append("This contains precise information you may want to review later. ") }
        }

        val spoken = AssistantSpokenResponsePolicy.forGlasses(rich)

        assertTrue(spoken.endsWith("More detail is in Chats."))
        assertTrue(spoken.length < rich.length)
        assertTrue(spoken.split(Regex("\\s+")).size <= 50)
        assertFalse(spoken.contains("```"))
    }

    @Test
    fun more_than_three_sentences_is_shortened_for_wearable_playback() {
        val spoken = AssistantSpokenResponsePolicy.forGlasses("One. Two. Three. Four.")

        assertEquals("One. Two. More detail is in Chats.", spoken)
    }
}

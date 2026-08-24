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
    fun markdown_formatting_is_never_read_as_symbols() {
        val rich = """
            ## **Paris**
            - Paris is *the* capital of France.
            - See [details](https://example.com) and `maps`.
            ****************************************
        """.trimIndent()

        val spoken = AssistantSpokenResponsePolicy.normalizeForSpeech(rich)

        assertEquals("Paris Paris is the capital of France. See details and maps.", spoken)
        assertFalse(spoken.contains('*'))
        assertFalse(spoken.contains('#'))
        assertFalse(spoken.contains('`'))
        assertFalse(spoken.contains("https://"))
    }

    @Test
    fun repeated_asterisks_are_removed_instead_of_spoken() {
        val spoken = AssistantSpokenResponsePolicy.normalizeForSpeech(
            "Answer **************************************** **done** ********",
        )

        assertEquals("Answer done", spoken)
    }

    @Test
    fun numeric_asterisk_is_spoken_as_times() {
        assertEquals(
            "6 times 7 is 42.",
            AssistantSpokenResponsePolicy.normalizeForSpeech("6 * 7 is **42**."),
        )
    }

    @Test
    fun runaway_answer_is_bounded_to_fifty_spoken_words() {
        val rich = (1..90).joinToString(" ") { "word$it" }

        val spoken = AssistantSpokenResponsePolicy.forGlasses(rich)
        val words = spoken.split(Regex("\\s+"))

        assertTrue(words.size <= 50)
        assertTrue(spoken.endsWith("More detail is in Chats."))
    }

    @Test
    fun fourth_sentence_is_not_spoken() {
        val spoken = AssistantSpokenResponsePolicy.forGlasses(
            "One is useful. Two is useful. Three is useful. Four should stay in Chats.",
        )

        assertTrue(spoken.startsWith("One is useful. Two is useful. Three is useful."))
        assertTrue(spoken.endsWith("More detail is in Chats."))
        assertFalse(spoken.contains("Four should"))
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
        assertFalse(spoken.contains("```"))
        assertTrue(spoken.split(Regex("\\s+")).size <= 50)
    }
}

package com.ad_glasses.ai.orchestrator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantVisualContextCodecTest {
    @Test
    fun `extracts machine memory without exposing it as answer`() {
        val parsed = AssistantVisualContextCodec.parse(
            "The shirt is navy blue.\n<AD_VISUAL_CONTEXT>A person wears a navy blue collared shirt beside a red bicycle.</AD_VISUAL_CONTEXT>",
        )

        assertEquals("The shirt is navy blue.", parsed.answer)
        assertEquals(
            "A person wears a navy blue collared shirt beside a red bicycle.",
            parsed.summary,
        )
    }

    @Test
    fun `ordinary provider answer remains unchanged`() {
        val parsed = AssistantVisualContextCodec.parse("A white sign says Parking Only.")

        assertEquals("A white sign says Parking Only.", parsed.answer)
        assertNull(parsed.summary)
    }

    @Test
    fun `visual memory is bounded before persistence`() {
        val longSummary = List(100) { "detail$it" }.joinToString(" ")
        val normalized = AssistantVisualContextCodec.normalizeSummary(longSummary)

        assertTrue(normalized.length <= AssistantVisualContextCodec.MAX_SUMMARY_CHARS)
        assertTrue(normalized.endsWith("…"))
    }

    @Test
    fun `malformed memory marker does not delete visible answer`() {
        val raw = "The label says 42. <AD_VISUAL_CONTEXT>unfinished memory"
        val parsed = AssistantVisualContextCodec.parse(raw)

        assertEquals(raw, parsed.answer)
        assertNull(parsed.summary)
    }

    @Test
    fun `empty memory block is ignored`() {
        val parsed = AssistantVisualContextCodec.parse(
            "A person is standing outside.\n<AD_VISUAL_CONTEXT>   </AD_VISUAL_CONTEXT>",
        )

        assertEquals("A person is standing outside.", parsed.answer)
        assertNull(parsed.summary)
    }

    @Test
    fun `summary whitespace is compacted`() {
        val parsed = AssistantVisualContextCodec.parse(
            "Yes — that is the same bicycle.\n<AD_VISUAL_CONTEXT> red   bicycle\nnext to   blue wall </AD_VISUAL_CONTEXT>",
        )

        assertEquals("Yes — that is the same bicycle.", parsed.answer)
        assertEquals("red bicycle next to blue wall", parsed.summary)
    }

    @Test
    fun `normalization strips nested machine markers`() {
        val summary = AssistantVisualContextCodec.normalizeSummary(
            "blue shirt <AD_VISUAL_CONTEXT> ignored </AD_VISUAL_CONTEXT> red bike",
        )

        assertFalse(summary.contains("AD_VISUAL_CONTEXT"))
        assertTrue(summary.contains("blue shirt"))
        assertTrue(summary.contains("red bike"))
    }

    @Test
    fun `visual memory instruction stays compact`() {
        val instruction = AssistantVisualContextCodec.modelInstruction

        assertTrue(instruction.contains("at most 45 words"))
        assertTrue(instruction.contains("<AD_VISUAL_CONTEXT>"))
        assertTrue(instruction.length < 700)
    }
}

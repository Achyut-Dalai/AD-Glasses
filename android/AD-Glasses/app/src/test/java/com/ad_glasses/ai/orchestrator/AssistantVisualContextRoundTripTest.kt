package com.ad_glasses.ai.orchestrator

import org.junit.Assert.assertEquals
import org.junit.Test

class AssistantVisualContextRoundTripTest {
    @Test
    fun `summary whitespace is compacted while answer punctuation is preserved`() {
        val parsed = AssistantVisualContextCodec.parse(
            "Yes — that is the same bicycle.\n<AD_VISUAL_CONTEXT> red   bicycle\nnext to   blue wall </AD_VISUAL_CONTEXT>",
        )

        assertEquals("Yes — that is the same bicycle.", parsed.answer)
        assertEquals("red bicycle next to blue wall", parsed.summary)
    }
}

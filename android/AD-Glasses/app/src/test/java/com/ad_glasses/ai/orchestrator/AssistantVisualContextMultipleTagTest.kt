package com.ad_glasses.ai.orchestrator

import org.junit.Assert.assertEquals
import org.junit.Test

class AssistantVisualContextMultipleTagTest {
    @Test
    fun `final complete visual memory block is used`() {
        val parsed = AssistantVisualContextCodec.parse(
            "Answer. <AD_VISUAL_CONTEXT>old</AD_VISUAL_CONTEXT>\n<AD_VISUAL_CONTEXT>new memory</AD_VISUAL_CONTEXT>",
        )
        assertEquals("Answer. <AD_VISUAL_CONTEXT>old</AD_VISUAL_CONTEXT>", parsed.answer)
        assertEquals("new memory", parsed.summary)
    }
}

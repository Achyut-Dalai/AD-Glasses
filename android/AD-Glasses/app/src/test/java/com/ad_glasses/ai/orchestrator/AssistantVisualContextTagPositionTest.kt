package com.ad_glasses.ai.orchestrator

import org.junit.Assert.assertEquals
import org.junit.Test

class AssistantVisualContextTagPositionTest {
    @Test
    fun `trailing whitespace after memory tag is ignored`() {
        val parsed = AssistantVisualContextCodec.parse(
            "Blue car.\n<AD_VISUAL_CONTEXT>blue car parked left of a tree</AD_VISUAL_CONTEXT>\n\n",
        )
        assertEquals("Blue car.", parsed.answer)
        assertEquals("blue car parked left of a tree", parsed.summary)
    }
}

package com.ad_glasses.ai.orchestrator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AssistantVisualContextEmptySummaryTest {
    @Test
    fun `empty machine memory is not persisted`() {
        val parsed = AssistantVisualContextCodec.parse(
            "A person is standing outside.\n<AD_VISUAL_CONTEXT>   </AD_VISUAL_CONTEXT>",
        )
        assertEquals("A person is standing outside.", parsed.answer)
        assertNull(parsed.summary)
    }
}

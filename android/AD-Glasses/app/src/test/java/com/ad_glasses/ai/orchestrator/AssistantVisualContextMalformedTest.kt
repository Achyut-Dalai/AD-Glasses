package com.ad_glasses.ai.orchestrator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AssistantVisualContextMalformedTest {
    @Test
    fun `closing marker without opener is ordinary visible text`() {
        val raw = "Answer only. </AD_VISUAL_CONTEXT>"
        val parsed = AssistantVisualContextCodec.parse(raw)

        assertEquals(raw, parsed.answer)
        assertNull(parsed.summary)
    }
}

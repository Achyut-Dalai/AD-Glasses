package com.ad_glasses.ai.orchestrator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AssistantVisualContextNoSummaryTest {
    @Test
    fun `provider may omit optional visual memory without breaking answer`() {
        val parsed = AssistantVisualContextCodec.parse("There are two people near a bus stop.")
        assertEquals("There are two people near a bus stop.", parsed.answer)
        assertNull(parsed.summary)
    }
}

package com.ad_glasses.ai.orchestrator

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantVisualContextWordBoundaryTest {
    @Test
    fun `long visual memory truncates at a readable boundary`() {
        val normalized = AssistantVisualContextCodec.normalizeSummary("scene ".repeat(100))
        assertTrue(normalized.endsWith("…"))
        assertFalse(normalized.endsWith(" …"))
    }
}

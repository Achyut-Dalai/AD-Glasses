package com.ad_glasses.ai.orchestrator

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantVisualContextPolicyTest {
    @Test
    fun `normalization strips nested machine markers`() {
        val summary = AssistantVisualContextCodec.normalizeSummary(
            "  blue shirt <AD_VISUAL_CONTEXT> ignored </AD_VISUAL_CONTEXT> red bike  ",
        )

        assertFalse(summary.contains("AD_VISUAL_CONTEXT"))
        assertTrue(summary.contains("blue shirt"))
        assertTrue(summary.contains("red bike"))
    }
}

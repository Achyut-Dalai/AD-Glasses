package com.ad_glasses.ai.orchestrator

import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantVisualContextInstructionTest {
    @Test
    fun `visual memory instruction stays compact`() {
        val instruction = AssistantVisualContextCodec.modelInstruction
        assertTrue(instruction.contains("at most 45 words"))
        assertTrue(instruction.contains("<AD_VISUAL_CONTEXT>"))
        assertTrue(instruction.length < 700)
    }
}

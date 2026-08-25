package com.ad_glasses.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AssistantTextFingerprintTest {
    @Test
    fun `trimming does not change correlation hash`() {
        assertEquals(
            AssistantTextFingerprint.of("what time is it"),
            AssistantTextFingerprint.of("  what time is it\n"),
        )
    }

    @Test
    fun `different text produces a different short hash`() {
        val first = AssistantTextFingerprint.of("turn on translation")
        val second = AssistantTextFingerprint.of("turn off translation")

        assertEquals(12, first.length)
        assertEquals(12, second.length)
        assertNotEquals(first, second)
    }

    @Test
    fun `blank text has stable sentinel`() {
        assertEquals("empty", AssistantTextFingerprint.of("   \n"))
    }
}

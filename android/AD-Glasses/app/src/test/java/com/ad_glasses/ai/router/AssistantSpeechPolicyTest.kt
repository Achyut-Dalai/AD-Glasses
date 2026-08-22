package com.ad_glasses.ai.router

import org.junit.Assert.assertEquals
import org.junit.Test

class AssistantSpeechPolicyTest {
    @Test
    fun `keeps concise clarification`() {
        assertEquals(
            "Which app should I open?",
            AssistantSpeechPolicy.clarification("Which app should I open?"),
        )
    }

    @Test
    fun `replaces tool details with safe clarification`() {
        assertEquals(
            "Please say exactly what you want me to do.",
            AssistantSpeechPolicy.clarification("Use click_coord at center:(50,90)"),
        )
    }

    @Test
    fun `caps excessive classifier speech`() {
        assertEquals(
            "Please say exactly what you want me to do.",
            AssistantSpeechPolicy.clarification("x".repeat(200)),
        )
    }
}

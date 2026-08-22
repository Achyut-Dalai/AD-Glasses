package com.ad_glasses.localagent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAgentRuntimePolicyTest {

    @Test
    fun `identical clicks are blocked after their repeat budget`() {
        val action = LocalAgentAction.ClickText("Send")
        var state = LocalAgentTaskState(goal = "Send message", maxSteps = 8, startedAtMs = 1L)

        state = state.nextStep("Clicked Send", failed = false, action = action)
        assertFalse(state.hasReachedRepeatLimit(action))

        state = state.nextStep("Clicked Send", failed = false, action = action)
        assertTrue(state.hasReachedRepeatLimit(action))
    }

    @Test
    fun `app launch receives a longer settle delay`() {
        assertEquals(3_000L, LocalAgentRuntimePolicy.settleDelayMs(LocalAgentAction.OpenApp("WhatsApp")))
        assertTrue(
            LocalAgentRuntimePolicy.settleDelayMs(LocalAgentAction.OpenApp("WhatsApp")) >
                LocalAgentRuntimePolicy.settleDelayMs(LocalAgentAction.Scroll(LocalAgentAction.Direction.DOWN)),
        )
    }
}

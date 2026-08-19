package com.fersaiyan.cyanbridge.ai.orchestrator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AssistantCapabilityCommandRouterTest {
    @Test
    fun translateStillRoutes() {
        assertEquals(
            AssistantCapabilityCommand(
                capability = AssistantCapability.TRANSLATOR,
                action = AssistantCapabilityAction.START,
            ),
            AssistantCapabilityCommandRouter.parse("start translate"),
        )
    }

    @Test
    fun soundbitesStillRoutes() {
        assertEquals(
            AssistantCapabilityCommand(
                capability = AssistantCapability.MEETING_NOTES,
                action = AssistantCapabilityAction.STOP,
            ),
            AssistantCapabilityCommandRouter.parse("stop soundbites"),
        )
    }

    @Test
    fun retiredCronNoLongerRoutes() {
        assertNull(AssistantCapabilityCommandRouter.parse("start cron"))
        assertNull(AssistantCapabilityCommandRouter.parse("start errands"))
    }
}

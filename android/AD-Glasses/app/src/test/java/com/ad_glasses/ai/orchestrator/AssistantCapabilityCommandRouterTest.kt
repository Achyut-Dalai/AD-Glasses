package com.ad_glasses.ai.orchestrator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AssistantCapabilityCommandRouterTest {
    @Test
    fun translateStartAliasesRoute() {
        listOf(
            "start translate",
            "begin translation",
            "enable translator",
            "turn on interpreter",
            "resume translate",
        ).forEach { phrase ->
            assertEquals(
                AssistantCapabilityCommand(
                    capability = AssistantCapability.TRANSLATOR,
                    action = AssistantCapabilityAction.START,
                ),
                AssistantCapabilityCommandRouter.parse(phrase),
            )
        }
    }

    @Test
    fun soundbitesAndMeetingAliasesRoute() {
        listOf("soundbites", "meeting notes", "meeting mode", "spark notes", "take notes").forEach { name ->
            assertEquals(
                AssistantCapabilityCommand(
                    capability = AssistantCapability.MEETING_NOTES,
                    action = AssistantCapabilityAction.STOP,
                ),
                AssistantCapabilityCommandRouter.parse("stop $name"),
            )
        }
    }

    @Test
    fun automationAliasesRouteOnlyWhenPairedWithStartOrStopAction() {
        listOf("automation", "local agent", "phone agent", "phone control").forEach { name ->
            assertEquals(
                AssistantCapabilityCommand(
                    capability = AssistantCapability.LOCAL_AGENT,
                    action = AssistantCapabilityAction.START,
                ),
                AssistantCapabilityCommandRouter.parse("enable $name"),
            )
            assertNull(AssistantCapabilityCommandRouter.parse(name))
        }
    }

    @Test
    fun retiredFeatureAndWebPhrasesDoNotMasqueradeAsCapabilityCommands() {
        listOf(
            "start cron",
            "start errands",
            "start daynote",
            "start diary",
            "start timeline",
            "start visual diary",
            "search the web",
            "browse the web",
            "check online",
        ).forEach { phrase -> assertNull(AssistantCapabilityCommandRouter.parse(phrase)) }
    }
}

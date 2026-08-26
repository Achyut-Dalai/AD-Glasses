package com.ad_glasses.ai.router

import org.junit.Assert.assertEquals
import org.junit.Test

class AssistantRequestRouterTest {
    private val router = AssistantRequestRouter()

    @Test
    fun textMeaningNeverChangesTheStructuralAnswerRoute() {
        listOf(
            "Search the web for today's cricket score",
            "Find KFC near me",
            "Open Spotify and play my liked songs",
            "How do I open Bluetooth settings?",
            "What am I looking at?",
        ).forEach { text ->
            val decision = router.classifyStructurally(
                AssistantRequest(text, AssistantRequestSource.GLASSES_VOICE, imageAttached = false),
            )
            assertEquals(text, AssistantIntent.ANSWER_QUESTION, decision.intent)
        }
    }

    @Test
    fun actualAttachedImageUsesVisionPathWithoutPhraseMatching() {
        val decision = router.classifyStructurally(
            AssistantRequest(
                text = "Please help with this",
                source = AssistantRequestSource.GLASSES_IMAGE,
                imageAttached = true,
            ),
        )

        assertEquals(AssistantIntent.ANALYZE_IMAGE, decision.intent)
        assertEquals("Please help with this", decision.normalizedGoal)
    }

    @Test
    fun blankRequestClarifies() {
        val decision = router.classifyStructurally(
            AssistantRequest("   ", AssistantRequestSource.CHAT),
        )

        assertEquals(AssistantIntent.CLARIFY, decision.intent)
    }
}

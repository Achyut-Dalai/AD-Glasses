package com.fersaiyan.cyanbridge.shared.notes

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RuleBasedSummarizationServiceTest {
    @Test
    fun summarizesTranscriptWithoutPlatformServices() = runBlocking {
        val summary = RuleBasedSummarizationService().summarize(
            SummarizationRequest(
                transcript = "Alice agreed to ship. TODO finalize the deck. What is the launch date?",
                hintTitle = "Sprint Sync",
                minSummaryBullets = 2,
            )
        )

        assertEquals("Sprint Sync", summary.title)
        assertTrue(summary.summaryBullets.size >= 2)
        assertEquals(listOf("TODO finalize the deck."), summary.actionItems)
        assertEquals(listOf("Alice agreed to ship."), summary.keyDecisions)
        assertEquals(listOf("What is the launch date?"), summary.openQuestions)
    }
}

package com.ad_glasses.ai.grounding

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TavilySearchPolicyTest {
    private val today = LocalDate.of(2026, 8, 26)

    @Test
    fun currentBitcoinPriceUsesFinanceAndDayFreshness() {
        val plan = TavilySearchPolicy.plan("What is the current Bitcoin price?", today)

        assertEquals(TavilySearchTopic.FINANCE, plan.topic)
        assertEquals(TavilyTimeRange.DAY, plan.timeRange)
        assertNull(plan.startDate)
        assertNull(plan.endDate)
        assertTrue(plan.query.contains("Current date: 2026-08-26"))
    }

    @Test
    fun naturalBitcoinPriceQueryIsLiveEvenWithoutSayingCurrent() {
        val plan = TavilySearchPolicy.plan("Bitcoin price", today)

        assertEquals(TavilySearchTopic.FINANCE, plan.topic)
        assertEquals(TavilyTimeRange.DAY, plan.timeRange)
        assertTrue(plan.query.contains("Current date: 2026-08-26"))
    }

    @Test
    fun historicalBitcoinQuestionKeepsRequestedYearInsteadOfCurrentFreshness() {
        val plan = TavilySearchPolicy.plan("What was the Bitcoin price in 2025?", today)

        assertEquals(TavilySearchTopic.FINANCE, plan.topic)
        assertNull(plan.timeRange)
        assertEquals("2024-12-31", plan.startDate)
        assertEquals("2026-01-01", plan.endDate)
        assertTrue(plan.query.contains("historical period 2025"))
        assertTrue(plan.query.contains("do not substitute current data"))
    }

    @Test
    fun explicitHistoricalDateGetsTightDateWindow() {
        val plan = TavilySearchPolicy.plan("What happened on 2025-05-10?", today)

        assertNull(plan.timeRange)
        assertEquals("2025-05-09", plan.startDate)
        assertEquals("2025-05-11", plan.endDate)
    }

    @Test
    fun latestNewsUsesNewsTopicAndRecentWindow() {
        val plan = TavilySearchPolicy.plan("What is the latest AI news?", today)

        assertEquals(TavilySearchTopic.NEWS, plan.topic)
        assertEquals(TavilyTimeRange.WEEK, plan.timeRange)
        assertTrue(plan.query.contains("Current date: 2026-08-26"))
    }

    @Test
    fun latestNonNewsFactUsesWiderWindowRatherThanPretendingItMustBeFromToday() {
        val plan = TavilySearchPolicy.plan("What is the latest stable Android version?", today)

        assertEquals(TavilySearchTopic.GENERAL, plan.topic)
        assertEquals(TavilyTimeRange.YEAR, plan.timeRange)
    }

    @Test
    fun timelessQuestionDoesNotGetArtificialFreshness() {
        val plan = TavilySearchPolicy.plan("What is retrieval augmented generation?", today)

        assertEquals(TavilySearchTopic.GENERAL, plan.topic)
        assertNull(plan.timeRange)
        assertNull(plan.startDate)
        assertNull(plan.endDate)
        assertEquals("What is retrieval augmented generation?", plan.query)
    }
}

package com.ad_glasses.ai.grounding

import org.junit.Assert.assertEquals
import org.junit.Test

class TavilySearchPolicyTest {
    @Test
    fun tavilyNativeTopicsUseExpectedWireValues() {
        assertEquals("general", TavilySearchTopic.GENERAL.wire)
        assertEquals("news", TavilySearchTopic.NEWS.wire)
        assertEquals("finance", TavilySearchTopic.FINANCE.wire)
    }

    @Test
    fun tavilyNativeFreshnessUsesExpectedWireValues() {
        assertEquals("day", TavilyTimeRange.DAY.wire)
        assertEquals("week", TavilyTimeRange.WEEK.wire)
        assertEquals("month", TavilyTimeRange.MONTH.wire)
        assertEquals("year", TavilyTimeRange.YEAR.wire)
    }
}

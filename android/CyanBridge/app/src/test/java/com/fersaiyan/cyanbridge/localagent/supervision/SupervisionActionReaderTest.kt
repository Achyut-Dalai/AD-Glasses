package com.achyut.adglasses.localagent.supervision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SupervisionActionReaderTest {

    @Test
    fun parseLine_readsDailyReviewWithEventIds() {
        val line = """
            {"action_id":"sup_123","action_type":"daily_review_confirm","ts_ms":1700000000000,"date":"2026-04-10","target_event_ids":["evt_1","evt_2"],"target_fact_id":"D1"}
        """.trimIndent()

        val action = SupervisionActionReader.parseLine(line)

        assertNotNull(action)
        assertEquals("sup_123", action?.actionId)
        assertEquals("daily_review_confirm", action?.actionType)
        assertEquals(listOf("evt_1", "evt_2"), action?.targetEventIds)
        assertEquals("D1", action?.targetFactId)
    }

    @Test
    fun parseLine_returnsNullOnInvalidJson() {
        val action = SupervisionActionReader.parseLine("not json")
        assertNull(action)
    }
}

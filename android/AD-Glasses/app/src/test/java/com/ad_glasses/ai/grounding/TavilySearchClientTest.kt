package com.ad_glasses.ai.grounding

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TavilySearchClientTest {
    private val client = TavilySearchClient(ApplicationProvider.getApplicationContext())

    @Test
    fun assistantSearchUsesFastTavilyLlmAnswerPayloadWithOneBoundedChunk() {
        val payload = client.buildPayload(
            query = "India vs Sri Lanka cricket live score",
            depth = TavilySearchDepth.FAST,
            maxResults = 3,
            topic = TavilySearchTopic.NEWS,
            timeRange = TavilyTimeRange.DAY,
            includeAnswer = true,
        )

        assertEquals("India vs Sri Lanka cricket live score", payload.getString("query"))
        assertEquals("fast", payload.getString("search_depth"))
        assertEquals("news", payload.getString("topic"))
        assertEquals("day", payload.getString("time_range"))
        assertEquals("basic", payload.getString("include_answer"))
        assertEquals(1, payload.getInt("chunks_per_source"))
        assertEquals(3, payload.getInt("max_results"))
        assertFalse(payload.getBoolean("include_raw_content"))
        assertFalse(payload.has("include_domains"))
    }

    @Test
    fun explicitUserDomainsCanConstrainTavilyWithoutChangingTopic() {
        val payload = client.buildPayload(
            query = "India cricket live score",
            depth = TavilySearchDepth.FAST,
            maxResults = 3,
            topic = TavilySearchTopic.NEWS,
            timeRange = TavilyTimeRange.DAY,
            includeAnswer = true,
            includeDomains = listOf("espn.in", "espn.in"),
        )

        val domains = payload.getJSONArray("include_domains")
        assertEquals(1, domains.length())
        assertEquals("espn.in", domains.getString(0))
        assertEquals("news", payload.getString("topic"))
        assertEquals(1, payload.getInt("chunks_per_source"))
    }

    @Test
    fun answerGenerationCanBeDisabledWithoutEnablingRawContent() {
        val payload = client.buildPayload(
            query = "stable knowledge verification",
            depth = TavilySearchDepth.FAST,
            maxResults = 3,
            topic = TavilySearchTopic.GENERAL,
            timeRange = null,
            includeAnswer = false,
        )

        assertFalse(payload.getBoolean("include_answer"))
        assertFalse(payload.getBoolean("include_raw_content"))
        assertEquals(1, payload.getInt("chunks_per_source"))
        assertTrue(!payload.has("time_range"))
    }
}

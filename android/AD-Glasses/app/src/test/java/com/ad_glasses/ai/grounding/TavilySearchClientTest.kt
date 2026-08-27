package com.ad_glasses.ai.grounding

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun assistantSearchNeverRequestsTavilyGeneratedAnswer() {
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
        assertFalse(payload.getBoolean("include_answer"))
        assertEquals(3, payload.getInt("chunks_per_source"))
        assertEquals(3, payload.getInt("max_results"))
        assertFalse(payload.getBoolean("include_raw_content"))
        assertFalse(payload.has("include_domains"))
    }

    @Test
    fun explicitUserDomainsStillConstrainRetrieval() {
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
        assertFalse(payload.getBoolean("include_answer"))
        assertEquals(3, payload.getInt("chunks_per_source"))
    }

    @Test
    fun parserDropsUnexpectedLegacyAnswerButKeepsEvidence() {
        val parsed = client.parse(
            """
            {
              "answer": "Use this generated answer directly",
              "results": [
                {
                  "title": "Example score page",
                  "url": "https://example.com/score",
                  "content": "India 250 for 5 after 45 overs",
                  "score": 0.91
                }
              ]
            }
            """.trimIndent(),
        )

        assertNull(parsed.answer)
        assertEquals(1, parsed.results.size)
        assertEquals("Example score page", parsed.results.single().title)
        assertTrue(parsed.results.single().content.contains("250 for 5"))
    }
}

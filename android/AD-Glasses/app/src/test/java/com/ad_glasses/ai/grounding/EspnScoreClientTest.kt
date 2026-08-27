package com.ad_glasses.ai.grounding

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EspnScoreClientTest {
    private val client = EspnScoreClient()

    @Test
    fun parsesSiteScoreboardCompetitorsAndStatus() {
        val payload = JSONObject(
            """
            {
              "events": [{
                "id": "401000001",
                "name": "Los Angeles Lakers at Golden State Warriors",
                "date": "2026-08-27T02:00:00Z",
                "competitions": [{
                  "status": {"type": {"shortDetail": "Final"}},
                  "competitors": [
                    {"team": {"displayName": "Los Angeles Lakers"}, "score": "112"},
                    {"team": {"displayName": "Golden State Warriors"}, "score": "109"}
                  ]
                }]
              }]
            }
            """.trimIndent(),
        )

        val events = client.parseEventsContainer(payload, "NBA")

        assertEquals(1, events.size)
        assertEquals("Los Angeles Lakers", events.single().sides.first().name)
        assertEquals("112", events.single().sides.first().score)
        assertEquals("Final", events.single().status)
        assertTrue(events.single().matchScore("Lakers score") > 0)
    }

    @Test
    fun parsesCricketPersonalizedHeaderWithoutArticleData() {
        val payload = JSONObject(
            """
            {
              "sports": [{
                "leagues": [{
                  "id": "23694",
                  "name": "India tour of England 2026",
                  "events": [{
                    "id": "1490237",
                    "name": "India vs England",
                    "date": "2026-08-27T10:00:00Z",
                    "status": {"type": {"shortDetail": "India 161/5, 18 ov"}},
                    "competitors": [
                      {"displayName": "India", "score": "161/5"},
                      {"displayName": "England", "score": "155"}
                    ]
                  }]
                }]
              }]
            }
            """.trimIndent(),
        )

        val events = client.parseHeader(payload, "Cricket")

        assertEquals(1, events.size)
        assertEquals("India", events.single().sides.first().name)
        assertEquals("161/5", events.single().sides.first().score)
        assertTrue(events.single().matchScore("India England cricket score") > 0)
    }
}

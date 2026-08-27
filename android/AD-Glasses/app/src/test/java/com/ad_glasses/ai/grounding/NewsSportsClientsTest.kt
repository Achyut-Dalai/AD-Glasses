package com.ad_glasses.ai.grounding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsSportsClientsTest {
    @Test
    fun googleNewsParserKeepsHeadlinePublisherDateAndLink() {
        val xml = """
            <rss><channel>
              <item>
                <title>Example headline</title>
                <link>https://news.google.com/articles/example</link>
                <pubDate>Thu, 27 Aug 2026 03:00:00 GMT</pubDate>
                <source url="https://example.com">Example Publisher</source>
              </item>
            </channel></rss>
        """.trimIndent()

        val items = GoogleNewsRssClient().parse(xml)

        assertEquals(1, items.size)
        assertEquals("Example headline", items.single().title)
        assertEquals("Example Publisher", items.single().source)
        assertEquals("Thu, 27 Aug 2026 03:00:00 GMT", items.single().publishedAt)
        assertEquals("https://news.google.com/articles/example", items.single().link)
    }

    @Test
    fun espnParserIsSportAgnostic() {
        val xml = """
            <rss><channel>
              <item><title>Lakers win season opener</title><link>https://www.espn.com/nba/story/1</link></item>
              <item><title>Verstappen takes pole at Belgian Grand Prix</title><link>https://www.espn.com/f1/story/2</link></item>
              <item><title>Alcaraz advances at US Open</title><link>https://www.espn.com/tennis/story/3</link></item>
              <item><title>Arsenal prepare for league clash</title><link>https://www.espn.com/soccer/story/4</link></item>
            </channel></rss>
        """.trimIndent()

        val items = EspnSportsClient().parse(xml)

        assertEquals(4, items.size)
        assertTrue(items.any { it.link.contains("/nba/") })
        assertTrue(items.any { it.link.contains("/f1/") })
        assertTrue(items.any { it.link.contains("/tennis/") })
        assertTrue(items.any { it.link.contains("/soccer/") })
    }

    @Test
    fun specificSportsQueryUsesGenericTokenOverlapOnly() {
        val items = listOf(
            SyndicatedHeadline("Lakers beat Celtics in overtime", "https://www.espn.com/nba/1", null, null),
            SyndicatedHeadline("Arsenal beat Chelsea at Emirates", "https://www.espn.com/soccer/2", null, null),
            SyndicatedHeadline("Verstappen wins Belgian Grand Prix", "https://www.espn.com/f1/3", null, null),
        )

        val matches = EspnSportsClient().selectRelevant("Lakers NBA result", items)

        assertEquals(1, matches.size)
        assertTrue(matches.single().title.contains("Lakers"))
    }

    @Test
    fun unrelatedEspnHeadlinesAreRejectedSoWebFallbackCanRun() {
        val items = listOf(
            SyndicatedHeadline("Lakers beat Celtics in overtime", "https://www.espn.com/nba/1", null, null),
            SyndicatedHeadline("Arsenal beat Chelsea at Emirates", "https://www.espn.com/soccer/2", null, null),
        )

        val matches = EspnSportsClient().selectRelevant("India Sri Lanka cricket", items)

        assertTrue(matches.isEmpty())
    }
}

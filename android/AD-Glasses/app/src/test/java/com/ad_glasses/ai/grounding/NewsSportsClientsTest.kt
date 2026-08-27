package com.ad_glasses.ai.grounding

import org.junit.Assert.assertEquals
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
}

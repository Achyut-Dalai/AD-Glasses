package com.ad_glasses.ai.grounding

import org.junit.Assert.assertEquals
import org.junit.Test

class KnowledgeApiClientsTest {
    private val currency = FrankfurterCurrencyClient()

    @Test
    fun parsesFrankfurterV2RateArray() {
        val quote = currency.parseQuote(
            body = """[{"date":"2026-08-26","base":"USD","quote":"INR","rate":87.42}]""",
            expectedBase = "USD",
            expectedQuote = "INR",
        )

        assertEquals("USD", quote.base)
        assertEquals("INR", quote.quote)
        assertEquals(87.42, quote.rate, 0.0001)
        assertEquals("2026-08-26", quote.date)
    }

    @Test
    fun parsesLegacyRatesObjectForFallbackCompatibility() {
        val quote = currency.parseQuote(
            body = """{"base":"EUR","date":"2026-08-26","rates":{"INR":101.25}}""",
            expectedBase = "EUR",
            expectedQuote = "INR",
        )

        assertEquals("EUR", quote.base)
        assertEquals("INR", quote.quote)
        assertEquals(101.25, quote.rate, 0.0001)
    }
}

package com.ad_glasses.ai.router

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamingUrlTest {
    @Test
    fun gemini_stream_url_uses_sse_endpoint() {
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:streamGenerateContent?alt=sse",
            geminiStreamGenerateContentUrl(
                "https://generativelanguage.googleapis.com/v1beta/",
                "models/gemini-2.0-flash",
            ),
        )
    }
}

package com.ad_glasses.ai.router

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiNativeRoutingTest {
    @Test
    fun managedGoogleBaseBuildsNativeGenerateContentEndpoint() {
        val base = ApiProvider.GOOGLE.resolveBaseUrl(
            "https://generativelanguage.googleapis.com/v1beta/openai/",
        )

        val endpoint = geminiGenerateContentUrl(base, "models/gemini-3.7-flash")

        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.7-flash:generateContent",
            endpoint,
        )
        assertFalse(endpoint.contains("/openai/"))
    }

    @Test
    fun nativeModelDiscoveryRequestsExpandedInitialPage() {
        val base = ApiProvider.GOOGLE.resolveBaseUrl("https://example.invalid/ignored")

        val endpoint = geminiModelsUrl(base)

        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models?pageSize=1000",
            endpoint,
        )
        assertTrue(endpoint.endsWith("pageSize=1000"))
    }

    @Test
    fun nativeGeminiMediaSupportsExpectedImageAndAudioTypes() {
        assertEquals("image/heic", geminiImageMimeType("HEIC"))
        assertEquals("image/heif", geminiImageMimeType("heif"))
        assertEquals("image/avif", geminiImageMimeType(".avif"))
        assertEquals("audio/webm", geminiAudioMimeType("webm"))
        assertEquals("audio/flac", geminiAudioMimeType("FLAC"))
        assertEquals("audio/aac", geminiAudioMimeType(".aac"))
    }
}

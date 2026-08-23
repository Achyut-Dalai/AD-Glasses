package com.ad_glasses.ai.router

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class GeminiNativeRoutingTest {
    @Test
    fun googleDefaultsToNativeGeminiRest() {
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta",
            ApiProvider.GOOGLE.defaultBaseUrl,
        )
        assertEquals("gemini-flash-latest", ApiProvider.GOOGLE.defaultModel)
        assertFalse(ApiProvider.GOOGLE.defaultBaseUrl.contains("/openai"))
    }

    @Test
    fun legacyGoogleOpenAiBaseIsNormalizedToNativeBase() {
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta",
            normalizeProviderBaseUrl(
                ApiProvider.GOOGLE,
                "https://generativelanguage.googleapis.com/v1beta/openai",
            ),
        )
    }

    @Test
    fun pastedGenerateContentEndpointIsReducedToBaseUrl() {
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta",
            normalizeProviderBaseUrl(
                ApiProvider.GOOGLE,
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent",
            ),
        )
    }

    @Test
    fun nativeGenerateContentEndpointUsesSelectedModel() {
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent",
            geminiGenerateContentUrl(
                "https://generativelanguage.googleapis.com/v1beta",
                "models/gemini-flash-latest",
            ),
        )
    }

    @Test
    fun nativeModelDiscoveryUsesGeminiModelsApi() {
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models?pageSize=1000",
            geminiModelsUrl("https://generativelanguage.googleapis.com/v1beta"),
        )
    }
}

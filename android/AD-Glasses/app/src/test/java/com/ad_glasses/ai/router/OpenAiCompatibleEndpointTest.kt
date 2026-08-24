package com.ad_glasses.ai.router

import org.junit.Assert.assertEquals
import org.junit.Test

class OpenAiCompatibleEndpointTest {
    @Test
    fun groq_base_url_keeps_openai_v1_path() {
        assertEquals(
            "https://api.groq.com/openai/v1",
            OpenAiCompatibleEndpoint.normalizeBaseUrl("https://api.groq.com/openai/v1/"),
        )
        assertEquals(
            "https://api.groq.com/openai/v1/chat/completions",
            OpenAiCompatibleEndpoint.chatCompletionsUrl("https://api.groq.com/openai/v1"),
        )
    }

    @Test
    fun openrouter_uses_its_openai_compatible_chat_endpoint() {
        assertEquals(
            "https://openrouter.ai/api/v1/chat/completions",
            OpenAiCompatibleEndpoint.chatCompletionsUrl("https://openrouter.ai/api/v1"),
        )
        assertEquals(
            "Bearer sk-or-v1-example",
            OpenAiCompatibleEndpoint.authorizationHeader("sk-or-v1-example"),
        )
    }

    @Test
    fun pasted_resource_urls_are_reduced_to_the_provider_base() {
        assertEquals(
            "https://api.groq.com/openai/v1",
            OpenAiCompatibleEndpoint.normalizeBaseUrl(
                "https://api.groq.com/openai/v1/chat/completions",
            ),
        )
        assertEquals(
            "https://api.groq.com/openai/v1",
            OpenAiCompatibleEndpoint.normalizeBaseUrl("https://api.groq.com/openai/v1/models"),
        )
    }

    @Test
    fun provider_specific_base_paths_are_not_removed() {
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/openai",
            OpenAiCompatibleEndpoint.normalizeBaseUrl(
                "https://generativelanguage.googleapis.com/v1beta/openai/",
            ),
        )
    }

    @Test
    fun bearer_prefix_header_and_quotes_are_normalized_once() {
        assertEquals(
            "gsk_example",
            OpenAiCompatibleEndpoint.normalizeBearerCredential("Bearer gsk_example"),
        )
        assertEquals(
            "gsk_example",
            OpenAiCompatibleEndpoint.normalizeBearerCredential("Authorization: Bearer \"gsk_example\""),
        )
        assertEquals(
            "Bearer gsk_example",
            OpenAiCompatibleEndpoint.authorizationHeader("bearer gsk_example"),
        )
        assertEquals(
            "Bearer gsk_example",
            OpenAiCompatibleEndpoint.authorizationHeader("gsk_example"),
        )
    }

    @Test
    fun groq_provider_uses_official_managed_endpoint() {
        assertEquals("https://api.groq.com/openai/v1", ApiProvider.GROQ.defaultBaseUrl)
        assertEquals("https://api.groq.com/openai/v1", ApiProvider.GROQ.resolveBaseUrl("https://wrong.example/v1"))
        assertEquals(
            "https://api.groq.com/openai/v1/chat/completions",
            OpenAiCompatibleEndpoint.chatCompletionsUrl(ApiProvider.GROQ.defaultBaseUrl),
        )
    }

    @Test
    fun custom_profile_keeps_a_groq_or_openrouter_base_url() {
        assertEquals(
            "https://api.groq.com/openai/v1",
            ApiProvider.CUSTOM.resolveBaseUrl("https://api.groq.com/openai/v1/"),
        )
        assertEquals(
            "https://openrouter.ai/api/v1",
            ApiProvider.CUSTOM.resolveBaseUrl("https://openrouter.ai/api/v1/chat/completions"),
        )
    }

    @Test
    fun openrouter_provider_defaults_to_requested_free_gemma_model() {
        assertEquals("https://openrouter.ai/api/v1", ApiProvider.OPENROUTER.defaultBaseUrl)
        assertEquals("google/gemma-4-26b-a4b-it:free", ApiProvider.OPENROUTER.defaultModel)
        assertEquals(
            "https://openrouter.ai/api/v1",
            ApiProvider.OPENROUTER.resolveBaseUrl("https://wrong.example/v1"),
        )
        assertEquals(
            "https://openrouter.ai/api/v1/chat/completions",
            OpenAiCompatibleEndpoint.chatCompletionsUrl(ApiProvider.OPENROUTER.defaultBaseUrl),
        )
    }
}

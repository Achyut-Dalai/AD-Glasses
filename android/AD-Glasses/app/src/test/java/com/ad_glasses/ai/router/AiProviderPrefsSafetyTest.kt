package com.ad_glasses.ai.router

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AiProviderPrefsSafetyTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("ai_provider_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun builtInProvidersOwnTheirEndpointsAndCustomDoesNot() {
        assertTrue(ApiProvider.OPENAI.endpointManagedByApp)
        assertTrue(ApiProvider.GOOGLE.endpointManagedByApp)
        assertTrue(ApiProvider.DEEPSEEK.endpointManagedByApp)
        assertTrue(ApiProvider.OPENROUTER.endpointManagedByApp)
        assertFalse(ApiProvider.CUSTOM.endpointManagedByApp)

        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta",
            ApiProvider.GOOGLE.resolveBaseUrl("https://example.invalid/v1beta/openai"),
        )
        assertEquals(
            "https://custom.example.test/v1",
            ApiProvider.CUSTOM.resolveBaseUrl("https://custom.example.test/v1/"),
        )
    }

    @Test
    fun geminiModelInputNormalizesNativeNamesAndFullGenerateContentUrls() {
        assertEquals("gemini-3.7-flash", ApiProvider.GOOGLE.normalizeModelId("models/gemini-3.7-flash"))
        assertEquals(
            "gemini-flash-latest",
            ApiProvider.GOOGLE.normalizeModelId(
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent",
            ),
        )
    }

    @Test
    fun retiredRemoteProviderWiresMigrateToCloud() {
        listOf("cli_relay", "company_backend", "mock", "gemini", "chatgpt", "phone_assistant")
            .forEach { legacy ->
                assertEquals(AiProviderType.CLOUD_API, AiProviderType.fromWire(legacy))
            }
    }

    @Test
    fun realtimeRelayIsOptInAndNeverRestoredByDefault() {
        assertEquals("", AiProviderPrefs.getRelayBaseUrl(context))

        AiProviderPrefs.setRelayBaseUrl(context, "https://relay.example.test/")

        assertEquals("https://relay.example.test", AiProviderPrefs.getRelayBaseUrl(context))
        assertTrue(AiProviderPrefs.isRelayConfigured(context))
    }
}

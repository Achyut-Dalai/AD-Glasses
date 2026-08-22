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
    fun freshInstallDefaultsToCloudAndOpenAiProvider() {
        assertEquals(AiProviderType.CLOUD_API, AiProviderPrefs.getProvider(context))
        assertEquals(ApiProvider.OPENAI, AiProviderPrefs.getApiProvider(context))
        assertFalse(AiProviderPrefs.isRelayConfigured(context))
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

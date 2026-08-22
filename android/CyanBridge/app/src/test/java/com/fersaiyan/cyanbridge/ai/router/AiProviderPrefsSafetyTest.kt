package com.fersaiyan.cyanbridge.ai.router

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
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
    fun freshInstallDefaultsToApiTokenAndOpenAiProvider() {
        assertEquals(AiProviderType.API_TOKEN, AiProviderPrefs.getProvider(context))
        assertEquals(ApiProvider.OPENAI, AiProviderPrefs.getApiProvider(context))
    }
}

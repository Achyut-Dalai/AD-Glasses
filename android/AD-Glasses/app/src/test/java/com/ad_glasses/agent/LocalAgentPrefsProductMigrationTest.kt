package com.ad_glasses.agent

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ad_glasses.shared.settings.AgentProviderType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalAgentPrefsProductMigrationTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("local_agent_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun unsetProductDefaultsUseCloudProvider() {
        assertEquals(AgentProviderType.CLOUD_AI, LocalAgentPrefs.getProviderType(context))
    }

    @Test
    fun retiredProviderValuesMigrateToCloudAndAreCleared() {
        val prefs = context.getSharedPreferences("local_agent_prefs", Context.MODE_PRIVATE)
        listOf("LOCAL_AGENT", "LOCAL", "LEGACY_PROVIDER", "GEMINI", "CHAT_GPT").forEach { legacy ->
            prefs.edit().putString("provider_type", legacy).commit()

            assertEquals(AgentProviderType.CLOUD_AI, LocalAgentPrefs.getProviderType(context))
            assertFalse(prefs.contains("provider_type"))
        }
    }
}

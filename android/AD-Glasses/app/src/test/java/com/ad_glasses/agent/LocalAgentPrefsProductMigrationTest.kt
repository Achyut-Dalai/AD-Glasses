package com.ad_glasses.agent

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ad_glasses.shared.settings.AgentProviderType
import org.junit.Assert.assertEquals
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
    fun oldProviderAndAssistantValuesResolveToCloud() {
        context.getSharedPreferences("local_agent_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("provider_type", "LEGACY_PROVIDER")
            .putString("glasses_assistant_mode", "PHONE_ASSISTANT")
            .commit()

        assertEquals(AgentProviderType.CLOUD_AI, LocalAgentPrefs.getProviderType(context))
    }

    @Test
    fun legacyNamedAssistantValuesDoNotOverrideExplicitLocalProvider() {
        val prefs = context.getSharedPreferences("local_agent_prefs", Context.MODE_PRIVATE)
        listOf("GEMINI", "CHAT_GPT", "PHONE_DEFAULT", "CHOSEN_PROVIDER").forEach { legacy ->
            prefs.edit()
                .putString("provider_type", AgentProviderType.LOCAL_AGENT.name)
                .putString("glasses_assistant_mode", legacy)
                .commit()

            assertEquals(AgentProviderType.LOCAL_AGENT, LocalAgentPrefs.getProviderType(context))
        }
    }
}

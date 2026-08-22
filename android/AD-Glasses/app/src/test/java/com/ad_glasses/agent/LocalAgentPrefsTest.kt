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
class LocalAgentPrefsTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("local_agent_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `provider type persists every supported selection`() {
        AgentProviderType.entries.forEach { provider ->
            LocalAgentPrefs.setProviderType(context, provider)
            assertEquals(provider, LocalAgentPrefs.getProviderType(context))
        }
    }

    @Test
    fun `legacy assistant selection key does not alter cloud default`() {
        val prefs = context.getSharedPreferences("local_agent_prefs", Context.MODE_PRIVATE)
        listOf("GEMINI", "CHAT_GPT", "PHONE_DEFAULT", "CHOSEN_PROVIDER", "PHONE_ASSISTANT").forEach { legacy ->
            prefs.edit()
                .remove("provider_type")
                .putString("glasses_assistant_mode", legacy)
                .commit()

            assertEquals(AgentProviderType.CLOUD_AI, LocalAgentPrefs.getProviderType(context))
        }
    }
}

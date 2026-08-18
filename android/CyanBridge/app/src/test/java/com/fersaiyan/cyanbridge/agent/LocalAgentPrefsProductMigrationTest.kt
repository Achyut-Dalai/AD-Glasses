package com.fersaiyan.cyanbridge.agent

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.fersaiyan.cyanbridge.shared.glasses.GlassesAssistantMode
import com.fersaiyan.cyanbridge.shared.settings.AgentProviderType
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
    fun unsetProductDefaultsUseInternalRelayAndConfiguredAiMode() {
        assertEquals(AgentProviderType.PRO_SUBSCRIPTION, LocalAgentPrefs.getProviderType(context))
        assertEquals(GlassesAssistantMode.CUSTOM_AI_PROVIDER, LocalAgentPrefs.getGlassesAssistantMode(context))
    }

    @Test
    fun explicitTaskerProviderAndPhoneAssistantSelectionsRemainAvailable() {
        context.getSharedPreferences("local_agent_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("provider_type", AgentProviderType.TASKER.name)
            .putString("glasses_assistant_mode", GlassesAssistantMode.PHONE_ASSISTANT.name)
            .commit()

        assertEquals(AgentProviderType.TASKER, LocalAgentPrefs.getProviderType(context))
        assertEquals(GlassesAssistantMode.PHONE_ASSISTANT, LocalAgentPrefs.getGlassesAssistantMode(context))
    }

    @Test
    fun oldNamedAssistantValuesDoNotBecomeImplicitPhoneAutomation() {
        context.getSharedPreferences("local_agent_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("glasses_assistant_mode", "CHAT_GPT")
            .commit()

        assertEquals(GlassesAssistantMode.CUSTOM_AI_PROVIDER, LocalAgentPrefs.getGlassesAssistantMode(context))
    }
}

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
    fun unsetProductDefaultsUsePhoneAssistantAndNoCloudProvider() {
        assertEquals(AgentProviderType.LOCAL_AGENT, LocalAgentPrefs.getProviderType(context))
        assertEquals(GlassesAssistantMode.PHONE_ASSISTANT, LocalAgentPrefs.getGlassesAssistantMode(context))
    }

    @Test
    fun oldTaskerProviderMigratesToNativeLocalProvider() {
        context.getSharedPreferences("local_agent_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("provider_type", "TASKER")
            .putString("glasses_assistant_mode", GlassesAssistantMode.PHONE_ASSISTANT.name)
            .commit()

        assertEquals(AgentProviderType.LOCAL_AGENT, LocalAgentPrefs.getProviderType(context))
        assertEquals(GlassesAssistantMode.PHONE_ASSISTANT, LocalAgentPrefs.getGlassesAssistantMode(context))
    }

    @Test
    fun oldNamedAssistantValuesMigrateToExplicitPhoneAssistantMode() {
        context.getSharedPreferences("local_agent_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("glasses_assistant_mode", "CHAT_GPT")
            .commit()

        assertEquals(GlassesAssistantMode.PHONE_ASSISTANT, LocalAgentPrefs.getGlassesAssistantMode(context))
    }
}

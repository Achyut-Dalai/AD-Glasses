package com.fersaiyan.cyanbridge.agent

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.fersaiyan.cyanbridge.shared.glasses.GlassesAssistantMode
import com.fersaiyan.cyanbridge.shared.settings.AgentProviderType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalAgentPrefsMigrationTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("local_agent_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun freshInstallUsesCloudAsPrimaryProvider() {
        assertEquals(AgentProviderType.PRO_SUBSCRIPTION, LocalAgentPrefs.getProviderType(context))
    }

    @Test
    fun retiredTaskerProviderMigratesToCloud() {
        context.getSharedPreferences("local_agent_prefs", Context.MODE_PRIVATE)
            .edit().putString("provider_type", "TASKER").commit()
        assertEquals(AgentProviderType.PRO_SUBSCRIPTION, LocalAgentPrefs.getProviderType(context))
    }

    @Test
    fun legacyPhoneAssistantModeMigratesToAdOwnedInference() {
        context.getSharedPreferences("local_agent_prefs", Context.MODE_PRIVATE)
            .edit().putString("glasses_assistant_mode", "PHONE_ASSISTANT").commit()
        assertEquals(GlassesAssistantMode.CUSTOM_AI_PROVIDER, LocalAgentPrefs.getGlassesAssistantMode(context))
    }

    @Test
    fun phoneUiAutomationCannotBeReenabledAsAnAiRoute() {
        LocalAgentPrefs.setLocalAgentAutomationEnabled(context, true)
        assertFalse(LocalAgentPrefs.isLocalAgentAutomationEnabled(context))
    }
}

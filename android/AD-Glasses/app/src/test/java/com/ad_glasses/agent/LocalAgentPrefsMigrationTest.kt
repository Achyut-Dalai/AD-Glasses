package com.ad_glasses.agent

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ad_glasses.shared.glasses.GlassesAssistantMode
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
class LocalAgentPrefsMigrationTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("local_agent_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun freshInstallUsesCloudAsPrimaryProvider() {
        assertEquals(AgentProviderType.CLOUD_AI, LocalAgentPrefs.getProviderType(context))
    }

    @Test
    fun retiredProviderValueMigratesToCloud() {
        context.getSharedPreferences("local_agent_prefs", Context.MODE_PRIVATE)
            .edit().putString("provider_type", "LEGACY_PROVIDER").commit()
        assertEquals(AgentProviderType.CLOUD_AI, LocalAgentPrefs.getProviderType(context))
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

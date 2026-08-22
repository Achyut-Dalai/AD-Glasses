package com.ad_glasses.ai.assistant

import org.junit.Assert.assertEquals
import org.junit.Test

class AiProviderTypeMigrationTest {
    @Test
    fun consumerAssistantWireValuesMigrateToCloud() {
        listOf("gemini", "chatgpt", "phone_assistant", "default_assistant").forEach { legacy ->
            assertEquals(AiProviderType.CLOUD, AiProviderType.fromWireName(legacy))
        }
    }

    @Test
    fun localWireValueStillResolvesToLocal() {
        assertEquals(AiProviderType.LOCAL_AGENT, AiProviderType.fromWireName("local_agent"))
    }
}

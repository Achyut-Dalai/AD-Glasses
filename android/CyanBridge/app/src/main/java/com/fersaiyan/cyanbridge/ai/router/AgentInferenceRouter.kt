package com.fersaiyan.cyanbridge.ai.router

import android.content.Context
import com.fersaiyan.cyanbridge.shared.settings.AgentProviderType
import com.fersaiyan.cyanbridge.agent.LocalAgentPrefs as AutomationPrefs
import com.fersaiyan.cyanbridge.agent.ProSubscriptionAiPrefs
import com.fersaiyan.cyanbridge.localmodels.provider.LocalModelsProvider

enum class AgentInferencePurpose {
    CLASSIFICATION,
    UI_PLANNING,
}

/** Resolves the two existing provider preference layers into one agent inference path. */
object AgentInferenceRouter {
    private val localModelsProvider = LocalModelsProvider()

    suspend fun complete(
        context: Context,
        purpose: AgentInferencePurpose,
        sessionId: String,
        systemPrompt: String,
        userPrompt: String,
        providerType: AgentProviderType = AutomationPrefs.getProviderType(context),
    ): String {
        val messages = listOf(
            mapOf("role" to "system", "content" to systemPrompt),
            mapOf("role" to "user", "content" to userPrompt),
        )

        return when (providerType) {
            AgentProviderType.LOCAL_AGENT -> localModelsProvider.streamChat(
                context = context,
                messages = messages,
                maxTokens = if (purpose == AgentInferencePurpose.CLASSIFICATION) 256 else 512,
            )

            AgentProviderType.PRO_SUBSCRIPTION -> CliRelayClient.chat(
                context = context,
                chatId = sessionId,
                prompt = userPrompt,
                messages = messages,
                modelOverride = ProSubscriptionAiPrefs.getTasksModel(context),
            ).getOrThrow()

            AgentProviderType.TASKER -> completeUsingAiProviderPrefs(
                context = context,
                purpose = purpose,
                sessionId = sessionId,
                userPrompt = userPrompt,
                messages = messages,
            )
        }
    }

    private suspend fun completeUsingAiProviderPrefs(
        context: Context,
        purpose: AgentInferencePurpose,
        sessionId: String,
        userPrompt: String,
        messages: List<Map<String, String>>,
    ): String {
        return when (AiProviderPrefs.getProvider(context)) {
            AiProviderType.CLI_RELAY -> CliRelayClient.chat(
                context = context,
                chatId = sessionId,
                prompt = userPrompt,
                messages = messages,
            ).getOrThrow()

            AiProviderType.LOCAL_MODELS -> localModelsProvider.streamChat(
                context = context,
                messages = messages,
                maxTokens = if (purpose == AgentInferencePurpose.CLASSIFICATION) 256 else 512,
            )

            AiProviderType.MOCK -> throw IllegalStateException("Mock provider cannot classify or plan agent tasks")
            AiProviderType.COMPANY_BACKEND -> throw IllegalStateException("Company backend is not configured for agent tasks")
        }
    }
}

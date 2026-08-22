package com.fersaiyan.cyanbridge.ai.router

import android.content.Context
import com.fersaiyan.cyanbridge.shared.settings.AgentProviderType
import com.fersaiyan.cyanbridge.agent.LocalAgentPrefs as AutomationPrefs
import com.fersaiyan.cyanbridge.agent.CloudAiPrefs
import com.fersaiyan.cyanbridge.localmodels.provider.LocalModelsProvider
import kotlinx.coroutines.CancellationException
import java.io.File

enum class AgentInferencePurpose {
    CLASSIFICATION,
    UI_PLANNING,
}

data class AgentInferenceResult(
    val content: String,
    val usedImage: Boolean,
    val mediaStatus: String,
)

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
        onToken: ((String) -> Unit)? = null,
    ): String {
        return completeText(
            context = context,
            purpose = purpose,
            sessionId = sessionId,
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            providerType = providerType,
            onToken = onToken,
        )
    }

    /**
     * Plans with an image only when the caller supplied one. Remote image transport is explicitly
     * guarded here as a defense in depth measure even if the caller already checked its setting.
     */
    suspend fun completeUiPlanning(
        context: Context,
        sessionId: String,
        systemPrompt: String,
        userPrompt: String,
        imagePath: String?,
        allowRemoteImageUpload: Boolean,
        providerType: AgentProviderType = AutomationPrefs.getProviderType(context),
        onToken: ((String) -> Unit)? = null,
    ): AgentInferenceResult {
        val usableImagePath = imagePath?.trim()?.takeIf { File(it).isFile }
        if (!imagePath.isNullOrBlank() && usableImagePath == null) {
            throw IllegalStateException("The requested image file is missing. AD did not fall back to a text-only answer.")
        }
        if (usableImagePath == null) {
            return AgentInferenceResult(
                content = completeText(
                    context,
                    AgentInferencePurpose.UI_PLANNING,
                    sessionId,
                    systemPrompt,
                    userPrompt,
                    providerType,
                    onToken,
                ),
                usedImage = false,
                mediaStatus = "Text-only planning",
            )
        }
        if (isRemotePlanner(context, providerType) && !allowRemoteImageUpload) {
            throw IllegalStateException(
                "Remote image upload is off. The image was not sent and AD did not silently retry without it.",
            )
        }

        val imageContent = try {
            when (providerType) {
                AgentProviderType.LOCAL_AGENT -> localModelsProvider.streamChat(
                    context = context,
                    messages = messages(systemPrompt, userPrompt),
                    imagePaths = listOf(usableImagePath),
                    maxTokens = UI_PLANNING_MAX_TOKENS,
                    onToken = onToken,
                )

                AgentProviderType.PRO_SUBSCRIPTION -> CliRelayClient.imageQuery(
                    context = context,
                    imagePath = usableImagePath,
                    prompt = multimodalPrompt(systemPrompt, userPrompt),
                    modelOverride = CloudAiPrefs.getTasksModel(context),
                ).getOrThrow()

            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw IllegalStateException(
                "The selected ${providerType.label} route could not analyze this image. Nothing was silently retried without it: " +
                    (error.message ?: error::class.java.simpleName),
                error,
            )
        }

        return AgentInferenceResult(
            content = imageContent,
            usedImage = true,
            mediaStatus = "Image attached to the selected planner for this step.",
        )
    }

    fun isRemotePlanner(
        context: Context,
        providerType: AgentProviderType = AutomationPrefs.getProviderType(context),
    ): Boolean {
        return when (providerType) {
            AgentProviderType.PRO_SUBSCRIPTION -> true
            AgentProviderType.LOCAL_AGENT -> false
        }
    }

    private suspend fun completeText(
        context: Context,
        purpose: AgentInferencePurpose,
        sessionId: String,
        systemPrompt: String,
        userPrompt: String,
        providerType: AgentProviderType,
        onToken: ((String) -> Unit)?,
    ): String {
        val messages = messages(systemPrompt, userPrompt)

        return when (providerType) {
            AgentProviderType.LOCAL_AGENT -> localModelsProvider.streamChat(
                context = context,
                messages = messages,
                maxTokens = if (purpose == AgentInferencePurpose.CLASSIFICATION) 256 else 512,
                onToken = onToken,
            )

            AgentProviderType.PRO_SUBSCRIPTION -> CliRelayClient.chat(
                context = context,
                chatId = sessionId,
                prompt = userPrompt,
                messages = messages,
                modelOverride = CloudAiPrefs.getTasksModel(context),
            ).getOrThrow()

        }
    }

    private fun messages(systemPrompt: String, userPrompt: String): List<Map<String, String>> = listOf(
        mapOf("role" to "system", "content" to systemPrompt),
        mapOf("role" to "user", "content" to userPrompt),
    )

    private fun multimodalPrompt(systemPrompt: String, userPrompt: String): String = buildString {
        appendLine("System instructions:")
        appendLine(systemPrompt)
        appendLine()
        appendLine("Planning request:")
        append(userPrompt)
    }

    private const val UI_PLANNING_MAX_TOKENS = 512
}

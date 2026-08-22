package com.fersaiyan.cyanbridge.ai.router

import android.content.Context
import com.fersaiyan.cyanbridge.agent.LocalAgentPrefs as InferencePrefs
import com.fersaiyan.cyanbridge.localmodels.provider.LocalModelsProvider
import com.fersaiyan.cyanbridge.shared.settings.AgentProviderType
import java.io.File
import kotlinx.coroutines.CancellationException

enum class AgentInferencePurpose {
    CLASSIFICATION,
    UI_PLANNING,
}

data class AgentInferenceResult(
    val content: String,
    val usedImage: Boolean,
    val mediaStatus: String,
)

/** Inference boundary with only two transports: encrypted cloud API or on-device local model. */
object AgentInferenceRouter {
    private val localModelsProvider = LocalModelsProvider()

    suspend fun complete(
        context: Context,
        purpose: AgentInferencePurpose,
        sessionId: String,
        systemPrompt: String,
        userPrompt: String,
        providerType: AgentProviderType = InferencePrefs.getProviderType(context),
        onToken: ((String) -> Unit)? = null,
    ): String = completeText(
        context = context,
        purpose = purpose,
        systemPrompt = systemPrompt,
        userPrompt = userPrompt,
        providerType = providerType,
        onToken = onToken,
    )

    suspend fun completeUiPlanning(
        context: Context,
        sessionId: String,
        systemPrompt: String,
        userPrompt: String,
        imagePath: String?,
        allowRemoteImageUpload: Boolean,
        providerType: AgentProviderType = InferencePrefs.getProviderType(context),
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
                    systemPrompt,
                    userPrompt,
                    providerType,
                    onToken,
                ),
                usedImage = false,
                mediaStatus = "Text-only inference",
            )
        }
        if (isRemotePlanner(providerType) && !allowRemoteImageUpload) {
            throw IllegalStateException("Remote image upload is off. The image was not sent.")
        }

        val imageContent = try {
            when (providerType) {
                AgentProviderType.CLOUD_AI -> ApiTokenClient.image(
                    context = context,
                    systemPrompt = systemPrompt,
                    userPrompt = userPrompt,
                    imagePath = usableImagePath,
                    maxTokens = UI_PLANNING_MAX_TOKENS,
                ).getOrThrow()
                AgentProviderType.LOCAL_AGENT -> localModelsProvider.streamChat(
                    context = context,
                    messages = messages(systemPrompt, userPrompt),
                    imagePaths = listOf(usableImagePath),
                    maxTokens = UI_PLANNING_MAX_TOKENS,
                    onToken = onToken,
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw IllegalStateException(
                "The selected ${providerType.label} route could not analyze this image: " +
                    (error.message ?: error::class.java.simpleName),
                error,
            )
        }

        return AgentInferenceResult(
            content = imageContent,
            usedImage = true,
            mediaStatus = "Image attached to the selected inference route.",
        )
    }

    fun isRemotePlanner(providerType: AgentProviderType): Boolean =
        providerType == AgentProviderType.CLOUD_AI

    private suspend fun completeText(
        context: Context,
        purpose: AgentInferencePurpose,
        systemPrompt: String,
        userPrompt: String,
        providerType: AgentProviderType,
        onToken: ((String) -> Unit)?,
    ): String {
        val messages = messages(systemPrompt, userPrompt)
        val maxTokens = if (purpose == AgentInferencePurpose.CLASSIFICATION) 256 else 512
        return when (providerType) {
            AgentProviderType.CLOUD_AI -> ApiTokenClient.chat(
                context = context,
                messages = messages,
                maxTokens = maxTokens,
            ).getOrThrow()
            AgentProviderType.LOCAL_AGENT -> localModelsProvider.streamChat(
                context = context,
                messages = messages,
                maxTokens = maxTokens,
                onToken = onToken,
            )
        }
    }

    private fun messages(systemPrompt: String, userPrompt: String): List<Map<String, String>> = listOf(
        mapOf("role" to "system", "content" to systemPrompt),
        mapOf("role" to "user", "content" to userPrompt),
    )

    private const val UI_PLANNING_MAX_TOKENS = 512
}

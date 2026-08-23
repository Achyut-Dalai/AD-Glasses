package com.ad_glasses.ai.orchestrator

import android.content.Context
import com.ad_glasses.ai.router.AgentInferencePurpose
import com.ad_glasses.ai.router.AgentInferenceRouter

/** Android execution bridge for conversational, vision and explicit AD capability requests. */
class AndroidAssistantCapabilityExecutor(
    context: Context,
    private val onToken: ((String) -> Unit)? = null,
) : AssistantCapabilityExecutor {
    private val appContext = context.applicationContext
    private val capabilities = AndroidCapabilityCommandExecutor(context)

    override suspend fun answer(
        prompt: String,
        context: AssistantExecutionContext,
    ): AssistantResult {
        val effectivePrompt = buildPromptWithArtifact(prompt, context)
        val reply = AgentInferenceRouter.complete(
            context = appContext,
            purpose = AgentInferencePurpose.UI_PLANNING,
            sessionId = context.threadId,
            systemPrompt = conversationSystemPrompt(context),
            userPrompt = effectivePrompt,
            providerType = context.providerType,
            onToken = onToken,
            webRequested = context.useWeb,
        )
        return reply.toDisplaylessResult()
    }

    override suspend fun analyzeImage(
        prompt: String,
        imagePath: String?,
        context: AssistantExecutionContext,
    ): AssistantResult {
        if (imagePath.isNullOrBlank()) {
            return AssistantResult(
                spokenText = "I don’t have a usable frame for that yet.",
                richText = "This visual request has context, but no image frame was supplied to the selected vision engine.",
            )
        }

        val result = AgentInferenceRouter.completeUiPlanning(
            context = appContext,
            sessionId = context.threadId,
            systemPrompt = conversationSystemPrompt(context),
            userPrompt = prompt,
            imagePath = imagePath,
            allowRemoteImageUpload = com.ad_glasses.localagent.LocalAgentPrefs
                .isRemoteScreenshotUploadEnabled(appContext),
            providerType = context.providerType,
            onToken = onToken,
            webRequested = false,
        )
        return result.content.toDisplaylessResult()
    }

    override suspend fun executeCapabilityCommand(
        command: AssistantCapabilityCommand,
        context: AssistantExecutionContext,
    ): AssistantResult = capabilities.execute(command)

    private fun conversationSystemPrompt(context: AssistantExecutionContext): String = buildString {
        appendLine("You are AD, the conversational assistant for displayless smart glasses.")
        appendLine("Answer naturally and directly. Lead with the useful spoken answer and avoid giant tables.")
        appendLine("Maintain context across turns. The phone can hold richer detail, but do not ask the user to operate it unless genuinely needed.")
        appendLine("Do not claim to open apps, tap controls, change Android settings, or operate the phone UI. AD no longer exposes UI automation as an AI invocation method.")
        if (context.useWeb) {
            appendLine("Web access was explicitly enabled for this turn. Use the active provider's native search tool when available and ground current claims in those results.")
        }
        context.artifactContext?.takeIf { it.isNotBlank() }?.let {
            appendLine()
            appendLine("Current artifact context (trusted app context, not a user quote):")
            appendLine(it.take(16_000))
        }
        val prior = context.history.dropLast(1).takeLast(8)
        if (prior.isNotEmpty()) {
            appendLine()
            appendLine("Recent conversation:")
            prior.forEach { message ->
                append(message.role.name.lowercase())
                append(": ")
                appendLine(message.content.take(1_200))
            }
        }
    }

    private fun buildPromptWithArtifact(prompt: String, context: AssistantExecutionContext): String = buildString {
        context.artifactContext?.takeIf { it.isNotBlank() }?.let {
            appendLine("Current artifact context:")
            appendLine(it.take(16_000))
            appendLine()
        }
        append(prompt)
    }

    private fun String.toDisplaylessResult(): AssistantResult {
        val rich = trim()
        if (rich.isBlank()) return AssistantResult("I didn’t get a usable answer.")
        return AssistantResult(
            spokenText = AssistantSpokenResponsePolicy.forGlasses(rich),
            richText = rich,
        )
    }
}

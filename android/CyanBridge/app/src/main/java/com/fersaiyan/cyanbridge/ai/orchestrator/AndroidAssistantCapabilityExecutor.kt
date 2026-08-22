package com.fersaiyan.cyanbridge.ai.orchestrator

import android.content.Context
import com.fersaiyan.cyanbridge.ai.router.AiProviderPrefs
import com.fersaiyan.cyanbridge.ai.router.AgentInferencePurpose
import com.fersaiyan.cyanbridge.ai.router.AgentInferenceRouter
import com.fersaiyan.cyanbridge.localagent.LocalAgentController
import com.fersaiyan.cyanbridge.shared.settings.AgentProviderType

/** Android execution bridge for AD decisions. */
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
        val reply = if (context.useWeb && context.providerType != AgentProviderType.PRO_SUBSCRIPTION) {
            return AssistantResult(
                spokenText = "Web search is off for Local AI.",
                richText = "This turn stayed on-device. Select an explicitly configured cloud route before using web search.",
            )
        } else if (context.useWeb && !AiProviderPrefs.isRelayConfigured(appContext)) {
            return AssistantResult(
                spokenText = "Cloud web search is not configured.",
                richText = "Web search was requested, but no cloud endpoint is configured. Nothing was sent.",
            )
        } else if (context.useWeb) {
            RelayWebSearchClient.chat(
                context = appContext,
                threadId = context.threadId,
                prompt = buildPromptWithArtifact(prompt, context),
                history = context.history,
            ).getOrElse { error ->
                return AssistantResult(
                    spokenText = "Current information isn’t available through the configured route yet.",
                    richText = "Fresh information was selected for this turn, but execution failed: ${error.message ?: error::class.java.simpleName}",
                )
            }
        } else {
            AgentInferenceRouter.complete(
                context = appContext,
                purpose = AgentInferencePurpose.UI_PLANNING,
                sessionId = context.threadId,
                systemPrompt = conversationSystemPrompt(context),
                userPrompt = prompt,
                providerType = context.providerType,
                onToken = onToken,
            )
        }
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
                richText = "This visual request has artifact context, but no image frame was supplied to the selected vision engine.",
            )
        }

        val result = AgentInferenceRouter.completeUiPlanning(
            context = appContext,
            sessionId = context.threadId,
            systemPrompt = conversationSystemPrompt(context),
            userPrompt = prompt,
            imagePath = imagePath,
            allowRemoteImageUpload = context.providerType == AgentProviderType.PRO_SUBSCRIPTION &&
                com.fersaiyan.cyanbridge.localagent.LocalAgentPrefs
                    .isRemoteScreenshotUploadEnabled(appContext),
            providerType = context.providerType,
            onToken = onToken,
        )
        return result.content.toDisplaylessResult()
    }

    override suspend fun executePhoneAction(
        goal: String,
        context: AssistantExecutionContext,
    ): AssistantResult {
        val result = LocalAgentController.start(appContext, goal)
        return AssistantResult(
            spokenText = result.userMessage,
            richText = if (result.ok) {
                "AD Android action requested: $goal\n${result.userMessage}"
            } else {
                "AD Android action could not start: $goal\n${result.userMessage}${result.error?.let { "\n$it" }.orEmpty()}"
            },
        )
    }

    override suspend fun executeCapabilityCommand(
        command: AssistantCapabilityCommand,
        context: AssistantExecutionContext,
    ): AssistantResult = capabilities.execute(command)

    private fun conversationSystemPrompt(context: AssistantExecutionContext): String = buildString {
        appendLine("You are AD, the conversational assistant for displayless smart glasses.")
        appendLine("Answer naturally and directly. Lead with the useful spoken answer and avoid giant tables.")
        appendLine("Maintain context across turns. The phone can hold richer detail, but do not make the user operate it unless visual confirmation is genuinely needed.")
        appendLine("Prefer background tools and direct Android capabilities. Treat visible accessibility automation as an explicit last-resort fallback.")
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

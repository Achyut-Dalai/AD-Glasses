package com.fersaiyan.cyanbridge.ai.orchestrator

import android.content.Context
import com.fersaiyan.cyanbridge.ai.router.AgentInferencePurpose
import com.fersaiyan.cyanbridge.ai.router.AgentInferenceRouter
import com.fersaiyan.cyanbridge.automation.AutomationEventBroadcaster
import com.fersaiyan.cyanbridge.automation.AutomationExecutor
import com.fersaiyan.cyanbridge.automation.AutomationRoutePrefs
import com.fersaiyan.cyanbridge.localagent.LocalAgentController

/** Android execution bridge for AD decisions. */
class AndroidAssistantCapabilityExecutor(
    context: Context,
) : AssistantCapabilityExecutor {
    private val appContext = context.applicationContext
    private val modes = AndroidModeCommandExecutor(context)

    override suspend fun answer(
        prompt: String,
        context: AssistantExecutionContext,
    ): AssistantResult {
        val reply = if (context.useWeb) {
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
            allowRemoteImageUpload = true,
        )
        return result.content.toDisplaylessResult()
    }

    override suspend fun executePhoneAction(
        goal: String,
        context: AssistantExecutionContext,
    ): AssistantResult = when (AutomationRoutePrefs.getExecutor(appContext)) {
        AutomationExecutor.TASKER -> {
            AutomationEventBroadcaster.sendPhoneAction(appContext, goal)
            AssistantResult(
                spokenText = "Done. I sent that to background automation.",
                richText = "Background automation requested: $goal",
            )
        }

        AutomationExecutor.ACCESSIBILITY -> {
            val result = LocalAgentController.start(appContext, goal)
            AssistantResult(
                spokenText = result.userMessage,
                richText = if (result.ok) {
                    "Visible Android fallback requested: $goal\n${result.userMessage}"
                } else {
                    "Visible Android fallback could not start: $goal\n${result.userMessage}${result.error?.let { "\n$it" }.orEmpty()}"
                },
            )
        }
    }

    override suspend fun executeModeCommand(
        command: AssistantModeCommand,
        context: AssistantExecutionContext,
    ): AssistantResult = modes.execute(command)

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
        val spoken = rich
            .replace(Regex("\\s+"), " ")
            .let { text -> if (text.length <= 420) text else text.take(417).trimEnd() + "…" }
        return AssistantResult(spokenText = spoken, richText = rich)
    }
}

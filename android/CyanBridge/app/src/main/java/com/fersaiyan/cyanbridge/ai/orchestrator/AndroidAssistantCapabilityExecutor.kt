package com.fersaiyan.cyanbridge.ai.orchestrator

import android.content.Context
import com.fersaiyan.cyanbridge.ai.router.AgentInferencePurpose
import com.fersaiyan.cyanbridge.ai.router.AgentInferenceRouter
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
                prompt = prompt,
                history = context.history,
            ).getOrElse { error ->
                return AssistantResult(
                    spokenText = "Web Search isn’t available through the configured AI relay yet.",
                    richText = "Web Search was selected for this turn, but execution failed: ${error.message ?: error::class.java.simpleName}",
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
                spokenText = "I’m ready to look. I need a frame from the glasses camera first.",
                richText = "Vision request is waiting for an image from the existing glasses capture pipeline.",
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
    ): AssistantResult {
        val result = LocalAgentController.start(appContext, goal)
        return AssistantResult(
            spokenText = result.userMessage,
            richText = if (result.ok) {
                "Phone action requested: $goal\n${result.userMessage}"
            } else {
                "Phone action could not start: $goal\n${result.userMessage}${result.error?.let { "\n$it" }.orEmpty()}"
            },
        )
    }

    override suspend fun executeModeCommand(
        command: AssistantModeCommand,
        context: AssistantExecutionContext,
    ): AssistantResult = modes.execute(command)

    private fun conversationSystemPrompt(context: AssistantExecutionContext): String = buildString {
        appendLine("You are AD, the conversational assistant for displayless smart glasses.")
        appendLine("Answer naturally and directly. Lead with the useful spoken answer and avoid giant tables.")
        appendLine("Maintain context across turns. The phone can hold richer detail, but do not make the user operate it unless visual confirmation is genuinely needed.")
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

    private fun String.toDisplaylessResult(): AssistantResult {
        val rich = trim()
        if (rich.isBlank()) return AssistantResult("I didn’t get a usable answer.")
        val spoken = rich
            .replace(Regex("\\s+"), " ")
            .let { text -> if (text.length <= 420) text else text.take(417).trimEnd() + "…" }
        return AssistantResult(spokenText = spoken, richText = rich)
    }
}

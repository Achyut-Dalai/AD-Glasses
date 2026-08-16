package com.fersaiyan.cyanbridge.ai.orchestrator

import android.content.Context
import com.fersaiyan.cyanbridge.ai.router.AiAssistantRouter
import com.fersaiyan.cyanbridge.localagent.LocalAgentController

/**
 * Android execution bridge for AD decisions.
 *
 * Device transport remains in the existing capture/session code; this class only invokes
 * mature AI, web, vision, mode and Local Agent capabilities once the orchestrator has
 * decided what the turn means.
 */
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
            AiAssistantRouter.chatReply(
                context = appContext,
                chatId = context.threadId,
                userPrompt = prompt,
                messages = context.history.toRelayHistory(excludeTrailingPrompt = prompt),
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

        val reply = AiAssistantRouter.chatReplyStreaming(
            context = appContext,
            chatId = context.threadId,
            userPrompt = prompt,
            messages = context.history.toRelayHistory(excludeTrailingPrompt = prompt),
            imagePaths = listOf(imagePath),
            audioPath = null,
            callbacks = null,
        )
        return reply.toDisplaylessResult()
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

    private fun List<com.fersaiyan.cyanbridge.shared.chat.ChatMessage>.toRelayHistory(
        excludeTrailingPrompt: String,
    ): List<Map<String, String>> {
        val trimmedPrompt = excludeTrailingPrompt.trim()
        val usable = if (
            isNotEmpty() &&
            last().role == com.fersaiyan.cyanbridge.shared.chat.ChatRole.USER &&
            last().content.trim() == trimmedPrompt
        ) dropLast(1) else this

        return usable.map { message ->
            mapOf(
                "role" to message.role.name.lowercase(),
                "content" to message.content,
            )
        }
    }

    private fun String.toDisplaylessResult(): AssistantResult {
        val rich = trim()
        if (rich.isBlank()) return AssistantResult("I didn’t get a usable answer.")
        val spoken = rich
            .replace(Regex("\\s+"), " ")
            .let { text ->
                if (text.length <= 420) text
                else text.take(417).trimEnd() + "…"
            }
        return AssistantResult(spokenText = spoken, richText = rich)
    }
}

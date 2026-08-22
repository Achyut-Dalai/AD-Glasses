package com.ad_glasses.ai.assistant

import android.content.Context
import com.ad_glasses.ai.router.ApiTokenClient
import com.ad_glasses.shared.chat.ChatMessage
import com.ad_glasses.shared.chat.ChatRole

/** Legacy assistant-package adapter. Both outcomes are AD-owned: Cloud API or Local AI. */
class AiAssistantRouter(
    private val localAgentRouter: LocalAgentRouter = LocalAgentRouter(),
) {

    suspend fun generateReply(
        context: Context,
        providerType: AiProviderType,
        messages: List<ChatMessage>,
    ): String {
        return when (providerType) {
            AiProviderType.CLOUD -> ApiTokenClient.chat(
                context = context,
                messages = messages.map { message ->
                    mapOf(
                        "role" to if (message.role == ChatRole.USER) "user" else "assistant",
                        "content" to message.content,
                    )
                },
            ).getOrElse { error ->
                "Cloud AI unavailable (${error.message ?: error::class.java.simpleName})."
            }
            AiProviderType.LOCAL_AGENT -> localAgentRouter.generateReply(context, messages)
        }
    }
}

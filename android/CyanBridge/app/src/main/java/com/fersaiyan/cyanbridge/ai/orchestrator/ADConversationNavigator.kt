package com.fersaiyan.cyanbridge.ai.orchestrator

import android.content.Context
import android.content.Intent
import com.fersaiyan.cyanbridge.ui.ChatThreadActivity

/**
 * Opens the mature phone chat UI on the same durable thread used by AD.
 * This is an incremental bridge: the phone remains a rich continuation surface while
 * glasses/voice/vision orchestration migrates behind AssistantOrchestrator.
 */
object ADConversationNavigator {
    fun open(context: Context, prefill: String? = null) {
        val threadId = AssistantConversationSession.get(context).activeThreadId()
        context.startActivity(
            Intent(context, ChatThreadActivity::class.java).apply {
                putExtra(ChatThreadActivity.EXTRA_CHAT_ID, threadId)
                prefill?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    putExtra(ChatThreadActivity.EXTRA_PREFILL_MESSAGE, it)
                }
            },
        )
    }
}

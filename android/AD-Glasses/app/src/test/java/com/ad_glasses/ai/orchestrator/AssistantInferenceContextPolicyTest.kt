package com.ad_glasses.ai.orchestrator

import com.ad_glasses.shared.chat.ChatMessage
import com.ad_glasses.shared.chat.ChatRole
import org.junit.Assert.assertEquals
import org.junit.Test

class AssistantInferenceContextPolicyTest {
    @Test
    fun voice_keeps_only_last_three_prior_messages() {
        val now = 100_000L
        val history = (0 until 6).map { index ->
            message(
                id = "m$index",
                role = if (index % 2 == 0) ChatRole.USER else ChatRole.ASSISTANT,
                createdAt = now - (5 - index) * 2_000L,
            )
        } + message("current", ChatRole.USER, now)

        val prior = AssistantInferenceContextPolicy.priorMessages(
            history,
            surface = AssistantInputSurface.GLASSES_VOICE,
            nowMs = now,
        )

        assertEquals(listOf("m3", "m4", "m5"), prior.map { it.id })
    }

    @Test
    fun long_pause_does_not_discard_last_three_messages() {
        val now = 1_000_000L
        val history = listOf(
            message("old-user", ChatRole.USER, 10_000L),
            message("old-answer", ChatRole.ASSISTANT, 20_000L),
            message("follow-up", ChatRole.USER, 30_000L),
            message("follow-up-answer", ChatRole.ASSISTANT, 40_000L),
            message("current", ChatRole.USER, now),
        )

        val prior = AssistantInferenceContextPolicy.priorMessages(
            history,
            surface = AssistantInputSurface.GLASSES_VOICE,
            nowMs = now,
        )

        assertEquals(
            listOf("old-answer", "follow-up", "follow-up-answer"),
            prior.map { it.id },
        )
    }

    @Test
    fun phone_text_uses_same_last_three_message_rule() {
        val now = 500_000L
        val history = (0 until 10).map { index ->
            message(
                id = "m$index",
                role = if (index % 2 == 0) ChatRole.USER else ChatRole.ASSISTANT,
                createdAt = now - (10 - index) * 120_000L,
            )
        } + message("current", ChatRole.USER, now)

        val prior = AssistantInferenceContextPolicy.priorMessages(
            history,
            surface = AssistantInputSurface.PHONE_TEXT,
            nowMs = now,
        )

        assertEquals(listOf("m7", "m8", "m9"), prior.map { it.id })
    }

    private fun message(id: String, role: ChatRole, createdAt: Long): ChatMessage = ChatMessage(
        id = id,
        chatId = "thread",
        role = role,
        content = id,
        createdAt = createdAt,
    )
}

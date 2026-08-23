package com.ad_glasses.ai.orchestrator

import com.ad_glasses.shared.chat.ChatMessage
import com.ad_glasses.shared.chat.ChatRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantInferenceContextPolicyTest {
    @Test
    fun active_micro_session_keeps_only_the_last_four_prior_messages() {
        val now = 100_000L
        val history = (0 until 6).map { index ->
            message(
                id = "m$index",
                role = if (index % 2 == 0) ChatRole.USER else ChatRole.ASSISTANT,
                createdAt = now - (5 - index) * 2_000L,
            )
        } + message("current", ChatRole.USER, now)

        val prior = AssistantInferenceContextPolicy.priorMessages(history, nowMs = now)

        assertEquals(listOf("m2", "m3", "m4", "m5"), prior.map { it.id })
    }

    @Test
    fun user_inactivity_expires_model_context_without_touching_history() {
        val now = 100_000L
        val history = listOf(
            message("old-user", ChatRole.USER, now - 60_000L),
            message("old-answer", ChatRole.ASSISTANT, now - 55_000L),
            message("current", ChatRole.USER, now),
        )

        assertTrue(AssistantInferenceContextPolicy.priorMessages(history, nowMs = now).isEmpty())
        assertEquals(3, history.size)
    }

    @Test
    fun user_pause_inside_history_stops_context_at_that_turn_boundary() {
        val now = 100_000L
        val history = listOf(
            message("old", ChatRole.ASSISTANT, now - 80_000L),
            message("recent-user", ChatRole.USER, now - 10_000L),
            message("recent-answer", ChatRole.ASSISTANT, now - 5_000L),
            message("current", ChatRole.USER, now),
        )

        assertEquals(
            listOf("recent-user", "recent-answer"),
            AssistantInferenceContextPolicy.priorMessages(history, nowMs = now).map { it.id },
        )
    }

    @Test
    fun slow_assistant_response_does_not_expire_the_completed_exchange() {
        val now = 100_000L
        val history = listOf(
            message("previous-user", ChatRole.USER, now - 80_000L),
            message("slow-answer", ChatRole.ASSISTANT, now - 15_000L),
            message("current", ChatRole.USER, now),
        )

        assertEquals(
            listOf("previous-user", "slow-answer"),
            AssistantInferenceContextPolicy.priorMessages(history, nowMs = now).map { it.id },
        )
    }

    private fun message(id: String, role: ChatRole, createdAt: Long): ChatMessage = ChatMessage(
        id = id,
        chatId = "thread",
        role = role,
        content = id,
        createdAt = createdAt,
    )
}

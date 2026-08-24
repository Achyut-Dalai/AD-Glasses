package com.ad_glasses.ai.orchestrator

import com.ad_glasses.shared.chat.ChatMessage
import com.ad_glasses.shared.chat.ChatRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantInferenceContextPolicyTest {
    @Test
    fun active_voice_micro_session_keeps_only_the_last_three_prior_messages() {
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
    fun voice_user_inactivity_expires_model_context_without_touching_history() {
        val now = 100_000L
        val history = listOf(
            message("old-user", ChatRole.USER, now - 60_000L),
            message("old-answer", ChatRole.ASSISTANT, now - 55_000L),
            message("current", ChatRole.USER, now),
        )

        assertTrue(
            AssistantInferenceContextPolicy.priorMessages(
                history,
                surface = AssistantInputSurface.GLASSES_VOICE,
                nowMs = now,
            ).isEmpty(),
        )
        assertEquals(3, history.size)
    }

    @Test
    fun voice_user_pause_inside_history_stops_context_at_that_turn_boundary() {
        val now = 100_000L
        val history = listOf(
            message("old", ChatRole.ASSISTANT, now - 80_000L),
            message("recent-user", ChatRole.USER, now - 10_000L),
            message("recent-answer", ChatRole.ASSISTANT, now - 5_000L),
            message("current", ChatRole.USER, now),
        )

        assertEquals(
            listOf("recent-user", "recent-answer"),
            AssistantInferenceContextPolicy.priorMessages(
                history,
                surface = AssistantInputSurface.GLASSES_VOICE,
                nowMs = now,
            ).map { it.id },
        )
    }

    @Test
    fun slow_assistant_response_does_not_expire_the_completed_voice_exchange() {
        val now = 100_000L
        val history = listOf(
            message("previous-user", ChatRole.USER, now - 80_000L),
            message("slow-answer", ChatRole.ASSISTANT, now - 15_000L),
            message("current", ChatRole.USER, now),
        )

        assertEquals(
            listOf("previous-user", "slow-answer"),
            AssistantInferenceContextPolicy.priorMessages(
                history,
                surface = AssistantInputSurface.GLASSES_VOICE,
                nowMs = now,
            ).map { it.id },
        )
    }

    @Test
    fun phone_text_keeps_recent_thread_context_across_long_pause() {
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

        assertEquals(8, prior.size)
        assertEquals(listOf("m2", "m3", "m4", "m5", "m6", "m7", "m8", "m9"), prior.map { it.id })
    }

    private fun message(id: String, role: ChatRole, createdAt: Long): ChatMessage = ChatMessage(
        id = id,
        chatId = "thread",
        role = role,
        content = id,
        createdAt = createdAt,
    )
}

package com.ad_glasses.ai.orchestrator

import com.ad_glasses.shared.chat.ChatMessage
import com.ad_glasses.shared.chat.ChatRole
import org.junit.Assert.assertEquals
import org.junit.Test

class AssistantInferenceContextPolicyTest {
    @Test
    fun voice_keeps_only_last_three_prior_messages_during_follow_up_window() {
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
    fun long_pause_discards_spoken_inference_context_but_not_history() {
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

        assertEquals(emptyList<String>(), prior.map { it.id })
        assertEquals(5, history.size)
    }

    @Test
    fun spoken_context_is_kept_at_exact_follow_up_boundary() {
        val now = 100_000L
        val previousAt = now - AssistantInferenceContextPolicy.SPOKEN_FOLLOW_UP_TTL_MS
        val history = listOf(
            message("previous-user", ChatRole.USER, previousAt - 1_000L),
            message("previous-answer", ChatRole.ASSISTANT, previousAt),
            message("current", ChatRole.USER, now),
        )

        val prior = AssistantInferenceContextPolicy.priorMessages(
            history,
            surface = AssistantInputSurface.GLASSES_VOICE,
            nowMs = now,
        )

        assertEquals(listOf("previous-user", "previous-answer"), prior.map { it.id })
    }

    @Test
    fun phone_voice_uses_same_time_gated_follow_up_rule() {
        val now = 500_000L
        val history = listOf(
            message("previous-user", ChatRole.USER, 100_000L),
            message("previous-answer", ChatRole.ASSISTANT, 110_000L),
            message("current", ChatRole.USER, now),
        )

        val prior = AssistantInferenceContextPolicy.priorMessages(
            history,
            surface = AssistantInputSurface.PHONE_VOICE,
            nowMs = now,
        )

        assertEquals(emptyList<String>(), prior.map { it.id })
    }

    @Test
    fun phone_text_keeps_last_three_messages_even_after_long_gap() {
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

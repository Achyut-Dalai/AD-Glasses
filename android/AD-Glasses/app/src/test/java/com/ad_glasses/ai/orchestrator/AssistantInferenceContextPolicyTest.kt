package com.ad_glasses.ai.orchestrator

import com.ad_glasses.shared.chat.ChatMessage
import com.ad_glasses.shared.chat.ChatRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantInferenceContextPolicyTest {
    @Test
    fun short_conversation_keeps_more_than_three_prior_messages_with_same_bounded_budget() {
        val now = 100_000L
        val history = (0 until 8).map { index ->
            message(
                id = "m$index",
                role = if (index % 2 == 0) ChatRole.USER else ChatRole.ASSISTANT,
                createdAt = now - (7 - index) * 2_000L,
                content = if (index == 1) "The running total is 42." else "short-$index",
            )
        } + message("current", ChatRole.USER, now, content = "Add 34 to the previous total.")

        val prior = AssistantInferenceContextPolicy.priorMessages(
            history,
            surface = AssistantInputSurface.PHONE_TEXT,
            nowMs = now,
        )

        assertEquals((0 until 8).map { "m$it" }, prior.map { it.id })
        assertTrue(prior.any { it.content == "The running total is 42." })
    }

    @Test
    fun worst_case_context_remains_bounded_to_old_three_message_payload() {
        val now = 500_000L
        val history = (0 until 5).map { index ->
            message(
                id = "m$index",
                role = if (index % 2 == 0) ChatRole.USER else ChatRole.ASSISTANT,
                createdAt = now - (5 - index) * 2_000L,
                content = "x".repeat(AssistantInferenceContextPolicy.MAX_MESSAGE_CHARS),
            )
        } + message("current", ChatRole.USER, now)

        val prior = AssistantInferenceContextPolicy.priorMessages(history, nowMs = now)

        assertEquals(listOf("m2", "m3", "m4"), prior.map { it.id })
        assertTrue(
            prior.sumOf { it.content.take(AssistantInferenceContextPolicy.MAX_MESSAGE_CHARS).length } <=
                AssistantInferenceContextPolicy.MAX_PRIOR_CONTEXT_CHARS,
        )
    }

    @Test
    fun budget_boundary_never_skips_recent_message_to_resurrect_older_context() {
        val now = 600_000L
        val history = listOf(
            message("older-short", ChatRole.USER, now - 6_000L, "old".repeat(10)),
            message("boundary", ChatRole.ASSISTANT, now - 5_000L, "b".repeat(200)),
            message("recent-1", ChatRole.USER, now - 4_000L, "a".repeat(800)),
            message("recent-2", ChatRole.ASSISTANT, now - 3_000L, "b".repeat(900)),
            message("recent-3", ChatRole.USER, now - 2_000L, "c".repeat(900)),
            message("current", ChatRole.USER, now, "continue"),
        )

        val prior = AssistantInferenceContextPolicy.priorMessages(history, nowMs = now)

        assertEquals(listOf("recent-1", "recent-2", "recent-3"), prior.map { it.id })
        assertFalse(prior.any { it.id == "older-short" })
    }

    @Test
    fun long_pause_does_not_discard_bounded_history() {
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
            listOf("old-user", "old-answer", "follow-up", "follow-up-answer"),
            prior.map { it.id },
        )
    }

    @Test
    fun transient_failure_bubbles_never_reenter_model_context() {
        val now = 500_000L
        val transient = "The AI didn’t produce a final answer. Please try again."
        val history = listOf(
            message("u1", ChatRole.USER, now - 5_000L, "What is a computer?"),
            message("e1", ChatRole.ASSISTANT, now - 4_000L, transient),
            message("u2", ChatRole.USER, now - 3_000L, "Try again"),
            message("a2", ChatRole.ASSISTANT, now - 2_000L, "A computer processes data using instructions."),
            message("current", ChatRole.USER, now, "Give an example"),
        )

        val prior = AssistantInferenceContextPolicy.priorMessages(history, nowMs = now)

        assertFalse(prior.any { it.id == "e1" })
        assertEquals(listOf("u1", "u2", "a2"), prior.map { it.id })
        assertTrue(AssistantInferenceContextPolicy.isTransientAssistantFailure(transient))
    }

    private fun message(
        id: String,
        role: ChatRole,
        createdAt: Long,
        content: String = id,
    ): ChatMessage = ChatMessage(
        id = id,
        chatId = "thread",
        role = role,
        content = content,
        createdAt = createdAt,
    )
}

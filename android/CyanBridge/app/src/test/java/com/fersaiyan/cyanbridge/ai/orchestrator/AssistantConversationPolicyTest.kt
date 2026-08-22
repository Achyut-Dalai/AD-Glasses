package com.fersaiyan.cyanbridge.ai.orchestrator

import com.fersaiyan.cyanbridge.shared.chat.ChatThread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AssistantConversationPolicyTest {

    @Test
    fun conversationControls_areRecognizedWithoutPersistableFreeTextFalsePositives() {
        assertEquals(
            AssistantConversationCommand.START_FRESH,
            AssistantConversationPolicy.parseCommand("New topic!"),
        )
        assertEquals(
            AssistantConversationCommand.START_FRESH,
            AssistantConversationPolicy.parseCommand("Start a new conversation."),
        )
        assertEquals(
            AssistantConversationCommand.FORGET_CURRENT,
            AssistantConversationPolicy.parseCommand("FORGET this conversation"),
        )

        assertNull(AssistantConversationPolicy.parseCommand("Suggest a new topic"))
        assertNull(AssistantConversationPolicy.parseCommand("What does forget this conversation do?"))
    }

    @Test
    fun expiry_isLimitedToAdManagedThreadsAndStartsAtTheSevenDayBoundary() {
        val now = 8L * DAY
        val legacyAd = thread("legacy-ad", AssistantConversationPolicy.THREAD_TITLE, now - 7L * DAY)
        val explicitlyManaged = thread("managed", "Renamed by user", now - 7L * DAY - HOUR)
        val unrelatedOldChat = thread("unrelated", "Personal note", now - 100L * HOUR)
        val recentAd = thread("recent-ad", AssistantConversationPolicy.THREAD_TITLE, now - 7L * DAY + HOUR)

        val expired = AssistantConversationPolicy.expiredThreadIds(
            threads = listOf(legacyAd, explicitlyManaged, unrelatedOldChat, recentAd),
            managedThreadIds = setOf(explicitlyManaged.id),
            nowMs = now,
        )

        assertEquals(setOf("legacy-ad", "managed"), expired)
    }

    @Test
    fun futureTimestamp_isNotTreatedAsExpired() {
        val future = thread(
            id = "future",
            title = AssistantConversationPolicy.THREAD_TITLE,
            updatedAt = 2L * HOUR,
        )

        val expired = AssistantConversationPolicy.expiredThreadIds(
            threads = listOf(future),
            managedThreadIds = emptySet(),
            nowMs = HOUR,
        )

        assertEquals(emptySet<String>(), expired)
    }

    private fun thread(id: String, title: String, updatedAt: Long) = ChatThread(
        id = id,
        title = title,
        createdAt = updatedAt,
        updatedAt = updatedAt,
    )

    private companion object {
        const val HOUR = 60L * 60L * 1_000L
        const val DAY = 24L * HOUR
    }
}

package com.ad_glasses.chat

import com.ad_glasses.shared.chat.ChatRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatStoreFallbackTest {

    @Test
    fun chatsRemainUsableWhenRoomRepositoryIsUnavailable() {
        ChatStore.clearAll()

        val thread = ChatStore.createThread("Fallback chat", nowMs = 1_000L)
        ChatStore.addMessage(
            chatId = thread.id,
            role = ChatRole.USER,
            content = "hello",
            nowMs = 2_000L,
        )

        assertEquals(thread.id, ChatStore.getThread(thread.id)?.id)
        assertEquals(1, ChatStore.listMessages(thread.id).size)
        assertTrue(ChatStore.listNonEmptyThreads().any { it.id == thread.id })

        ChatStore.clearAll()

        assertTrue(ChatStore.listThreads().isEmpty())
    }
}

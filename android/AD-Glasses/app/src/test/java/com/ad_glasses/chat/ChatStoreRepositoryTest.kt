package com.ad_glasses.chat

import com.ad_glasses.shared.chat.ChatRole
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatStoreRepositoryTest {
    private val repository = ChatStoreRepository()

    @After
    fun tearDown() = runBlocking {
        repository.clearAll()
    }

    @Test
    fun repositoryPersistsThreadAndMessagesThroughTheSharedContract() = runBlocking {
        val thread = repository.createThread(title = null, nowMs = 1_000L)
        val message = repository.addMessage(
            chatId = thread.id,
            role = ChatRole.USER,
            content = "Portable chat repository",
            nowMs = 2_000L,
        )

        val storedThread = repository.getThread(thread.id)
        val storedMessages = repository.listMessages(thread.id)

        assertEquals(message, storedMessages.single())
        assertEquals("Portable chat repository", storedThread?.title)
        assertEquals(2_000L, storedThread?.updatedAt)
        assertTrue(repository.listNonEmptyThreads().any { it.id == thread.id })
    }
}

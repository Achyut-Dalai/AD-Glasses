package com.achyut.adglasses.shared.chat

enum class ChatRole {
    USER,
    ASSISTANT,
}

data class ChatMessage(
    val id: String,
    val chatId: String,
    val role: ChatRole,
    val content: String,
    val createdAt: Long,
)

data class ChatThread(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
)

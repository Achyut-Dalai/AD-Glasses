package com.adglasses.app.core.assistant

import android.content.Context
import com.adglasses.app.core.model.ChatMessage
import com.adglasses.app.core.model.MessageRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class ConversationStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("ad_conversation", Context.MODE_PRIVATE)
    private val _messages = MutableStateFlow(load())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    fun addUser(text: String) = add(ChatMessage(role = MessageRole.User, text = text.trim()))
    fun addAssistant(text: String) = add(ChatMessage(role = MessageRole.Assistant, text = text.trim()))

    fun clear() {
        _messages.value = emptyList()
        persist()
    }

    private fun add(message: ChatMessage) {
        if (message.text.isBlank()) return
        _messages.value = _messages.value + message
        persist()
    }

    private fun persist() {
        val array = JSONArray()
        _messages.value.forEach { message ->
            array.put(JSONObject().apply {
                put("id", message.id)
                put("role", message.role.name)
                put("text", message.text)
                put("createdAt", message.createdAtEpochMs)
            })
        }
        prefs.edit().putString("messages", array.toString()).apply()
    }

    private fun load(): List<ChatMessage> = runCatching {
        val source = prefs.getString("messages", null) ?: return@runCatching emptyList()
        val array = JSONArray(source)
        buildList {
            repeat(array.length()) { index ->
                val item = array.getJSONObject(index)
                add(
                    ChatMessage(
                        id = item.getString("id"),
                        role = MessageRole.valueOf(item.getString("role")),
                        text = item.getString("text"),
                        createdAtEpochMs = item.getLong("createdAt"),
                    )
                )
            }
        }
    }.getOrDefault(emptyList())
}

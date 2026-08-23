package com.ad_glasses.ai.orchestrator

import com.ad_glasses.shared.chat.ChatMessage

/** Web use is opt-in: a visible toggle or an explicit search/browse phrase. */
object AssistantWebPolicy {
    fun shouldUseWeb(
        text: String,
        requested: Boolean? = null,
        history: List<ChatMessage> = emptyList(),
    ): Boolean = requested == true || EXPLICIT_WEB.containsMatchIn(text.trim())

    private val EXPLICIT_WEB = Regex(
        pattern = "\\b(search (?:the )?web|browse (?:the )?web|search online|browse online|use web search|" +
            "look up .{0,80} (?:online|on the web|on the internet)|search the internet)\\b",
        option = RegexOption.IGNORE_CASE,
    )
}

package com.ad_glasses.ai.router

object AssistantSpeechPolicy {
    private const val DEFAULT_CLARIFICATION = "Please say exactly what you want me to do."

    fun clarification(raw: String?): String {
        val clean = raw.orEmpty()
            .replace(Regex("```[\\s\\S]*?```"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (clean.isBlank() || clean.length > 160) return DEFAULT_CLARIFICATION
        if (SUSPICIOUS_TOOL_TEXT.containsMatchIn(clean)) return DEFAULT_CLARIFICATION
        return clean
    }

    private val SUSPICIOUS_TOOL_TEXT = Regex(
        "(?:\\{.*\\}|click_coord|click_text|type_text|tool_call|reasoning|bounds:|center:|action\\s*:)",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
}

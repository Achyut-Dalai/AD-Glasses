package com.ad_glasses.ai.orchestrator

/** Keeps glasses playback useful while preserving the complete answer in Chats. */
object AssistantSpokenResponsePolicy {
    private const val MAX_DIRECT_CHARS = 260
    private const val SUMMARY_TARGET_CHARS = 200
    private const val CHAT_POINTER = " More detail is in Chats."

    fun forGlasses(richText: String): String {
        val normalized = richText
            .replace(Regex("```[A-Za-z0-9_-]*"), "")
            .replace("```", "")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (normalized.isBlank()) return "I didn’t get a usable answer."
        if (normalized.length <= MAX_DIRECT_CHARS) return normalized

        val candidate = normalized.take(SUMMARY_TARGET_CHARS + 1)
        val sentenceEnd = candidate.indices
            .filter { index -> candidate[index] in charArrayOf('.', '!', '?') }
            .lastOrNull { it >= 70 }
        val cutAt = sentenceEnd?.plus(1)
            ?: candidate.take(SUMMARY_TARGET_CHARS).lastIndexOf(' ').takeIf { it >= 70 }
            ?: SUMMARY_TARGET_CHARS
        return candidate.take(cutAt).trimEnd(' ', ',', ';', ':', '-', '–', '—') + CHAT_POINTER
    }
}

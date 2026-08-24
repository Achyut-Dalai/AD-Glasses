package com.ad_glasses.ai.orchestrator

/** Keeps glasses playback natural, plain-text, and bounded while preserving richText in Chats. */
object AssistantSpokenResponsePolicy {
    private const val MAX_SPOKEN_WORDS = 50
    private const val MAX_CONTENT_WORDS_WHEN_TRUNCATED = 45
    private const val MAX_SPOKEN_SENTENCES = 3
    private const val CHAT_POINTER = "More detail is in Chats."

    /**
     * Convert provider text into speech-safe plain text. The model is instructed not to emit
     * Markdown, but this remains a hard code-layer guard because Android TTS may literally read
     * punctuation such as repeated asterisks, hashes, underscores, and list markers aloud.
     */
    fun normalizeForSpeech(richText: String): String {
        var text = richText

        // Preserve the useful text while dropping visual-only Markdown wrappers.
        text = text
            .replace(Regex("(?is)```[A-Za-z0-9_+.-]*\\s*(.*?)```"), "$1")
            .replace(Regex("`([^`]*)`"), "$1")
            .replace(Regex("!\\[([^\\]]*)]\\([^)]*\\)"), "$1")
            .replace(Regex("\\[([^\\]]+)]\\([^)]*\\)"), "$1")
            .replace(Regex("(?m)^\\s{0,3}#{1,6}\\s*"), "")
            .replace(Regex("(?m)^\\s*>+\\s?"), "")
            .replace(Regex("(?m)^\\s*[-*+]\\s+"), "")
            .replace(Regex("(?m)^\\s*\\d+[.)]\\s+"), "")
            .replace(Regex("(?m)^\\s*(?:[-*_]\\s*){3,}$"), "")

        // Keep simple spoken math understandable before removing remaining emphasis markers.
        text = text
            .replace(Regex("(?<=\\d)\\s*\\*\\s*(?=\\d)"), " times ")
            .replace(Regex("[*~]+"), "")
            .replace('_', ' ')
            .replace(Regex("(?m)\\|+"), ", ")
            .replace(Regex("\\[(?:\\d{1,3}|[A-Za-z]{1,4}\\d{0,3})]"), "")
            .replace(Regex("https?://\\S+", RegexOption.IGNORE_CASE), "link")
            .replace(Regex("<[^>]+>"), "")
            .replace("&amp;", " and ", ignoreCase = true)
            .replace("&lt;", " less than ", ignoreCase = true)
            .replace("&gt;", " greater than ", ignoreCase = true)
            .replace(Regex("\\s+"), " ")
            .trim()

        return text
    }

    /**
     * Safe prefix for live TTS. Keep streamed speech to the first 45 content words and three
     * sentences so finalization can still append either the last few words of a compliant answer or
     * the five-word Chats pointer without ever crossing the 50-word wearable ceiling.
     */
    fun streamingPrefixForGlasses(richText: String): String {
        val normalized = normalizeForSpeech(richText)
        if (normalized.isBlank()) return ""
        return takeWords(
            takeSentences(normalized, MAX_SPOKEN_SENTENCES),
            MAX_CONTENT_WORDS_WHEN_TRUNCATED,
        )
    }

    /**
     * Provider-side prompting and max-output tokens are the primary length controls. This local
     * limit is the final wearable guard if a provider ignores them: at most three sentences and
     * fifty spoken words, including the Chats pointer when truncation was necessary.
     */
    fun forGlasses(richText: String): String {
        val normalized = normalizeForSpeech(richText)
        if (normalized.isBlank()) return "I didn’t get a usable answer."

        val sentenceLimited = takeSentences(normalized, MAX_SPOKEN_SENTENCES)
        val originalWordCount = wordCount(normalized)
        val sentenceWasTruncated = sentenceLimited.length < normalized.length
        val needsWordTruncation = wordCount(sentenceLimited) > MAX_SPOKEN_WORDS

        if (!sentenceWasTruncated && !needsWordTruncation && originalWordCount <= MAX_SPOKEN_WORDS) {
            return normalized
        }

        val content = takeWords(sentenceLimited, MAX_CONTENT_WORDS_WHEN_TRUNCATED)
            .trimEnd(' ', ',', ';', ':', '-', '–', '—')
        return if (content.isBlank()) {
            CHAT_POINTER
        } else {
            "$content $CHAT_POINTER"
        }
    }

    private fun takeSentences(text: String, maxSentences: Int): String {
        var sentences = 0
        for (index in text.indices) {
            if (text[index] !in charArrayOf('.', '!', '?')) continue
            val next = index + 1
            if (next < text.length && !text[next].isWhitespace()) continue
            sentences++
            if (sentences >= maxSentences && next < text.length) {
                return text.substring(0, next).trim()
            }
        }
        return text
    }

    private fun takeWords(text: String, maxWords: Int): String {
        if (wordCount(text) <= maxWords) return text.trim()
        return text.trim()
            .split(Regex("\\s+"))
            .take(maxWords)
            .joinToString(" ")
    }

    private fun wordCount(text: String): Int = text
        .trim()
        .takeIf { it.isNotBlank() }
        ?.split(Regex("\\s+"))
        ?.size
        ?: 0
}

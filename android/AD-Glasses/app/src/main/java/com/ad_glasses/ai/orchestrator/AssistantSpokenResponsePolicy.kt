package com.ad_glasses.ai.orchestrator

/** Keeps glasses playback useful while preserving the complete answer in Chats. */
object AssistantSpokenResponsePolicy {
    private const val MAX_DIRECT_WORDS = 50
    private const val MAX_DIRECT_SENTENCES = 3
    private const val TRUNCATED_BODY_WORDS = 42
    private const val TRUNCATED_BODY_SENTENCES = 2
    private const val CHAT_POINTER = "More detail is in Chats."

    private val wordPattern = Regex("\\S+")
    private val sentenceEndPattern = Regex("[.!?](?=\\s|$)")

    /**
     * Convert rich model text into speech-safe plain text.
     *
     * This deliberately lives below prompting: providers can ignore a no-Markdown instruction, and
     * Android TTS may literally pronounce formatting runs such as "asterisk asterisk". The scanner
     * removes visual-only Markdown while preserving the words themselves. It is also prefix-stable
     * enough for cumulative streaming text, so a closing Markdown marker cannot rewrite text that
     * has already been spoken.
     */
    fun normalizeForSpeech(richText: String): String {
        if (richText.isBlank()) return ""

        val spoken = StringBuilder(richText.length)
        var index = 0
        var lineStart = true

        while (index < richText.length) {
            val char = richText[index]

            when {
                char == '\r' -> {
                    index++
                }

                char == '\n' -> {
                    appendSentenceBreak(spoken)
                    lineStart = true
                    index++
                }

                lineStart && char.isWhitespace() -> {
                    index++
                }

                lineStart && char == '#' -> {
                    while (index < richText.length && richText[index] == '#') index++
                    while (index < richText.length && richText[index] != '\n' && richText[index].isWhitespace()) {
                        index++
                    }
                }

                lineStart && isListMarker(richText, index) -> {
                    appendSentenceBreak(spoken)
                    index++
                    while (index < richText.length && richText[index] != '\n' && richText[index].isWhitespace()) {
                        index++
                    }
                }

                richText.startsWith("```", index) -> {
                    index += 3
                    if (lineStart) {
                        // Opening fences commonly carry a language label (```kotlin). Do not make
                        // TTS say the fence language; wait until the code/text body begins.
                        while (index < richText.length && richText[index] != '\n') index++
                    }
                }

                startsWithUrl(richText, index) -> {
                    appendToken(spoken, "link")
                    while (index < richText.length && !richText[index].isWhitespace()) index++
                    lineStart = false
                }

                char == '!' && index + 1 < richText.length && richText[index + 1] == '[' -> {
                    // Markdown image syntax: speak the alt text, not the leading exclamation mark.
                    index++
                }

                char == '[' -> {
                    index++
                }

                char == ']' && index + 1 < richText.length && richText[index + 1] == '(' -> {
                    // The link label has already been appended. Drop the visual URL target, even if
                    // the target is still arriving in a streamed completion.
                    index += 2
                    while (index < richText.length && richText[index] != ')') index++
                    if (index < richText.length) index++
                }

                char == ']' -> {
                    index++
                }

                char == '*' -> {
                    val runEnd = runEnd(richText, index, '*')
                    if (runEnd == index + 1 && looksLikeMathAsterisk(richText, index)) {
                        appendToken(spoken, "times")
                        lineStart = false
                    }
                    index = runEnd
                }

                char == '_' -> {
                    val runEnd = runEnd(richText, index, '_')
                    if (runEnd == index + 1 && isBetweenAlphaNumeric(richText, index)) {
                        appendSpace(spoken)
                    }
                    index = runEnd
                }

                char == '`' || char == '~' || char == '#' -> {
                    index = runEnd(richText, index, char)
                }

                char == '|' -> {
                    trimTrailingWhitespace(spoken)
                    if (spoken.isNotEmpty() && spoken.last() != ',') spoken.append(',')
                    appendSpace(spoken)
                    lineStart = false
                    index++
                }

                char == '\\' && index + 1 < richText.length && richText[index + 1] in MARKDOWN_ESCAPABLE -> {
                    index++
                }

                char.isWhitespace() -> {
                    appendSpace(spoken)
                    index++
                }

                else -> {
                    if (char in SPEECH_PUNCTUATION) trimTrailingWhitespace(spoken)
                    spoken.append(char)
                    lineStart = false
                    index++
                }
            }
        }

        trimTrailingWhitespace(spoken)
        return spoken.toString().trim()
    }

    /** Hard speech guardrail: never make a wearable user listen to an accidental essay. */
    fun forGlasses(richText: String): String {
        val normalized = normalizeForSpeech(richText)
        if (normalized.isBlank()) return "I didn’t get a usable answer."

        if (wordCount(normalized) <= MAX_DIRECT_WORDS && sentenceCount(normalized) <= MAX_DIRECT_SENTENCES) {
            return normalized
        }

        val wordCut = endAfterWord(normalized, TRUNCATED_BODY_WORDS)
        val secondSentenceCut = sentenceEndPattern.findAll(normalized)
            .map { match -> match.range.last + 1 }
            .take(TRUNCATED_BODY_SENTENCES)
            .toList()
            .getOrNull(TRUNCATED_BODY_SENTENCES - 1)
        val cutAt = listOfNotNull(wordCut, secondSentenceCut).minOrNull() ?: normalized.length

        var body = normalized.take(cutAt)
            .trimEnd(' ', ',', ';', ':', '-', '–', '—')
        if (body.isBlank()) return CHAT_POINTER
        if (body.last() !in charArrayOf('.', '!', '?')) body += "."
        return "$body $CHAT_POINTER"
    }

    private fun isListMarker(text: String, index: Int): Boolean {
        val char = text[index]
        if (char !in charArrayOf('-', '+', '*', '•', '>')) return false
        return index + 1 < text.length && text[index + 1].isWhitespace()
    }

    private fun startsWithUrl(text: String, index: Int): Boolean =
        text.regionMatches(index, "https://", 0, 8, ignoreCase = true) ||
            text.regionMatches(index, "http://", 0, 7, ignoreCase = true) ||
            text.regionMatches(index, "www.", 0, 4, ignoreCase = true)

    private fun runEnd(text: String, start: Int, char: Char): Int {
        var index = start
        while (index < text.length && text[index] == char) index++
        return index
    }

    private fun looksLikeMathAsterisk(text: String, index: Int): Boolean {
        val previousIndex = previousNonWhitespaceIndex(text, index - 1) ?: return false
        val nextIndex = nextNonWhitespaceIndex(text, index + 1) ?: return false
        val previous = text[previousIndex]
        val next = text[nextIndex]
        if (!previous.isLetterOrDigit() || !next.isLetterOrDigit()) return false

        val spacedOperator = index > 0 && index + 1 < text.length &&
            text[index - 1].isWhitespace() && text[index + 1].isWhitespace()
        return spacedOperator || (previous.isDigit() && next.isDigit())
    }

    private fun isBetweenAlphaNumeric(text: String, index: Int): Boolean =
        index > 0 && index + 1 < text.length &&
            text[index - 1].isLetterOrDigit() && text[index + 1].isLetterOrDigit()

    private fun previousNonWhitespaceIndex(text: String, start: Int): Int? {
        var index = start
        while (index >= 0) {
            if (!text[index].isWhitespace()) return index
            index--
        }
        return null
    }

    private fun nextNonWhitespaceIndex(text: String, start: Int): Int? {
        var index = start
        while (index < text.length) {
            if (!text[index].isWhitespace()) return index
            index++
        }
        return null
    }

    private fun appendToken(target: StringBuilder, token: String) {
        appendSpace(target)
        target.append(token)
        appendSpace(target)
    }

    private fun appendSpace(target: StringBuilder) {
        if (target.isNotEmpty() && !target.last().isWhitespace()) target.append(' ')
    }

    private fun appendSentenceBreak(target: StringBuilder) {
        trimTrailingWhitespace(target)
        if (target.isEmpty()) return
        while (target.isNotEmpty() && target.last() in TRAILING_BREAK_PUNCTUATION) {
            target.deleteCharAt(target.lastIndex)
        }
        if (target.isNotEmpty() && target.last() !in charArrayOf('.', '!', '?')) target.append('.')
        appendSpace(target)
    }

    private fun trimTrailingWhitespace(target: StringBuilder) {
        while (target.isNotEmpty() && target.last().isWhitespace()) {
            target.deleteCharAt(target.lastIndex)
        }
    }

    private fun wordCount(text: String): Int = wordPattern.findAll(text).count()

    private fun sentenceCount(text: String): Int {
        if (text.isBlank()) return 0
        val matches = sentenceEndPattern.findAll(text).toList()
        if (matches.isEmpty()) return 1
        val trimmedLastIndex = text.trimEnd().lastIndex
        val hasTrailingSentenceWithoutPunctuation = matches.last().range.last < trimmedLastIndex
        return matches.size + if (hasTrailingSentenceWithoutPunctuation) 1 else 0
    }

    private fun endAfterWord(text: String, wordNumber: Int): Int? = wordPattern
        .findAll(text)
        .drop(wordNumber - 1)
        .firstOrNull()
        ?.range
        ?.last
        ?.plus(1)

    private val MARKDOWN_ESCAPABLE = setOf('*', '_', '#', '`', '~', '[', ']', '(', ')')
    private val SPEECH_PUNCTUATION = setOf(',', '.', ';', ':', '!', '?')
    private val TRAILING_BREAK_PUNCTUATION = setOf(',', ';', ':', '-', '–', '—')
}

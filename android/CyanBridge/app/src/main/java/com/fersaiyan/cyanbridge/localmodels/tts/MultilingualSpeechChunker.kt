package com.fersaiyan.cyanbridge.localmodels.tts

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Multilingual, code-point-aware, streaming text segmenter for Text-To-Speech.
 * Segments incoming streaming model tokens into natural spoken phrases across languages
 * (English, Portuguese, German, French, Italian, Russian, Chinese, etc.) without losing
 * or duplicating text, splitting decimals, or truncating valid short answers.
 */
class MultilingualSpeechChunker(
    private val config: SpeechChunkingConfig = SpeechChunkingConfig(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    private val onChunkReady: (String) -> Unit,
) {
    private val buffer = StringBuilder()
    private val fullAccumulatedText = StringBuilder()

    private var isFirstChunk = true
    private var candidateJob: Job? = null
    private var idleJob: Job? = null
    private var activeSessionId: Long = 0L

    @Synchronized
    fun startSession(sessionId: Long) {
        resetInternal()
        this.activeSessionId = sessionId
    }

    /**
     * Appends a newly generated text delta or full accumulated update.
     * Automatically extracts the newly added suffix if the callback passes full text.
     */
    @Synchronized
    fun append(rawInput: String, sessionId: Long = activeSessionId) {
        if (sessionId != activeSessionId || rawInput.isEmpty()) return

        val delta = extractNewTextDelta(rawInput)
        if (delta.isEmpty()) return

        buffer.append(delta)
        rescheduleIdleTimer()

        evaluateBuffer(forceFlush = false)
    }

    /**
     * Extracts newly added suffix if input is a cumulative text string.
     */
    private fun extractNewTextDelta(input: String): String {
        val currentAcc = fullAccumulatedText.toString()
        return if (input.startsWith(currentAcc) && input.length >= currentAcc.length) {
            val delta = input.substring(currentAcc.length)
            fullAccumulatedText.append(delta)
            delta
        } else {
            fullAccumulatedText.append(input)
            input
        }
    }

    @Synchronized
    fun finish(sessionId: Long = activeSessionId) {
        if (sessionId != activeSessionId) return
        cancelTimers()
        flushRemaining()
    }

    @Synchronized
    fun reset() {
        resetInternal()
    }

    private fun resetInternal() {
        cancelTimers()
        buffer.clear()
        fullAccumulatedText.clear()
        isFirstChunk = true
    }

    private fun cancelTimers() {
        candidateJob?.cancel()
        candidateJob = null
        idleJob?.cancel()
        idleJob = null
    }

    private fun rescheduleIdleTimer() {
        idleJob?.cancel()
        val currentSession = activeSessionId
        val idleMs = if (isFirstChunk) config.firstChunkIdleFlushMs else config.normalChunkIdleFlushMs

        idleJob = scope.launch {
            delay(idleMs)
            synchronized(this@MultilingualSpeechChunker) {
                if (activeSessionId == currentSession && buffer.isNotEmpty()) {
                    evaluateBuffer(forceFlush = true)
                }
            }
        }
    }

    private fun evaluateBuffer(forceFlush: Boolean) {
        val text = buffer.toString()
        if (text.isBlank()) return

        val codePoints = codePointCount(text)
        val minCodePoints = if (isFirstChunk) config.firstChunkMinCodePoints else config.normalChunkMinCodePoints

        // 1. Hard maximum length limit - force split
        if (codePoints >= config.hardChunkMaxCodePoints) {
            val splitIndex = findBestSplitIndex(text, config.preferredChunkMaxCodePoints)
            emitChunk(text.substring(0, splitIndex))
            buffer.delete(0, splitIndex)
            rescheduleIdleTimer()
            return
        }

        // 2. Strong boundary check
        val strongMatch = findStrongBoundary(text)
        if (strongMatch != null) {
            if (isConfirmedBoundary(text, strongMatch)) {
                emitChunk(text.substring(0, strongMatch.endIndex))
                buffer.delete(0, strongMatch.endIndex)
                rescheduleIdleTimer()
                return
            } else if (!forceFlush) {
                // Ambiguous boundary (e.g. potential decimal or abbreviation). Launch candidate delay timer.
                scheduleCandidateBoundaryCheck(strongMatch.endIndex)
                return
            }
        }

        // 3. Soft boundary check (comma, semicolon, dash) if chunk is preferred length
        if (codePoints >= config.preferredChunkMaxCodePoints) {
            val softMatch = findSoftBoundary(text)
            if (softMatch != null) {
                emitChunk(text.substring(0, softMatch.endIndex))
                buffer.delete(0, softMatch.endIndex)
                rescheduleIdleTimer()
                return
            }
        }

        // 4. Force flush on idle timeout if minimum length or short complete phrase is present
        if (forceFlush && codePoints >= minCodePoints) {
            emitChunk(text)
            buffer.clear()
        }
    }

    private fun scheduleCandidateBoundaryCheck(endIndex: Int) {
        if (candidateJob?.isActive == true) return
        val currentSession = activeSessionId

        candidateJob = scope.launch {
            delay(config.candidateBoundaryDelayMs)
            synchronized(this@MultilingualSpeechChunker) {
                if (activeSessionId == currentSession && buffer.length >= endIndex) {
                    val currentText = buffer.toString()
                    emitChunk(currentText.substring(0, endIndex))
                    buffer.delete(0, endIndex)
                    rescheduleIdleTimer()
                }
            }
        }
    }

    private fun emitChunk(chunkText: String) {
        val trimmed = chunkText.trim()
        if (trimmed.isNotEmpty()) {
            isFirstChunk = false
            onChunkReady(trimmed)
        }
    }

    private fun flushRemaining() {
        val remaining = buffer.toString().trim()
        if (remaining.isNotEmpty()) {
            emitChunk(remaining)
            buffer.clear()
        }
    }

    data class BoundaryMatch(val endIndex: Int, val char: Char)

    private fun findStrongBoundary(text: String): BoundaryMatch? {
        val len = text.length
        for (i in 0 until len) {
            val c = text[i]
            if (isStrongPunctuation(c)) {
                // Include trailing closing marks like quotes, brackets, parens: ." ?” !” 。》 !)
                var end = i + 1
                while (end < len && isClosingPunctuation(text[end])) {
                    end++
                }
                return BoundaryMatch(endIndex = end, char = c)
            }
        }
        return null
    }

    private fun findSoftBoundary(text: String): BoundaryMatch? {
        val len = text.length
        for (i in len - 1 downTo 0) {
            val c = text[i]
            if (isSoftPunctuation(c)) {
                var end = i + 1
                while (end < len && isClosingPunctuation(text[end])) {
                    end++
                }
                return BoundaryMatch(endIndex = end, char = c)
            }
        }
        return null
    }

    private fun isConfirmedBoundary(text: String, match: BoundaryMatch): Boolean {
        val idx = match.endIndex
        if (idx >= text.length) return true // At current end of stream

        val char = match.char
        // CJK punctuation (! ? 。 ！） and newlines are definitive boundaries
        if (char == '。' || char == '！' || char == '？' || char == '\n' || char == '\r') {
            return true
        }

        // Period guards
        if (char == '.') {
            // Decimal check: e.g. "3.14" or "3,14"
            if (idx > 1 && idx < text.length) {
                val prev = text[idx - 2]
                val next = text[idx]
                if (prev.isDigit() && next.isDigit()) return false
            }

            // Version number or domain check: "2.1.0", "example.com"
            val prefixWord = getWordBeforeIndex(text, idx - 1)
            if (isAbbreviation(prefixWord) || isVersionOrDomain(text, idx)) {
                return false
            }
        }

        // Check if followed by whitespace or uppercase/new sentence
        val nextChar = text[idx]
        return nextChar.isWhitespace() || nextChar.isUpperCase() || !nextChar.isLetterOrDigit()
    }

    private fun findBestSplitIndex(text: String, preferredMax: Int): Int {
        val limit = preferredMax.coerceAtMost(text.length)
        // 1. Look for soft boundary
        for (i in limit downTo 1) {
            if (isSoftPunctuation(text[i - 1]) || isStrongPunctuation(text[i - 1])) {
                return i
            }
        }
        // 2. Look for whitespace boundary
        for (i in limit downTo 1) {
            if (text[i - 1].isWhitespace()) {
                return i
            }
        }
        // 3. Fallback: code point boundary
        return limit
    }

    private fun getWordBeforeIndex(text: String, periodIdx: Int): String {
        var start = periodIdx - 1
        while (start >= 0 && (text[start].isLetterOrDigit() || text[start] == '_')) {
            start--
        }
        return text.substring(start + 1, periodIdx).lowercase(Locale.ROOT)
    }

    private fun isStrongPunctuation(c: Char): Boolean = when (c) {
        '.', '!', '?', '。', '！', '？', '\n', '\r' -> true
        else -> false
    }

    private fun isSoftPunctuation(c: Char): Boolean = when (c) {
        ',', ';', ':', '，', '；', '：', '—', '–' -> true
        else -> false
    }

    private fun isClosingPunctuation(c: Char): Boolean = when (c) {
        '"', '\'', ')', ']', '}', '》', '」', '』', '”', '’' -> true
        else -> false
    }

    private fun isAbbreviation(word: String): Boolean = when (word) {
        "dr", "mr", "mrs", "ms", "prof", "sr", "jr", "vs", "etc", "st", "approx",
        "dott", "prof", "nº", "г", "ул", "str" -> true
        else -> false
    }

    private fun isVersionOrDomain(text: String, idx: Int): Boolean {
        if (idx >= text.length) return false
        val snippet = text.substring(idx).take(10).lowercase(Locale.ROOT)
        return snippet.startsWith("com") || snippet.startsWith("org") ||
            snippet.startsWith("net") || snippet.startsWith("bin") ||
            snippet.startsWith("io") || snippet.startsWith("ai")
    }

    private fun codePointCount(str: String): Int {
        return str.codePointCount(0, str.length)
    }
}

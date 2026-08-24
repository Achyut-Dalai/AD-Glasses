package com.ad_glasses.ai.orchestrator

/**
 * Turns a streamed provider completion into safe, natural TTS segments.
 *
 * Raw provider deltas are never spoken directly. We repeatedly sanitize the cumulative completion,
 * wait for a stable sentence/phrase boundary, and only expose text that is a prefix of the final
 * glasses speech target. This keeps leading reasoning blocks and prompt echoes out of TTS while
 * still allowing the first useful sentence to start before generation is finished.
 */
class AssistantStreamingSpeechBuffer(
    private val streamingPrefixBudgetChars: Int = DEFAULT_STREAMING_PREFIX_BUDGET_CHARS,
    private val firstForcedSplitChars: Int = DEFAULT_FIRST_FORCED_SPLIT_CHARS,
    private val firstMinForcedSplitChars: Int = DEFAULT_FIRST_MIN_FORCED_SPLIT_CHARS,
    private val forcedSplitChars: Int = DEFAULT_FORCED_SPLIT_CHARS,
    private val minForcedSplitChars: Int = DEFAULT_MIN_FORCED_SPLIT_CHARS,
) {
    private val raw = StringBuilder()
    private var consumedPrefix: String = ""
    private var finished = false

    /** Accept one provider text delta and return any newly-safe speech segments. */
    fun accept(delta: String): List<String> {
        if (finished || delta.isEmpty()) return emptyList()
        raw.append(delta)

        val clean = AssistantCompletionSanitizer.cleanForStreaming(raw.toString())
        if (clean.isBlank()) return emptyList()
        val streamingTarget = AssistantSpokenResponsePolicy.streamingPrefixForGlasses(clean)
        if (streamingTarget.isBlank()) return emptyList()

        return drainStreamingPrefix(streamingTarget)
    }

    /**
     * Finalize with the provider's complete raw answer and return whatever remains to be spoken.
     * The final tail is constrained by the normal glasses speech policy.
     */
    fun finish(finalRaw: String): List<String> {
        if (finished) return emptyList()
        finished = true

        val clean = AssistantCompletionSanitizer.clean(finalRaw)
        if (clean.isBlank()) return emptyList()
        val finalTarget = AssistantSpokenResponsePolicy.forGlasses(clean)
        if (finalTarget.isBlank()) return emptyList()

        if (consumedPrefix.isNotEmpty() && !finalTarget.startsWith(consumedPrefix)) {
            // The sanitized answer changed underneath text that was already spoken. Never guess at
            // a continuation that could contradict the audible prefix.
            return emptyList()
        }

        val remainder = finalTarget.substring(consumedPrefix.length).trimStart()
        if (remainder.isBlank()) return emptyList()
        consumedPrefix = finalTarget
        return segmentFully(remainder)
    }

    fun hasEmittedSpeech(): Boolean = consumedPrefix.isNotEmpty()

    private fun drainStreamingPrefix(normalized: String): List<String> {
        if (consumedPrefix.isNotEmpty() && !normalized.startsWith(consumedPrefix)) {
            // Streaming sanitization should be prefix-stable after the first emitted segment. If it
            // is not, stop speaking early rather than leaking text from an unstable completion.
            return emptyList()
        }

        var cursor = consumedPrefix.length
        while (cursor < normalized.length && normalized[cursor].isWhitespace()) cursor++
        if (cursor >= normalized.length) {
            consumedPrefix = normalized.substring(0, cursor)
            return emptyList()
        }

        val maxExclusive = minOf(normalized.length, streamingPrefixBudgetChars)
        if (cursor >= maxExclusive) return emptyList()

        val segments = mutableListOf<String>()
        while (cursor < maxExclusive) {
            val isFirstAudibleSegment = consumedPrefix.isEmpty() && segments.isEmpty()
            val splitChars = if (isFirstAudibleSegment) firstForcedSplitChars else forcedSplitChars
            val minSplitChars = if (isFirstAudibleSegment) firstMinForcedSplitChars else minForcedSplitChars
            val sentenceEnd = findSentenceBoundary(normalized, cursor, maxExclusive)
            val sentenceFitsEarlyWindow = sentenceEnd != null && sentenceEnd - cursor <= splitChars
            val cut = when {
                sentenceFitsEarlyWindow -> sentenceEnd
                maxExclusive - cursor >= splitChars ->
                    findForcedBoundary(
                        text = normalized,
                        start = cursor,
                        hardEnd = minOf(cursor + splitChars, maxExclusive),
                        minSplitChars = minSplitChars,
                        preferPhraseBoundary = isFirstAudibleSegment,
                    ) ?: sentenceEnd
                sentenceEnd != null -> sentenceEnd
                else -> null
            } ?: break

            val segment = normalized.substring(cursor, cut).trim()
            if (segment.isNotBlank()) segments += segment
            cursor = cut
            while (cursor < normalized.length && normalized[cursor].isWhitespace()) cursor++
        }

        if (cursor > consumedPrefix.length) {
            consumedPrefix = normalized.substring(0, cursor)
        }
        return segments
    }

    private fun findSentenceBoundary(text: String, start: Int, maxExclusive: Int): Int? {
        var index = start
        while (index < maxExclusive) {
            val char = text[index]
            if (char == '\n') return index + 1
            if (char == '.' || char == '!' || char == '?') {
                val next = index + 1
                if (next >= text.length || text[next].isWhitespace()) return next
            }
            index++
        }
        return null
    }

    private fun findForcedBoundary(
        text: String,
        start: Int,
        hardEnd: Int,
        minSplitChars: Int = minForcedSplitChars,
        preferPhraseBoundary: Boolean = false,
    ): Int? {
        if (hardEnd - start < minSplitChars) return null
        val minEnd = start + minSplitChars
        for (index in hardEnd - 1 downTo minEnd) {
            if (text[index] == ',' || text[index] == ';' || text[index] == ':' ||
                text[index] == '—' || text[index] == '–'
            ) {
                return index + 1
            }
        }

        if (preferPhraseBoundary) {
            // End the first audible chunk before a conjunction/transition instead of producing an
            // awkward utterance such as "...screen and". Later chunks retain the old segmentation.
            val lower = text.lowercase()
            var phraseBoundary = -1
            for (separator in NATURAL_PHRASE_SEPARATORS) {
                val index = lower.lastIndexOf(separator, startIndex = hardEnd - 1)
                if (
                    index >= minEnd &&
                    index + separator.length <= hardEnd &&
                    index > phraseBoundary
                ) {
                    phraseBoundary = index
                }
            }
            if (phraseBoundary >= minEnd) return phraseBoundary
        }

        for (index in hardEnd - 1 downTo minEnd) {
            if (text[index].isWhitespace()) return index
        }
        return hardEnd
    }

    private fun segmentFully(text: String): List<String> {
        val segments = mutableListOf<String>()
        var cursor = 0
        while (cursor < text.length) {
            while (cursor < text.length && text[cursor].isWhitespace()) cursor++
            if (cursor >= text.length) break

            val hardEnd = minOf(cursor + DEFAULT_FINAL_SEGMENT_CHARS, text.length)
            val sentenceEnd = findSentenceBoundary(text, cursor, hardEnd)
            val cut: Int = sentenceEnd ?: if (hardEnd < text.length) {
                findForcedBoundary(text, cursor, hardEnd) ?: hardEnd
            } else {
                text.length
            }
            val segment = text.substring(cursor, cut).trim()
            if (segment.isNotBlank()) segments += segment
            cursor = cut
        }
        return segments
    }

    private companion object {
        const val DEFAULT_STREAMING_PREFIX_BUDGET_CHARS = 180
        const val DEFAULT_FIRST_FORCED_SPLIT_CHARS = 72
        const val DEFAULT_FIRST_MIN_FORCED_SPLIT_CHARS = 48
        const val DEFAULT_FORCED_SPLIT_CHARS = 140
        const val DEFAULT_MIN_FORCED_SPLIT_CHARS = 80
        const val DEFAULT_FINAL_SEGMENT_CHARS = 180
        val NATURAL_PHRASE_SEPARATORS = listOf(
            " and ",
            " but ",
            " while ",
            " because ",
            " so ",
            " then ",
            " which ",
        )
    }
}

package com.fersaiyan.cyanbridge.plugins.walkingaid

import java.text.Normalizer
import java.util.Locale
import kotlin.math.max

/** Converts a casual description into labels supported by the installed object detectors. */
object WalkingAidFocusMapper {
    private val supportedLabels = listOf(
        "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck",
        "boat", "traffic light", "fire hydrant", "stop sign", "parking meter", "bench",
        "bird", "cat", "dog", "horse", "sheep", "cow", "elephant", "bear", "zebra",
        "giraffe", "backpack", "umbrella", "handbag", "tie", "suitcase", "frisbee",
        "skis", "snowboard", "sports ball", "kite", "baseball bat", "baseball glove",
        "skateboard", "surfboard", "tennis racket", "bottle", "wine glass", "cup",
        "fork", "knife", "spoon", "bowl", "banana", "apple", "sandwich", "orange",
        "broccoli", "carrot", "hot dog", "pizza", "donut", "cake", "chair", "couch",
        "potted plant", "bed", "dining table", "toilet", "tv", "laptop", "mouse",
        "remote", "keyboard", "cell phone", "microwave", "oven", "toaster", "sink",
        "refrigerator", "book", "clock", "vase", "scissors", "teddy bear", "hair drier",
        "toothbrush",
    )

    private val casualGroups = linkedMapOf(
        setOf("people", "pedestrians", "someone", "crowds", "children", "kids") to setOf("person"),
        setOf("traffic", "road traffic", "moving vehicles") to setOf(
            "bicycle", "car", "motorcycle", "bus", "truck",
        ),
        setOf("vehicles", "transportation") to setOf(
            "bicycle", "car", "motorcycle", "bus", "train", "truck", "boat",
        ),
        setOf("bikes", "cyclists", "cycles") to setOf("bicycle", "motorcycle"),
        setOf("pets", "pet animals") to setOf("bird", "cat", "dog"),
        setOf("animals", "wildlife") to setOf(
            "bird", "cat", "dog", "horse", "sheep", "cow", "elephant", "bear", "zebra", "giraffe",
        ),
        setOf("street signs", "road signs", "crossings", "intersections") to setOf(
            "traffic light", "stop sign",
        ),
        setOf("seats", "places to sit", "furniture") to setOf(
            "bench", "chair", "couch", "bed", "dining table",
        ),
        setOf("things on the floor", "things on the ground", "trip hazards", "things i could trip over") to setOf(
            "backpack", "suitcase", "sports ball", "skateboard", "bottle", "chair",
        ),
        setOf("bags", "luggage") to setOf("backpack", "handbag", "suitcase"),
        setOf("screens", "electronics") to setOf(
            "tv", "laptop", "cell phone", "keyboard", "remote",
        ),
    )

    /**
     * Matches a natural-language request only against labels that the detector found in this
     * frame. This keeps custom watch-list behavior model-agnostic and avoids treating unsupported
     * prompt nouns as real detections.
     */
    fun matchDetectedLabels(text: String, detectedLabels: Collection<String>): List<String> {
        val normalized = normalize(text)
        if (normalized.isBlank()) return emptyList()
        val padded = " $normalized "
        val resolved = linkedSetOf<String>()

        detectedLabels
            .asSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinctBy(::normalize)
            .forEach { label ->
                val normalizedLabel = normalize(label)
                if (matchesPhrase(padded, normalized, normalizedLabel)) {
                    resolved += label
                }
            }
        casualGroups.forEach { (phrases, labels) ->
            if (phrases.any { matchesPhrase(padded, normalized, normalize(it)) }) {
                detectedLabels.forEach { detected ->
                    if (labels.any { normalize(it) == normalize(detected) }) resolved += detected
                }
            }
        }
        return resolved.toList()
    }

    /** Used by settings previews before a frame is available. Runtime warnings use detected labels. */
    fun resolve(text: String): List<String> = matchDetectedLabels(text, supportedLabels)

    fun friendlySummary(labels: List<String>): String = labels.joinToString(", ") { label ->
        when (normalize(label)) {
            "person" -> "people"
            "sports ball" -> "balls"
            "cell phone" -> "phones"
            "traffic light" -> "traffic lights"
            "stop sign" -> "stop signs"
            else -> pluralOf(label)
        }
    }

    private fun matchesPhrase(paddedText: String, normalizedText: String, phrase: String): Boolean {
        if (phrase.isBlank()) return false
        if (containsPhrase(paddedText, phrase) || containsPhrase(paddedText, pluralOf(phrase))) {
            return true
        }

        val phraseWords = phrase.split(' ')
        val textWords = normalizedText.split(' ')
        if (textWords.size < phraseWords.size) return false
        val singularPhrase = singularize(phrase)
        val pluralPhrase = pluralOf(phrase)
        return textWords.windowed(phraseWords.size).any { window ->
            if (window.size == 1 && window[0] in FUZZY_STOP_WORDS) return@any false
            val candidate = window.joinToString(" ")
            fuzzyEquivalent(candidate, phrase) ||
                fuzzyEquivalent(candidate, pluralPhrase) ||
                fuzzyEquivalent(singularize(candidate), singularPhrase)
        }
    }

    private fun fuzzyEquivalent(left: String, right: String): Boolean {
        if (left == right) return true
        val longest = max(left.length, right.length)
        if (longest < MIN_FUZZY_LENGTH || kotlin.math.abs(left.length - right.length) > MAX_EDIT_DISTANCE) {
            return false
        }
        val allowedDistance = if (longest >= MIN_FUZZY_LENGTH) 1 else 0
        return editDistanceAtMost(left, right, allowedDistance)
    }

    private fun editDistanceAtMost(left: String, right: String, limit: Int): Boolean {
        if (kotlin.math.abs(left.length - right.length) > limit) return false
        val distance = Array(left.length + 1) { IntArray(right.length + 1) }
        for (leftIndex in 0..left.length) distance[leftIndex][0] = leftIndex
        for (rightIndex in 0..right.length) distance[0][rightIndex] = rightIndex
        for (leftIndex in 1..left.length) {
            for (rightIndex in 1..right.length) {
                val substitutionCost = if (left[leftIndex - 1] == right[rightIndex - 1]) 0 else 1
                distance[leftIndex][rightIndex] = minOf(
                    distance[leftIndex][rightIndex - 1] + 1,
                    distance[leftIndex - 1][rightIndex] + 1,
                    distance[leftIndex - 1][rightIndex - 1] + substitutionCost,
                )
                if (
                    leftIndex > 1 && rightIndex > 1 &&
                    left[leftIndex - 1] == right[rightIndex - 2] &&
                    left[leftIndex - 2] == right[rightIndex - 1]
                ) {
                    distance[leftIndex][rightIndex] = minOf(
                        distance[leftIndex][rightIndex],
                        distance[leftIndex - 2][rightIndex - 2] + 1,
                    )
                }
            }
        }
        return distance[left.length][right.length] <= limit
    }

    private fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

    private fun containsPhrase(paddedText: String, phrase: String): Boolean =
        phrase.isNotBlank() && paddedText.contains(" $phrase ")

    private fun singularize(value: String): String {
        val words = value.split(' ').toMutableList()
        val last = words.lastOrNull() ?: return value
        words[words.lastIndex] = when {
            last.endsWith("ies") && last.length > 4 -> last.dropLast(3) + "y"
            last.endsWith("ches") || last.endsWith("shes") || last.endsWith("xes") || last.endsWith("zes") -> last.dropLast(2)
            last.endsWith("s") && !last.endsWith("ss") && last.length > 3 -> last.dropLast(1)
            else -> last
        }
        return words.joinToString(" ")
    }

    private fun pluralOf(label: String): String {
        val normalized = normalize(label)
        val words = normalized.split(' ').toMutableList()
        val last = words.lastOrNull() ?: return normalized
        words[words.lastIndex] = when {
            last.endsWith("s") || last.endsWith("x") || last.endsWith("z") ||
                last.endsWith("ch") || last.endsWith("sh") -> last + "es"
            last.endsWith("y") -> last.dropLast(1) + "ies"
            else -> last + "s"
        }
        return words.joinToString(" ")
    }

    private const val MIN_FUZZY_LENGTH = 5
    private const val MAX_EDIT_DISTANCE = 1
    private val FUZZY_STOP_WORDS = setOf(
        "about", "ahead", "anything", "attention", "careful", "could", "dangerous",
        "extra", "focus", "look", "looking", "notice", "open", "please", "should",
        "things", "unusual", "warn", "warning", "watch", "with", "would",
    )
}

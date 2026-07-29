package com.fersaiyan.cyanbridge.plugins.walkingaid

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

    fun resolve(text: String): List<String> {
        val normalized = normalize(text)
        if (normalized.isBlank()) return emptyList()
        val padded = " $normalized "
        val resolved = linkedSetOf<String>()

        supportedLabels.forEach { label ->
            if (containsPhrase(padded, label) || containsPhrase(padded, pluralOf(label))) {
                resolved += label
            }
        }
        casualGroups.forEach { (phrases, labels) ->
            if (phrases.any { containsPhrase(padded, normalize(it)) }) {
                resolved += labels
            }
        }
        return resolved.toList()
    }

    fun friendlySummary(labels: List<String>): String = labels.joinToString(", ") { label ->
        when (label) {
            "person" -> "people"
            "sports ball" -> "balls"
            "cell phone" -> "phones"
            "traffic light" -> "traffic lights"
            "stop sign" -> "stop signs"
            else -> pluralOf(label)
        }
    }

    private fun normalize(text: String): String = text
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

    private fun containsPhrase(paddedText: String, phrase: String): Boolean =
        phrase.isNotBlank() && paddedText.contains(" $phrase ")

    private fun pluralOf(label: String): String = when {
        label.endsWith("s") || label.endsWith("x") || label.endsWith("z") ||
            label.endsWith("ch") || label.endsWith("sh") -> label + "es"
        label.endsWith("y") -> label.dropLast(1) + "ies"
        else -> label + "s"
    }
}

package com.ad_glasses.ai.image

/** The bytes supplied to the model, not a cosmetic image-quality preference. */
enum class ImageQuestionSource(
    val wireName: String,
    val label: String,
) {
    HIGH_QUALITY(
        wireName = "high_quality",
        label = "High quality (Wi-Fi full resolution)",
    ),
    FAST_PREVIEW(
        wireName = "fast_preview",
        label = "Fast preview (BLE thumbnail)",
    ),
}

/** Verified thumbnail sizes exposed by the vendor app's AI clarity selector. */
enum class ImageThumbnailQuality(
    val sdkValue: Int,
    val label: String,
) {
    INSTANT(0, "Instant"),
    QUICK(1, "Quick"),
    SMOOTH(2, "Smooth"),
    FINE(3, "Fine"),
    CLEARER(4, "Clearer"),
    DETAILED(5, "Detailed"),
}

enum class HighQualityFailureChoice {
    RETRY_HIGH_QUALITY,
    USE_FAST_PREVIEW,
    CANCEL,
}

enum class ImageSourceResolution {
    HIGH_QUALITY,
    FAST_PREVIEW,
    AWAITING_EXPLICIT_FALLBACK_CHOICE,
    CANCELLED,
}

object ImageQuestionSourcePolicy {
    fun defaultSource(): ImageQuestionSource = ImageQuestionSource.FAST_PREVIEW

    fun defaultThumbnailQuality(): ImageThumbnailQuality = ImageThumbnailQuality.DETAILED

    /** A Wi-Fi failure must never silently turn into a BLE-thumbnail request. */
    fun onHighQualityFailure(): ImageSourceResolution =
        ImageSourceResolution.AWAITING_EXPLICIT_FALLBACK_CHOICE

    fun resolveHighQualityFailure(choice: HighQualityFailureChoice): ImageSourceResolution = when (choice) {
        HighQualityFailureChoice.RETRY_HIGH_QUALITY -> ImageSourceResolution.HIGH_QUALITY
        HighQualityFailureChoice.USE_FAST_PREVIEW -> ImageSourceResolution.FAST_PREVIEW
        HighQualityFailureChoice.CANCEL -> ImageSourceResolution.CANCELLED
    }
}

package com.fersaiyan.cyanbridge.ai.image

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

enum class ExternalImageAutomationStage(val wireName: String) {
    IDLE("idle"),
    IMAGE_STARTED("image_started"),
    IMAGE_ATTACHED("image_attached"),
    PROMPT_SENT("prompt_sent"),
    ANSWER_READY("answer_ready"),
    FAILED("failed"),
    ;

    companion object {
        fun fromWireName(value: String?): ExternalImageAutomationStage? =
            entries.firstOrNull { it.wireName == value }
    }
}

data class ExternalImageAutomationState(
    val stage: ExternalImageAutomationStage = ExternalImageAutomationStage.IDLE,
    val error: String? = null,
)

/** Rejects stale/out-of-order automation callbacks instead of opening a follow-up early. */
object ExternalImageAutomationStateMachine {
    fun transition(
        current: ExternalImageAutomationState,
        next: ExternalImageAutomationStage,
        error: String? = null,
    ): ExternalImageAutomationState {
        if (next == ExternalImageAutomationStage.IMAGE_STARTED) {
            return if (
                current.stage == ExternalImageAutomationStage.IDLE ||
                current.stage == ExternalImageAutomationStage.FAILED
            ) {
                ExternalImageAutomationState(next)
            } else {
                current
            }
        }
        if (next == ExternalImageAutomationStage.FAILED) {
            return ExternalImageAutomationState(next, error?.takeIf { it.isNotBlank() } ?: "External automation failed")
        }
        if (next == current.stage) return current

        val accepted = when (next) {
            ExternalImageAutomationStage.IMAGE_ATTACHED ->
                current.stage == ExternalImageAutomationStage.IMAGE_STARTED
            ExternalImageAutomationStage.PROMPT_SENT ->
                current.stage == ExternalImageAutomationStage.IMAGE_ATTACHED
            ExternalImageAutomationStage.ANSWER_READY ->
                current.stage == ExternalImageAutomationStage.PROMPT_SENT
            ExternalImageAutomationStage.IDLE,
            ExternalImageAutomationStage.IMAGE_STARTED,
            ExternalImageAutomationStage.FAILED -> false
        }
        return if (accepted) ExternalImageAutomationState(next) else current
    }
}

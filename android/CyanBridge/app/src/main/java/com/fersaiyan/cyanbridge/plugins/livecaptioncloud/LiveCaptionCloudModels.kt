package com.fersaiyan.cyanbridge.plugins.livecaptioncloud

data class CaptionEntry(
    val timestampMs: Long,
    val originalText: String,
    val translatedText: String?,
    val sourceLanguage: String,
    val targetLanguage: String?,
    val confidence: Float,
)

data class CaptionDisplaySettings(
    val fontSize: Int,
    val showOriginal: Boolean,
    val showTranslation: Boolean,
    val backgroundColor: String,
    val textColor: String,
)

enum class CaptionPosition {
    TOP,
    BOTTOM,
    CENTER,
}

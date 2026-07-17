package com.fersaiyan.cyanbridge.ai.vision

import java.util.Locale

enum class VisionProfile(
    val displayName: String,
    val maxWords: Int,
) {
    WALKING("Walking", 18),
    DETAILED("Detailed", 90),
}

data class VisionProfileSettings(
    val profile: VisionProfile,
    val responseLanguageTag: String,
    val customInstructions: String = "",
)

/** Builds the first multimodal request so every provider receives the same assistive intent. */
object VisionPromptBuilder {
    fun build(settings: VisionProfileSettings, userQuestion: String?): String {
        val language = languageLabel(settings.responseLanguageTag)
        val customInstructions = settings.customInstructions.trim().take(MAX_CUSTOM_INSTRUCTIONS_CHARS)
        val question = userQuestion?.trim().orEmpty()

        return buildString {
            appendLine("You are an assistive visual description system for a blind or low-vision user.")
            appendLine("Respond only in $language (${settings.responseLanguageTag}).")
            appendLine("Describe only what is visibly supported by the image. Do not guess or claim that a route is safe.")
            when (settings.profile) {
                VisionProfile.WALKING -> {
                    appendLine("Use one direct sentence of at most ${settings.profile.maxWords} words.")
                    appendLine("Prioritize immediate obstacles, ground-level changes, moving hazards, landmarks, and essential visible text.")
                    appendLine("If nothing relevant is visible, state that briefly without claiming the path is safe.")
                }

                VisionProfile.DETAILED -> {
                    appendLine("Give a useful scene description of at most ${settings.profile.maxWords} words.")
                    appendLine("Include layout, important objects, people, landmarks, and essential visible text when present.")
                }
            }
            if (customInstructions.isNotBlank()) {
                appendLine("Additional user instructions: $customInstructions")
            }
            if (question.isNotBlank()) {
                appendLine("User question: $question")
            }
            append("Always respond only in $language.")
        }
    }

    private fun languageLabel(tag: String): String {
        val locale = Locale.forLanguageTag(tag)
        return locale.getDisplayLanguage(Locale.ENGLISH).ifBlank { tag }
    }

    private const val MAX_CUSTOM_INSTRUCTIONS_CHARS = 1_500
}

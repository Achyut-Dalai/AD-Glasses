package com.ad_glasses.ai.vision

import java.util.Locale

data class ImageQuestionSettings(
    val appLanguageTag: String,
    val defaultQuestion: String,
    val usesBuiltInDefault: Boolean,
)

enum class ImageQuestionRoute {
    CLOUD_API,
}

/** A single resolved prompt is deliberately shared by every Cloud image-question entry point. */
data class ResolvedImageQuestionPrompt(
    val text: String,
) {
    fun forRoute(route: ImageQuestionRoute): String = when (route) {
        ImageQuestionRoute.CLOUD_API -> text
    }
}

object ImageQuestionDefaults {
    /**
     * Do not synthesize the listening prompt through Kokoro. Voice-query callers treat a blank cue
     * as immediately complete and can start Moonshine without running both native speech engines at
     * the same time. A packaged non-TTS earcon can be wired into those callers separately.
     */
    @Suppress("UNUSED_PARAMETER")
    fun listeningCueForLanguage(languageTag: String): String = ""

    fun questionCueForLanguage(languageTag: String): String = when (
        Locale.forLanguageTag(languageTag).language.lowercase(Locale.ROOT)
    ) {
        "pt" -> "Pergunte."
        "es" -> "Pregunta."
        "de" -> "Frag."
        "fr" -> "Demandez."
        "it" -> "Chiedi."
        "zh" -> "请提问。"
        "ko" -> "질문하세요."
        "ru" -> "Спросите."
        else -> "Ask."
    }

    fun questionForLanguage(languageTag: String): String = when (
        Locale.forLanguageTag(languageTag).language.lowercase(Locale.ROOT)
    ) {
        "pt" -> "Dê-me uma descrição concisa da imagem"
        "es" -> "Dame una descripción concisa de la imagen"
        "de" -> "Gib mir eine kurze Beschreibung des Bildes"
        "fr" -> "Donnez-moi une description concise de l'image"
        "it" -> "Dammi una descrizione concisa dell'immagine"
        "zh" -> "请简洁地描述这张图片"
        "ko" -> "이미지를 간단히 설명해 주세요"
        "ru" -> "Дайте мне краткое описание изображения"
        else -> "Give me a concise description of the image"
    }

    fun responseLanguageInstruction(languageTag: String): String =
        "Answer only in ${languageLabel(languageTag)} ($languageTag)."

    private fun languageLabel(languageTag: String): String =
        Locale.forLanguageTag(languageTag)
            .getDisplayLanguage(Locale.ENGLISH)
            .ifBlank { languageTag }
}

object ImageQuestionPromptResolver {
    fun resolve(
        settings: ImageQuestionSettings,
        userQuestion: String?,
    ): ResolvedImageQuestionPrompt {
        val languageTag = settings.appLanguageTag.ifBlank { "en" }
        val question = userQuestion?.trim().takeUnless { it.isNullOrBlank() }
            ?: settings.defaultQuestion.trim().ifBlank {
                ImageQuestionDefaults.questionForLanguage(languageTag)
            }

        return ResolvedImageQuestionPrompt(
            text = "$question\n\n${ImageQuestionDefaults.responseLanguageInstruction(languageTag)}",
        )
    }
}

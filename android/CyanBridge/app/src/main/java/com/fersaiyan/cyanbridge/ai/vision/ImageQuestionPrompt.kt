package com.fersaiyan.cyanbridge.ai.vision

import java.util.Locale

data class ImageQuestionSettings(
    val appLanguageTag: String,
    val defaultQuestion: String,
    val usesBuiltInDefault: Boolean,
)

enum class ImageQuestionRoute {
    PRO_RELAY,
    LOCAL_GEMMA,
    TASKER_GEMINI,
}

/** A single resolved prompt is deliberately shared by every image-question route. */
data class ResolvedImageQuestionPrompt(
    val text: String,
) {
    fun forRoute(route: ImageQuestionRoute): String = when (route) {
        ImageQuestionRoute.PRO_RELAY,
        ImageQuestionRoute.LOCAL_GEMMA,
        ImageQuestionRoute.TASKER_GEMINI
        -> text
    }
}

object ImageQuestionDefaults {
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
        "pt" -> "Descreva o que está à minha frente. Mencione textos importantes, objetos, perigos e detalhes que possam ser úteis."
        "es" -> "Describe lo que hay frente a mí. Menciona texto importante, objetos, peligros y detalles que puedan ser útiles."
        "de" -> "Beschreibe, was vor mir ist. Nenne wichtige Texte, Objekte, Gefahren und hilfreiche Details."
        "fr" -> "Décrivez ce qui se trouve devant moi. Mentionnez les textes importants, les objets, les dangers et les détails utiles."
        "it" -> "Descrivi ciò che ho davanti. Indica testi importanti, oggetti, pericoli e dettagli utili."
        "zh" -> "描述我面前的内容。请提及重要文字、物体、危险和可能有用的细节。"
        "ko" -> "내 앞에 있는 것을 설명해 주세요. 중요한 글자, 물체, 위험 요소와 도움이 될 만한 세부 사항을 알려 주세요."
        "ru" -> "Опишите то, что находится передо мной. Упомяните важный текст, объекты, опасности и полезные детали."
        else -> "Describe what is in front of me. Mention important text, objects, hazards, and details that may be useful."
    }
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
            text = "$question\n\nAnswer only in ${languageLabel(languageTag)} ($languageTag).",
        )
    }

    private fun languageLabel(languageTag: String): String {
        return Locale.forLanguageTag(languageTag)
            .getDisplayLanguage(Locale.ENGLISH)
            .ifBlank { languageTag }
    }
}

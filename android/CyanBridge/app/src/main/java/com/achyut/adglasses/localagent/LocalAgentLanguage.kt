package com.achyut.adglasses.localagent

import com.achyut.adglasses.ai.vision.ImageQuestionPreferences
import java.util.Locale

object LocalAgentLanguage {

    private val completionByLocale = mapOf(
        "en" to "Task finished.",
        "pt" to "Tarefa concluída.",
        "es" to "Tarea finalizada.",
        "de" to "Aufgabe abgeschlossen.",
        "fr" to "Tâche terminée.",
        "it" to "Attività completata.",
        "zh" to "任务已完成。",
        "ko" to "작업이 완료되었습니다.",
        "ru" to "Задача выполнена.",
    )

    private val approvalCueByLocale = mapOf(
        "en" to "Answer now.",
        "pt" to "Responda agora.",
        "es" to "Responda ahora.",
        "de" to "Jetzt antworten.",
        "fr" to "Répondez maintenant.",
        "it" to "Rispondi ora.",
        "zh" to "请现在回答。",
        "ko" to "지금 대답해 주세요.",
        "ru" to "Ответьте сейчас.",
    )

    fun currentLocale(context: android.content.Context): Locale {
        val tag = ImageQuestionPreferences.get(context).appLanguageTag
        return if (tag.isNullOrBlank()) Locale.getDefault() else Locale.forLanguageTag(tag)
    }

    fun currentLocalePrefix(context: android.content.Context): String {
        val tag = ImageQuestionPreferences.get(context).appLanguageTag
        return if (tag.isNullOrBlank()) Locale.getDefault().language else tag.take(2)
    }

    fun completionSpeech(context: android.content.Context): String {
        val key = currentLocalePrefix(context)
        return completionByLocale[key] ?: completionByLocale["en"] ?: "Task finished."
    }

    fun approvalListeningCue(context: android.content.Context): String {
        val key = currentLocalePrefix(context)
        return approvalCueByLocale[key] ?: approvalCueByLocale["en"] ?: "Answer now."
    }
}

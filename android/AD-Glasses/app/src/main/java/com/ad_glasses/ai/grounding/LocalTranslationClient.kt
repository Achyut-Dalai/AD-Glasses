package com.ad_glasses.ai.grounding

import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * On-device ML Kit translation. Models are downloaded once and then reused by ML Kit locally.
 * First-time model downloads are Wi-Fi-only so a voice request cannot silently consume ~30 MB of
 * mobile data. If a required model is unavailable, callers can fall back to the configured AD LLM.
 */
class LocalTranslationClient {
    suspend fun translate(
        text: String,
        sourceLanguage: String?,
        targetLanguage: String,
    ): Result<StructuredKnowledgeResult> = try {
        val cleanText = text.replace(Regex("\\s+"), " ").trim().take(MAX_TRANSLATION_CHARS)
        require(cleanText.isNotBlank()) { "Translation text cannot be blank." }
        val target = TranslateLanguage.fromLanguageTag(targetLanguage.trim())
            ?: throw IllegalArgumentException("ML Kit does not support target language '$targetLanguage'.")
        val source = sourceLanguage
            ?.trim()
            ?.takeIf { it.isNotBlank() && !it.equals("auto", ignoreCase = true) }
            ?.let(TranslateLanguage::fromLanguageTag)
            ?: identifyLanguage(cleanText)
            ?: throw IllegalStateException("ML Kit could not identify the source language.")
        if (source == target) {
            return Result.success(
                StructuredKnowledgeResult(
                    answer = cleanText,
                    context = "On-device translation was unnecessary because source and target language are both $target.",
                    sources = emptyList(),
                ),
            )
        }

        val translator = Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(source)
                .setTargetLanguage(target)
                .build(),
        )
        try {
            val conditions = DownloadConditions.Builder().requireWifi().build()
            translator.downloadModelIfNeeded(conditions).awaitTask()
            val translated = translator.translate(cleanText).awaitTask()
                .replace(Regex("\\s+"), " ")
                .trim()
                .take(MAX_TRANSLATION_CHARS)
            require(translated.isNotBlank()) { "ML Kit returned an empty translation." }
            Result.success(
                StructuredKnowledgeResult(
                    answer = translated,
                    context = "On-device ML Kit translation from $source to $target: $translated",
                    sources = emptyList(),
                ),
            )
        } finally {
            translator.close()
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Result.failure(error)
    }

    private suspend fun identifyLanguage(text: String): String? {
        val identifier = LanguageIdentification.getClient()
        return try {
            identifier.identifyLanguage(text).awaitTask()
                .takeIf { it.isNotBlank() && it != "und" }
                ?.let(TranslateLanguage::fromLanguageTag)
        } finally {
            identifier.close()
        }
    }

    private companion object {
        const val MAX_TRANSLATION_CHARS = 2_000
    }
}

private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { value ->
        if (continuation.isActive) continuation.resume(value) { _, _, _ -> }
    }
    addOnFailureListener { error ->
        if (continuation.isActive) continuation.resumeWithException(error)
    }
}

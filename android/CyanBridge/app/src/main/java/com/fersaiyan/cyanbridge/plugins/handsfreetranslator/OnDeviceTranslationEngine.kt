package com.fersaiyan.cyanbridge.plugins.handsfreetranslator

import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentifier
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

data class OnDeviceTranslationResult(
    val translatedText: String,
    val sourceLanguage: String,
    val targetLanguage: String,
)

/** Phone-owned, on-device translation. No prompt, API key, relay, or chat provider is used. */
class OnDeviceTranslationEngine : Closeable {
    private val identifier: LanguageIdentifier = LanguageIdentification.getClient()
    private val translators = ConcurrentHashMap<String, Translator>()

    suspend fun translate(
        text: String,
        configuredSourceLanguage: String,
        configuredTargetLanguage: String,
        autoDetect: Boolean,
        requireWifiForModelDownload: Boolean = true,
    ): OnDeviceTranslationResult {
        val input = text.trim()
        require(input.isNotBlank()) { "There is no speech to translate" }

        val detected = if (autoDetect) identifier.identifyLanguage(input).awaitResult() else configuredSourceLanguage
        val sourceTag = detected.takeIf { it.isNotBlank() && it != UNDETERMINED }
            ?: throw IllegalStateException("Couldn’t identify that phrase’s language. Choose a source language and try again.")
        val source = TranslateLanguage.fromLanguageTag(sourceTag)
            ?: throw IllegalStateException("On-device translation does not support source language '$sourceTag'.")
        val target = TranslateLanguage.fromLanguageTag(configuredTargetLanguage)
            ?: throw IllegalStateException("On-device translation does not support target language '$configuredTargetLanguage'.")

        if (source == target) {
            return OnDeviceTranslationResult(input, source, target)
        }

        val key = "$source->$target"
        val translator = translators.getOrPut(key) {
            Translation.getClient(
                TranslatorOptions.Builder()
                    .setSourceLanguage(source)
                    .setTargetLanguage(target)
                    .build(),
            )
        }
        val downloadConditions = DownloadConditions.Builder().apply {
            if (requireWifiForModelDownload) requireWifi()
        }.build()
        translator.downloadModelIfNeeded(downloadConditions).awaitResult()
        val translated = translator.translate(input).awaitResult().trim()
        require(translated.isNotBlank()) { "The on-device translator returned an empty result" }
        return OnDeviceTranslationResult(translated, source, target)
    }

    override fun close() {
        translators.values.forEach(Translator::close)
        translators.clear()
        identifier.close()
    }

    private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result -> if (continuation.isActive) continuation.resume(result) }
        addOnFailureListener { error -> if (continuation.isActive) continuation.resumeWithException(error) }
        addOnCanceledListener { continuation.cancel() }
    }

    private companion object {
        const val UNDETERMINED = "und"
    }
}

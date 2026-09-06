package com.adglasses.app.core.translation

import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.LinkedHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MlKitTranslationEngine {
    companion object {
        /** A few active bilingual directions cover a live session without retaining every pair. */
        private const val MAX_CACHED_TRANSLATORS = 4
    }

    private val cacheLock = Any()
    private val sessions = LinkedHashMap<LanguagePair, TranslatorSession>(8, 0.75f, true)

    suspend fun translate(text: String, sourceLanguageTag: String, targetLanguageTag: String): String {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return ""

        val source = TranslateLanguage.fromLanguageTag(sourceLanguageTag)
            ?: error("Unsupported source language")
        val target = TranslateLanguage.fromLanguageTag(targetLanguageTag)
            ?: error("Unsupported target language")
        if (source == target) return cleaned

        val pair = LanguagePair(source, target)
        val session = acquire(pair)
        return try {
            session.mutex.withLock {
                if (!session.modelReady) {
                    session.translator
                        .downloadModelIfNeeded(DownloadConditions.Builder().build())
                        .await()
                    session.modelReady = true
                }
                session.translator.translate(cleaned).await()
            }
        } finally {
            release(pair, session)
        }
    }

    /** Close retained clients if the app graph ever gains an explicit shutdown lifecycle. */
    fun close() {
        val toClose = synchronized(cacheLock) {
            val copy = sessions.values.map { it.translator }
            sessions.clear()
            copy
        }
        toClose.forEach(Translator::close)
    }

    private fun acquire(pair: LanguagePair): TranslatorSession {
        lateinit var session: TranslatorSession
        val evicted = synchronized(cacheLock) {
            session = sessions[pair] ?: TranslatorSession(
                translator = Translation.getClient(
                    TranslatorOptions.Builder()
                        .setSourceLanguage(pair.source)
                        .setTargetLanguage(pair.target)
                        .build()
                )
            ).also { sessions[pair] = it }
            session.users += 1
            trimIdleSessionsLocked()
        }
        evicted.forEach(Translator::close)
        return session
    }

    private fun release(pair: LanguagePair, session: TranslatorSession) {
        val evicted = synchronized(cacheLock) {
            val current = sessions[pair]
            if (current === session) session.users = (session.users - 1).coerceAtLeast(0)
            trimIdleSessionsLocked()
        }
        evicted.forEach(Translator::close)
    }

    /**
     * Never close a client with an in-flight Task. If every cached direction is busy, the cache may
     * temporarily exceed the target and will shrink naturally as translations complete.
     */
    private fun trimIdleSessionsLocked(): List<Translator> {
        if (sessions.size <= MAX_CACHED_TRANSLATORS) return emptyList()
        val toClose = mutableListOf<Translator>()
        val iterator = sessions.entries.iterator()
        while (sessions.size > MAX_CACHED_TRANSLATORS && iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value.users == 0) {
                iterator.remove()
                toClose += entry.value.translator
            }
        }
        return toClose
    }

    private data class LanguagePair(val source: String, val target: String)

    private data class TranslatorSession(
        val translator: Translator,
        val mutex: Mutex = Mutex(),
        var modelReady: Boolean = false,
        var users: Int = 0,
    )
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { value -> if (continuation.isActive) continuation.resume(value) }
    addOnFailureListener { error -> if (continuation.isActive) continuation.resumeWithException(error) }
    addOnCanceledListener { continuation.cancel() }
}

package com.fersaiyan.cyanbridge.plugins.handsfreetranslator

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

class HandsFreeTranslatorStore {
    private val TAG = "HandsFreeTranslatorStore"
    private val PREFS = "hands_free_translator_store"
    private val KEY_TRANSLATIONS = "translations_json"
    private val KEY_PRESETS = "presets_json"

    private val lock = Any()
    private val translations = mutableListOf<TranslationEntry>()
    private val presets = mutableListOf<TranslationPreset>()
    private var loaded = false

    fun addTranslation(translation: TranslationEntry, maxHistory: Int) {
        synchronized(lock) {
            pruneExpiredLocked(translation.timestampMs)
            translations.add(translation)
            val overflow = translations.size - maxHistory.coerceAtLeast(50)
            if (overflow > 0) {
                repeat(overflow) { translations.removeFirstOrNull() }
            }
        }
    }

    fun getTranslations(maxCount: Int): List<TranslationEntry> {
        synchronized(lock) {
            if (translations.isEmpty()) return emptyList()
            val takeCount = maxCount.coerceIn(1, translations.size)
            return translations.takeLast(takeCount).toList()
        }
    }

    fun addPreset(preset: TranslationPreset) {
        synchronized(lock) {
            presets.add(preset)
        }
    }

    fun getPresets(): List<TranslationPreset> {
        synchronized(lock) {
            return presets.toList()
        }
    }

    fun removePreset(presetId: String) {
        synchronized(lock) {
            presets.removeAll { it.id == presetId }
        }
    }

    fun clear(context: Context) {
        synchronized(lock) {
            translations.clear()
            persistInternal(context, 100)
        }
    }

    private fun persistInternal(context: Context, maxHistory: Int) {
        try {
            val arr = JSONArray()
            val items = translations.takeLast(maxHistory.coerceAtLeast(50))
            for (translation in items) {
                arr.put(
                    JSONObject()
                        .put("timestampMs", translation.timestampMs)
                        .put("originalText", translation.originalText)
                        .put("translatedText", translation.translatedText)
                        .put("sourceLanguage", translation.sourceLanguage)
                        .put("targetLanguage", translation.targetLanguage)
                        .put("confidence", translation.confidence.toDouble())
                )
            }
            prefs(context).edit().putString(KEY_TRANSLATIONS, arr.toString()).apply()

            // Persist presets
            val presetsArr = JSONArray()
            for (preset in presets) {
                presetsArr.put(
                    JSONObject()
                        .put("id", preset.id)
                        .put("name", preset.name)
                        .put("sourceLanguage", preset.sourceLanguage)
                        .put("targetLanguage", preset.targetLanguage)
                        .put("phrases", JSONArray(preset.phrases))
                )
            }
            prefs(context).edit().putString(KEY_PRESETS, presetsArr.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist translations", e)
        }
    }

    fun persist(context: Context, maxHistory: Int) {
        persistInternal(context, maxHistory)
    }

    fun load(context: Context) {
        synchronized(lock) {
            if (loaded) return
            loaded = true
            try {
                // Load translations
                val raw = prefs(context).getString(KEY_TRANSLATIONS, "[]") ?: "[]"
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val translation = TranslationEntry(
                        timestampMs = obj.optLong("timestampMs", 0L),
                        originalText = obj.optString("originalText", ""),
                        translatedText = obj.optString("translatedText", ""),
                        sourceLanguage = obj.optString("sourceLanguage", "en"),
                        targetLanguage = obj.optString("targetLanguage", "es"),
                        confidence = obj.optDouble("confidence", 0.9).toFloat(),
                    )
                    if (translation.originalText.isNotBlank()) {
                        translations.add(translation)
                    }
                }
                val beforePrune = translations.size
                pruneExpiredLocked(System.currentTimeMillis())

                // Load presets
                val presetsRaw = prefs(context).getString(KEY_PRESETS, "[]") ?: "[]"
                val presetsArr = JSONArray(presetsRaw)
                for (i in 0 until presetsArr.length()) {
                    val obj = presetsArr.optJSONObject(i) ?: continue
                    val phrasesArr = obj.optJSONArray("phrases") ?: JSONArray()
                    val phrases = (0 until phrasesArr.length()).mapNotNull {
                        phrasesArr.optString(it)
                    }
                    val preset = TranslationPreset(
                        id = obj.optString("id", ""),
                        name = obj.optString("name", ""),
                        sourceLanguage = obj.optString("sourceLanguage", "en"),
                        targetLanguage = obj.optString("targetLanguage", "es"),
                        phrases = phrases,
                    )
                    if (preset.id.isNotBlank()) {
                        presets.add(preset)
                    }
                }

                Log.i(TAG, "Loaded ${translations.size} translations and ${presets.size} presets")
                if (translations.size != beforePrune) persistInternal(context, 500)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load translations", e)
            }
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun pruneExpiredLocked(nowMs: Long) {
        val cutoff = nowMs - TRANSLATION_RETENTION_MS
        translations.removeAll { it.timestampMs <= 0L || it.timestampMs < cutoff }
    }

    private companion object {
        const val TRANSLATION_RETENTION_MS = 24L * 60L * 60L * 1_000L
    }
}

package com.ad_glasses.plugins.handsfreetranslator

import android.content.Context
import com.ad_glasses.ai.orchestrator.AssistantCapabilityRuntimeEvents

object HandsFreeTranslatorPreferences {
    private const val PREFS = "hands_free_translator_prefs"

    private const val KEY_ENABLED = "enabled"
    private const val KEY_SOURCE_LANGUAGE = "source_language"
    private const val KEY_TARGET_LANGUAGE = "target_language"
    private const val KEY_TRANSLATION_MODE = "translation_mode"
    private const val KEY_AUTO_DETECT = "auto_detect"
    private const val KEY_SPEAK_TRANSLATION = "speak_translation"
    private const val KEY_SHOW_CONFIDENCE = "show_confidence"
    private const val KEY_MAX_HISTORY = "max_history"
    private const val KEY_CLOUD_MODEL_ID = "cloud_model_id"
    private const val KEY_CUSTOM_PROMPT = "custom_prompt"
    private const val MAX_CUSTOM_PROMPT_CHARS = 1_000

    private const val DEFAULT_ENABLED = false
    private const val DEFAULT_SOURCE_LANGUAGE = "en"
    private const val DEFAULT_TARGET_LANGUAGE = "es"
    private const val DEFAULT_TRANSLATION_MODE = "real_time"
    private const val DEFAULT_AUTO_DETECT = true
    private const val DEFAULT_SPEAK_TRANSLATION = true
    private const val DEFAULT_SHOW_CONFIDENCE = false
    private const val DEFAULT_MAX_HISTORY = 100
    private const val DEFAULT_CLOUD_MODEL_ID = "deepseek/deepseek-v4-flash"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, DEFAULT_ENABLED)

    fun setEnabled(context: Context, enabled: Boolean) {
        val changed = isEnabled(context) != enabled
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (changed) AssistantCapabilityRuntimeEvents.notifyChanged()
    }

    fun getSourceLanguage(context: Context): String =
        prefs(context).getString(KEY_SOURCE_LANGUAGE, DEFAULT_SOURCE_LANGUAGE).orEmpty()

    fun setSourceLanguage(context: Context, language: String) {
        prefs(context).edit().putString(KEY_SOURCE_LANGUAGE, language).apply()
    }

    fun getTargetLanguage(context: Context): String =
        prefs(context).getString(KEY_TARGET_LANGUAGE, DEFAULT_TARGET_LANGUAGE).orEmpty()

    fun setTargetLanguage(context: Context, language: String) {
        prefs(context).edit().putString(KEY_TARGET_LANGUAGE, language).apply()
    }

    fun getTranslationMode(context: Context): String =
        prefs(context).getString(KEY_TRANSLATION_MODE, DEFAULT_TRANSLATION_MODE).orEmpty()

    fun setTranslationMode(context: Context, mode: String) {
        prefs(context).edit().putString(KEY_TRANSLATION_MODE, mode).apply()
    }

    fun isAutoDetect(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_DETECT, DEFAULT_AUTO_DETECT)

    fun setAutoDetect(context: Context, autoDetect: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_DETECT, autoDetect).apply()
    }

    fun isSpeakTranslation(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SPEAK_TRANSLATION, DEFAULT_SPEAK_TRANSLATION)

    fun setSpeakTranslation(context: Context, speak: Boolean) {
        prefs(context).edit().putBoolean(KEY_SPEAK_TRANSLATION, speak).apply()
    }

    fun isShowConfidence(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SHOW_CONFIDENCE, DEFAULT_SHOW_CONFIDENCE)

    fun setShowConfidence(context: Context, show: Boolean) {
        prefs(context).edit().putBoolean(KEY_SHOW_CONFIDENCE, show).apply()
    }

    fun getMaxHistory(context: Context): Int =
        prefs(context).getInt(KEY_MAX_HISTORY, DEFAULT_MAX_HISTORY).coerceIn(50, 500)

    fun setMaxHistory(context: Context, count: Int) {
        prefs(context).edit().putInt(KEY_MAX_HISTORY, count.coerceIn(50, 500)).apply()
    }

    fun getCloudModelId(context: Context): String =
        prefs(context).getString(KEY_CLOUD_MODEL_ID, DEFAULT_CLOUD_MODEL_ID).orEmpty()

    fun setCloudModelId(context: Context, modelId: String) {
        prefs(context).edit().putString(KEY_CLOUD_MODEL_ID, modelId).apply()
    }

    fun getCustomPrompt(context: Context): String =
        prefs(context).getString(KEY_CUSTOM_PROMPT, "").orEmpty()
            .take(MAX_CUSTOM_PROMPT_CHARS)

    fun setCustomPrompt(context: Context, prompt: String) {
        prefs(context).edit().putString(KEY_CUSTOM_PROMPT, prompt.trim().take(MAX_CUSTOM_PROMPT_CHARS)).apply()
    }
}

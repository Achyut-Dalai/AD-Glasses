package com.achyut.adglasses.plugins.livecaptioncloud

import android.content.Context

object LiveCaptionCloudPreferences {
    private const val PREFS = "live_caption_cloud_prefs"

    private const val KEY_ENABLED = "enabled"
    private const val KEY_SOURCE_LANGUAGE = "source_language"
    private const val KEY_TARGET_LANGUAGE = "target_language"
    private const val KEY_TRANSLATION_ENABLED = "translation_enabled"
    private const val KEY_FONT_SIZE = "font_size"
    private const val KEY_CAPTION_POSITION = "caption_position"
    private const val KEY_SHOW_CONFIDENCE = "show_confidence"
    private const val KEY_MAX_HISTORY = "max_history"
    private const val KEY_CLOUD_API_MODEL_ID = "cloud_model_id"
    private const val KEY_CUSTOM_PROMPT = "custom_prompt"
    private const val MAX_CUSTOM_PROMPT_CHARS = 1_000

    private const val DEFAULT_ENABLED = false
    private const val DEFAULT_SOURCE_LANGUAGE = "en"
    private const val DEFAULT_TARGET_LANGUAGE = "es"
    private const val DEFAULT_TRANSLATION_ENABLED = false
    private const val DEFAULT_FONT_SIZE = 24
    private const val DEFAULT_CAPTION_POSITION = "bottom"
    private const val DEFAULT_SHOW_CONFIDENCE = false
    private const val DEFAULT_MAX_HISTORY = 100
    private const val DEFAULT_CLOUD_API_MODEL_ID = "deepseek/deepseek-v4-flash"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, DEFAULT_ENABLED)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
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

    fun isTranslationEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_TRANSLATION_ENABLED, DEFAULT_TRANSLATION_ENABLED)

    fun setTranslationEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_TRANSLATION_ENABLED, enabled).apply()
    }

    fun getFontSize(context: Context): Int =
        prefs(context).getInt(KEY_FONT_SIZE, DEFAULT_FONT_SIZE).coerceIn(12, 48)

    fun setFontSize(context: Context, size: Int) {
        prefs(context).edit().putInt(KEY_FONT_SIZE, size.coerceIn(12, 48)).apply()
    }

    fun getCaptionPosition(context: Context): String =
        prefs(context).getString(KEY_CAPTION_POSITION, DEFAULT_CAPTION_POSITION).orEmpty()

    fun setCaptionPosition(context: Context, position: String) {
        prefs(context).edit().putString(KEY_CAPTION_POSITION, position).apply()
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
        prefs(context).getString(KEY_CLOUD_API_MODEL_ID, DEFAULT_CLOUD_API_MODEL_ID).orEmpty()

    fun setCloudModelId(context: Context, modelId: String) {
        prefs(context).edit().putString(KEY_CLOUD_API_MODEL_ID, modelId).apply()
    }

    fun getCustomPrompt(context: Context): String =
        prefs(context).getString(KEY_CUSTOM_PROMPT, "").orEmpty()
            .take(MAX_CUSTOM_PROMPT_CHARS)

    fun setCustomPrompt(context: Context, prompt: String) {
        prefs(context).edit().putString(KEY_CUSTOM_PROMPT, prompt.trim().take(MAX_CUSTOM_PROMPT_CHARS)).apply()
    }
}

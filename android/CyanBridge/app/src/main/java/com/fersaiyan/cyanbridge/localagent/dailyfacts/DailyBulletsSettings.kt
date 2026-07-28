package com.fersaiyan.cyanbridge.localagent.dailyfacts

import android.content.Context

object DailyBulletsSettings {
    private const val PREFS = "daily_bullets_settings"

    private const val KEY_CUSTOM_REVIEW_PROMPT = "custom_review_prompt"
    private const val KEY_CUSTOM_SUMMARY_PROMPT = "custom_summary_prompt"
    private const val KEY_CUSTOM_BULLET_PROMPT = "custom_bullet_prompt"
    private const val KEY_MAX_TOKENS_PER_BULLET = "max_tokens_per_bullet"

    val DEFAULT_BULLET_PROMPT = """
        You summarize one mobile screen OCR event into exactly one bullet.

        The app package is provided below, and the app name may also appear inside the OCR text.

        APP_PACKAGE: ${'$'}{event.packageName}
        EVENT_TIME: ${'$'}{event.time}
        OCR_TEXT:
        ${'$'}{event.text}

        Return JSON only:
        {"skip": false, "bullet": "...", "confidence": 0.0}

        Rules:
        - Keep bullet factual and concise (max 26 words)
        - Preserve concrete details like person names, contact names, topics, or action context when visible
        - If OCR is too noisy or meaningless, set skip=true
        - Do not invent details outside OCR
    """.trimIndent()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getCustomReviewPrompt(context: Context): String {
        return prefs(context).getString(KEY_CUSTOM_REVIEW_PROMPT, "") ?: ""
    }

    fun setCustomReviewPrompt(context: Context, prompt: String) {
        prefs(context).edit().putString(KEY_CUSTOM_REVIEW_PROMPT, prompt).apply()
    }

    fun getCustomSummaryPrompt(context: Context): String {
        return prefs(context).getString(KEY_CUSTOM_SUMMARY_PROMPT, "") ?: ""
    }

    fun setCustomSummaryPrompt(context: Context, prompt: String) {
        prefs(context).edit().putString(KEY_CUSTOM_SUMMARY_PROMPT, prompt).apply()
    }

    fun getCustomBulletPrompt(context: Context): String {
        return prefs(context).getString(KEY_CUSTOM_BULLET_PROMPT, "") ?: ""
    }

    fun getBulletPrompt(context: Context): String =
        getCustomBulletPrompt(context).ifBlank { DEFAULT_BULLET_PROMPT }

    fun setCustomBulletPrompt(context: Context, prompt: String) {
        prefs(context).edit().putString(KEY_CUSTOM_BULLET_PROMPT, prompt).apply()
    }

    fun restoreDefaultBulletPrompt(context: Context) {
        setCustomBulletPrompt(context, DEFAULT_BULLET_PROMPT)
    }

    fun getMaxTokensPerBullet(context: Context): Int {
        return prefs(context).getInt(KEY_MAX_TOKENS_PER_BULLET, 0)
    }

    fun setMaxTokensPerBullet(context: Context, tokens: Int) {
        prefs(context).edit().putInt(KEY_MAX_TOKENS_PER_BULLET, tokens.coerceAtLeast(0)).apply()
    }
}

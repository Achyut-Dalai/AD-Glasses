package com.fersaiyan.cyanbridge.agent

import android.content.Context

/** Model choices for the user's own cloud relay. */
object CloudAiPrefs {
    private const val PREFS_NAME = "cloud_ai_prefs"
    private const val LEGACY_PREFS_NAME = "pro_subscription_ai_prefs"
    private const val KEY_REQUESTS_MODEL = "requests_model"
    private const val KEY_QUESTIONS_MODEL = "questions_model"
    private const val KEY_TASKS_MODEL = "tasks_model"
    private const val DEFAULT_MODEL = "auto"

    private fun normalizeModel(model: String?): String {
        val clean = model.orEmpty().trim()
        if (clean.isBlank()) return DEFAULT_MODEL
        val withoutMultiplier = clean
            .replace(Regex("\\s*\\(\\s*\\d+\\s*x\\s*\\)\\s*$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*\\(\\s*x\\s*\\d+\\s*\\)\\s*$", RegexOption.IGNORE_CASE), "")
            .trim()
        return withoutMultiplier.substringBefore(" · ").trim().ifBlank { DEFAULT_MODEL }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun read(context: Context, key: String): String {
        val current = prefs(context).getString(key, null)
        if (current != null) return normalizeModel(current)
        val legacy = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
            .getString(key, DEFAULT_MODEL)
        return normalizeModel(legacy)
    }

    fun getRequestsModel(context: Context): String = read(context, KEY_REQUESTS_MODEL)
    fun setRequestsModel(context: Context, model: String) =
        prefs(context).edit().putString(KEY_REQUESTS_MODEL, normalizeModel(model)).apply()

    fun getQuestionsModel(context: Context): String = read(context, KEY_QUESTIONS_MODEL)
    fun setQuestionsModel(context: Context, model: String) =
        prefs(context).edit().putString(KEY_QUESTIONS_MODEL, normalizeModel(model)).apply()

    fun getTasksModel(context: Context): String = read(context, KEY_TASKS_MODEL)
    fun setTasksModel(context: Context, model: String) =
        prefs(context).edit().putString(KEY_TASKS_MODEL, normalizeModel(model)).apply()
}

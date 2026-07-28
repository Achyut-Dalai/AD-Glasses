package com.fersaiyan.cyanbridge.ui.localization

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.fersaiyan.cyanbridge.R

enum class AppLanguage(
    val languageTag: String,
    private val selfLabel: String,
) {
    SYSTEM("", ""),
    ENGLISH("en", "English"),
    PORTUGUESE_BRAZIL("pt-BR", "Português (Brasil)"),
    SPANISH("es", "Español"),
    GERMAN("de", "Deutsch"),
    FRENCH("fr", "Français"),
    ITALIAN("it", "Italiano"),
    CHINESE_SIMPLIFIED("zh-CN", "中文（简体）"),
    KOREAN("ko", "한국어"),
    RUSSIAN("ru", "Русский"),
    ;

    fun displayName(context: Context): String = when (this) {
        SYSTEM -> context.getString(R.string.language_system_default)
        else -> selfLabel
    }

    companion object {
        fun fromStored(value: String?): AppLanguage {
            return entries.firstOrNull { it.name == value } ?: SYSTEM
        }
    }
}

object AppLanguagePreferences {
    private const val PREFS = "app_language"
    private const val KEY_LANGUAGE = "selected_language"

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun selected(context: Context): AppLanguage {
        return AppLanguage.fromStored(preferences(context).getString(KEY_LANGUAGE, null))
    }

    fun hasUserSelectedLanguage(context: Context): Boolean =
        preferences(context).contains(KEY_LANGUAGE)

    fun applyStoredLocale(context: Context) {
        applyLocale(selected(context))
    }

    fun select(context: Context, language: AppLanguage) {
        preferences(context).edit().putString(KEY_LANGUAGE, language.name).apply()
        applyLocale(language)
    }

    private fun applyLocale(language: AppLanguage) {
        val locales = if (language == AppLanguage.SYSTEM) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(language.languageTag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }
}

package com.fersaiyan.cyanbridge.localmodels.tts

import java.util.Locale

/**
 * Sanitizes and normalizes model text output specifically for Text-To-Speech playback,
 * without altering or mutating the raw visible text displayed in the user UI.
 */
object StreamingTextNormalizer {

    private val MARKDOWN_HEADER_REGEX = Regex("(?m)^#{1,6}\\s+")
    private val MARKDOWN_BOLD_ITALIC_REGEX = Regex("(\\*\\*|\\*|__|_|~~)")
    private val BULLET_POINT_REGEX = Regex("(?m)^\\s*[-*+]\\s+")
    private val NUMBERED_LIST_REGEX = Regex("(?m)^\\s*\\d+\\.\\s+")
    private val CODE_BLOCK_REGEX = Regex("```[\\s\\S]*?```")
    private val INLINE_CODE_REGEX = Regex("`([^`]+)`")
    private val THINK_TAG_REGEX = Regex("(?i)<think>[\\s\\S]*?</think>")
    private val CONTROL_TOKEN_REGEX = Regex("<\\|[^|]+\\|>")
    private val URL_REGEX = Regex("https?://\\S+|www\\.\\S+")
    private val EMAIL_REGEX = Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
    private val MULTIPLE_WHITESPACE_REGEX = Regex("\\s+")
    private val REPEATED_PUNCTUATION_REGEX = Regex("([.!?,;:])\\1+")

    /**
     * Converts a raw model output chunk into a speech-friendly string for TTS.
     */
    fun normalizeForSpeech(rawText: String, languageTag: String? = null): String {
        if (rawText.isBlank()) return ""

        var result = rawText

        // 1. Remove reasoning tags and control tokens
        result = THINK_TAG_REGEX.replace(result, "")
        result = CONTROL_TOKEN_REGEX.replace(result, "")

        // 2. Remove code blocks or replace with localized note
        result = CODE_BLOCK_REGEX.replace(result) {
            codeBlockPlaceholder(languageTag)
        }
        result = INLINE_CODE_REGEX.replace(result, "$1")

        // 3. Normalize URLs and Email addresses
        result = URL_REGEX.replace(result) {
            urlPlaceholder(languageTag)
        }
        result = EMAIL_REGEX.replace(result) {
            emailPlaceholder(languageTag)
        }

        // 4. Strip Markdown formatting
        result = MARKDOWN_HEADER_REGEX.replace(result, "")
        result = MARKDOWN_BOLD_ITALIC_REGEX.replace(result, "")
        result = BULLET_POINT_REGEX.replace(result, "")
        result = NUMBERED_LIST_REGEX.replace(result, "")

        // 5. Clean up repeated punctuation & whitespace
        result = REPEATED_PUNCTUATION_REGEX.replace(result, "$1")
        result = MULTIPLE_WHITESPACE_REGEX.replace(result, " ").trim()

        return result
    }

    private fun urlPlaceholder(languageTag: String?): String {
        val lang = languageTag?.let { Locale.forLanguageTag(it).language.lowercase(Locale.ROOT) } ?: ""
        return when (lang) {
            "pt" -> " um link está incluído "
            "es" -> " un enlace está incluido "
            "de" -> " ein Link ist enthalten "
            "fr" -> " un lien est inclus "
            "it" -> " un link è incluso "
            "zh" -> " 包含链接 "
            "ru" -> " ссылка включена "
            else -> " a link is included "
        }
    }

    private fun codeBlockPlaceholder(languageTag: String?): String {
        val lang = languageTag?.let { Locale.forLanguageTag(it).language.lowercase(Locale.ROOT) } ?: ""
        return when (lang) {
            "pt" -> " [bloco de código no ecrã] "
            "es" -> " [bloque de código en pantalla] "
            "de" -> " [Code-Block auf dem Bildschirm] "
            "fr" -> " [bloc de code à l'écran] "
            "it" -> " [blocco di codice sullo schermo] "
            "zh" -> " [屏幕上显示的代码块] "
            "ru" -> " [блок кода на экране] "
            else -> " [code block shown on screen] "
        }
    }

    private fun emailPlaceholder(languageTag: String?): String {
        val lang = languageTag?.let { Locale.forLanguageTag(it).language.lowercase(Locale.ROOT) } ?: ""
        return when (lang) {
            "pt" -> " endereço de e-mail incluído "
            "es" -> " dirección de correo incluida "
            "de" -> " E-Mail-Adresse enthalten "
            "fr" -> " adresse e-mail incluse "
            "it" -> " indirizzo e-mail incluso "
            "zh" -> " 包含电子邮件地址 "
            "ru" -> " указан адрес электронной почты "
            else -> " an email address is included "
        }
    }
}

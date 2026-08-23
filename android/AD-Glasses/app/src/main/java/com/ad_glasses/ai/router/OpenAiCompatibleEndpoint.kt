package com.ad_glasses.ai.router

/** Normalizes user-supplied OpenAI-compatible endpoints and Bearer credentials. */
object OpenAiCompatibleEndpoint {
    private const val CHAT_COMPLETIONS_SUFFIX = "/chat/completions"
    private const val MODELS_SUFFIX = "/models"

    fun normalizeBaseUrl(raw: String): String {
        var value = raw.trim().trimEnd('/')
        value = stripSuffix(value, CHAT_COMPLETIONS_SUFFIX)
        value = stripSuffix(value, MODELS_SUFFIX)
        return value.trimEnd('/')
    }

    fun normalizeBearerCredential(raw: String): String {
        val value = raw.trim()
        return if (value.startsWith("Bearer ", ignoreCase = true)) {
            value.substringAfter(' ').trim()
        } else {
            value
        }
    }

    fun authorizationHeader(rawCredential: String): String {
        val credential = normalizeBearerCredential(rawCredential)
        require(credential.isNotBlank()) { "API key is required." }
        return "Bearer $credential"
    }

    fun chatCompletionsUrl(baseUrl: String): String =
        "${normalizeBaseUrl(baseUrl)}$CHAT_COMPLETIONS_SUFFIX"

    fun modelsUrl(baseUrl: String): String =
        "${normalizeBaseUrl(baseUrl)}$MODELS_SUFFIX"

    private fun stripSuffix(value: String, suffix: String): String =
        if (value.endsWith(suffix, ignoreCase = true)) value.dropLast(suffix.length) else value
}

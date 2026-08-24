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

    /**
     * Accept an API key by itself or a commonly pasted Authorization header, but persist only the
     * credential. This prevents accidental `Bearer Bearer ...` requests and quoted-key 401s.
     */
    fun normalizeBearerCredential(raw: String): String {
        var value = raw.trim()
        if (value.startsWith("Authorization:", ignoreCase = true)) {
            value = value.substringAfter(':').trim()
        }
        if (value.startsWith("Bearer ", ignoreCase = true)) {
            value = value.substringAfter(' ').trim()
        }
        if (value.length >= 2) {
            val first = value.first()
            val last = value.last()
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                value = value.substring(1, value.lastIndex).trim()
            }
        }
        return value
    }

    fun authorizationHeader(rawCredential: String): String {
        val credential = normalizeBearerCredential(rawCredential)
        require(credential.isNotBlank()) { "API key is required." }
        return "Bearer $credential"
    }

    fun chatCompletionsUrl(baseUrl: String): String {
        val base = normalizeBaseUrl(baseUrl)
        require(base.startsWith("https://")) { "OpenAI-compatible API base URL must use HTTPS." }
        return "$base$CHAT_COMPLETIONS_SUFFIX"
    }

    fun modelsUrl(baseUrl: String): String {
        val base = normalizeBaseUrl(baseUrl)
        require(base.startsWith("https://")) { "OpenAI-compatible API base URL must use HTTPS." }
        return "$base$MODELS_SUFFIX"
    }

    private fun stripSuffix(value: String, suffix: String): String =
        if (value.endsWith(suffix, ignoreCase = true)) value.dropLast(suffix.length) else value
}

package com.ad_glasses.ai.router

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

/** Direct Cloud AI transport resolved through the active encrypted profile. */
object ApiTokenClient {
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 120_000
    private const val MAX_RETRIES = 3
    private const val BASE_DELAY_MS = 1_000L

    suspend fun chat(
        context: Context,
        messages: List<Map<String, String>>,
        imagePaths: List<String> = emptyList(),
        audioPath: String? = null,
        maxTokens: Int = 2048,
        webRequested: Boolean = false,
    ): Result<String> = runCatching {
        val (profile, apiKey) = AiProviderPrefs.activeProfileWithKey(context)
        require(profile.model.isNotBlank()) { "${profile.name} does not have a model selected." }
        require(profile.baseUrl.isNotBlank()) { "${profile.name} does not have an API base URL." }

        val useNativeWeb = webRequested && profile.webAvailable && imagePaths.isEmpty() && audioPath.isNullOrBlank()
        val text = when {
            useNativeWeb && profile.provider == ApiProvider.GOOGLE ->
                retry { postGeminiGrounded(profile, apiKey, messages, maxTokens) }
                    .let(::extractGeminiText)
            useNativeWeb && profile.provider == ApiProvider.OPENAI ->
                retry { postOpenAiResponses(profile, apiKey, messages, maxTokens) }
                    .let(::extractOpenAiResponseText)
            else -> {
                val payloadMessages = buildOpenAiMessages(messages, imagePaths, audioPath)
                val payload = JSONObject()
                    .put("model", profile.model)
                    .put("messages", payloadMessages)
                    .put("max_tokens", maxTokens)
                if (useNativeWeb && profile.provider == ApiProvider.OPENROUTER) {
                    payload.put(
                        "tools",
                        JSONArray().put(JSONObject().put("type", "openrouter:web_search")),
                    )
                }
                retry {
                    postJson(
                        url = profile.baseUrl.trimEnd('/') + "/chat/completions",
                        apiKey = apiKey,
                        payload = payload,
                    )
                }.let(::extractChatCompletionText)
            }
        }
        text.ifBlank { throw IllegalStateException("${profile.name} returned an empty response") }
    }

    suspend fun image(
        context: Context,
        systemPrompt: String,
        userPrompt: String,
        imagePath: String,
        maxTokens: Int = 1200,
    ): Result<String> {
        val messages = buildList {
            if (systemPrompt.isNotBlank()) add(mapOf("role" to "system", "content" to systemPrompt))
            add(mapOf("role" to "user", "content" to userPrompt))
        }
        return chat(
            context = context,
            messages = messages,
            imagePaths = listOf(imagePath),
            maxTokens = maxTokens,
        )
    }

    /** Fetch models without ever returning the API key to UI state. */
    suspend fun discoverModels(
        context: Context,
        provider: ApiProvider,
        baseUrl: String,
        profileId: String? = null,
        apiKeyReplacement: String? = null,
    ): Result<List<String>> = runCatching {
        val key = apiKeyReplacement?.trim().orEmpty().ifBlank {
            profileId?.let { AiProviderPrefs.apiKeyForRequest(context, it) }.orEmpty()
        }
        require(key.isNotBlank()) { "Enter an API key or use a profile that already has one saved." }
        val cleanBase = baseUrl.trim().trimEnd('/')
        require(cleanBase.startsWith("https://") || cleanBase.startsWith("http://")) {
            "API base URL must start with http:// or https://"
        }

        val response = if (provider == ApiProvider.GOOGLE) {
            val nativeBase = cleanBase.substringBeforeLast("/openai", cleanBase)
            getJson("$nativeBase/models?key=${URLEncoder.encode(key, "UTF-8")}", apiKey = null)
        } else {
            getJson("$cleanBase/models", apiKey = key)
        }

        val models = linkedSetOf<String>()
        response.optJSONArray("data")?.let { data ->
            for (index in 0 until data.length()) {
                data.optJSONObject(index)?.optString("id")?.trim()?.takeIf { it.isNotBlank() }?.let(models::add)
            }
        }
        response.optJSONArray("models")?.let { data ->
            for (index in 0 until data.length()) {
                val item = data.optJSONObject(index) ?: continue
                val raw = item.optString("name").trim().removePrefix("models/")
                if (raw.isNotBlank()) models += raw
            }
        }
        if (models.isEmpty()) throw IllegalStateException("The provider returned no selectable models.")
        models.sortedWith(compareBy<String> { !it.contains("flash", ignoreCase = true) }.thenBy { it.lowercase() })
    }

    private fun buildOpenAiMessages(
        messages: List<Map<String, String>>,
        imagePaths: List<String>,
        audioPath: String?,
    ): JSONArray {
        val payloadMessages = JSONArray()
        messages.forEachIndexed { index, message ->
            val role = message["role"]?.trim()?.lowercase().orEmpty().ifBlank { "user" }
            val text = message["content"].orEmpty()
            val isLastUser = index == messages.lastIndex && role == "user"
            if (isLastUser && (imagePaths.isNotEmpty() || !audioPath.isNullOrBlank())) {
                payloadMessages.put(
                    JSONObject()
                        .put("role", role)
                        .put("content", mediaContent(text, imagePaths, audioPath)),
                )
            } else {
                payloadMessages.put(JSONObject().put("role", role).put("content", text))
            }
        }
        return payloadMessages
    }

    private fun postOpenAiResponses(
        profile: CloudAiProfile,
        apiKey: String,
        messages: List<Map<String, String>>,
        maxTokens: Int,
    ): JSONObject {
        val input = JSONArray()
        messages.forEach { message ->
            val role = message["role"]?.trim()?.lowercase().orEmpty().ifBlank { "user" }
            input.put(
                JSONObject()
                    .put("role", if (role == "assistant") "assistant" else if (role == "system") "system" else "user")
                    .put("content", message["content"].orEmpty()),
            )
        }
        val payload = JSONObject()
            .put("model", profile.model)
            .put("input", input)
            .put("max_output_tokens", maxTokens)
            .put("tools", JSONArray().put(JSONObject().put("type", "web_search")))
        return postJson(profile.baseUrl.trimEnd('/') + "/responses", apiKey, payload)
    }

    private fun postGeminiGrounded(
        profile: CloudAiProfile,
        apiKey: String,
        messages: List<Map<String, String>>,
        maxTokens: Int,
    ): JSONObject {
        val systemParts = JSONArray()
        val contents = JSONArray()
        messages.forEach { message ->
            val role = message["role"]?.trim()?.lowercase().orEmpty()
            val text = message["content"].orEmpty()
            if (role == "system") {
                if (text.isNotBlank()) systemParts.put(JSONObject().put("text", text))
            } else {
                contents.put(
                    JSONObject()
                        .put("role", if (role == "assistant") "model" else "user")
                        .put("parts", JSONArray().put(JSONObject().put("text", text))),
                )
            }
        }
        val payload = JSONObject()
            .put("contents", contents)
            .put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
            .put("generationConfig", JSONObject().put("maxOutputTokens", maxTokens))
        if (systemParts.length() > 0) {
            payload.put("systemInstruction", JSONObject().put("parts", systemParts))
        }
        val nativeBase = profile.baseUrl.trimEnd('/').substringBeforeLast("/openai", profile.baseUrl.trimEnd('/'))
        val endpoint = "$nativeBase/models/${URLEncoder.encode(profile.model, "UTF-8")}:generateContent?key=${URLEncoder.encode(apiKey, "UTF-8")}"
        return postJson(endpoint, apiKey = null, payload = payload)
    }

    private fun mediaContent(text: String, imagePaths: List<String>, audioPath: String?): JSONArray {
        val content = JSONArray()
        content.put(JSONObject().put("type", "text").put("text", text))
        imagePaths.forEach { path ->
            val file = File(path)
            require(file.isFile) { "Image file not found: $path" }
            val mime = when (file.extension.lowercase()) {
                "png" -> "image/png"
                "webp" -> "image/webp"
                else -> "image/jpeg"
            }
            val data = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
            content.put(
                JSONObject()
                    .put("type", "image_url")
                    .put("image_url", JSONObject().put("url", "data:$mime;base64,$data")),
            )
        }
        audioPath?.takeIf { it.isNotBlank() }?.let { path ->
            val file = File(path)
            require(file.isFile) { "Audio file not found: $path" }
            val format = when (file.extension.lowercase()) {
                "mp3", "mpeg" -> "mp3"
                else -> "wav"
            }
            val data = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
            content.put(
                JSONObject()
                    .put("type", "input_audio")
                    .put("input_audio", JSONObject().put("data", data).put("format", format)),
            )
        }
        return content
    }

    private suspend fun retry(block: () -> JSONObject): JSONObject {
        var last: Throwable? = null
        repeat(MAX_RETRIES) { attempt ->
            try {
                return block()
            } catch (error: Throwable) {
                last = error
                val text = error.message.orEmpty()
                val retryable = text.contains("HTTP 429") || Regex("HTTP 5\\d\\d").containsMatchIn(text)
                if (!retryable || attempt == MAX_RETRIES - 1) throw error
                delay(BASE_DELAY_MS * (1L shl attempt))
            }
        }
        throw last ?: IllegalStateException("API request failed")
    }

    private fun postJson(
        url: String,
        apiKey: String?,
        payload: JSONObject,
    ): JSONObject = requestJson("POST", url, apiKey, payload)

    private fun getJson(url: String, apiKey: String?): JSONObject = requestJson("GET", url, apiKey, null)

    private fun requestJson(
        method: String,
        url: String,
        apiKey: String?,
        payload: JSONObject?,
    ): JSONObject {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.setRequestProperty("Accept", "application/json")
            if (!apiKey.isNullOrBlank()) conn.setRequestProperty("Authorization", "Bearer $apiKey")
            if (payload != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = BufferedReader(InputStreamReader(stream ?: conn.inputStream)).use { it.readText() }
            if (code !in 200..299) throw IllegalStateException("API HTTP $code: $body")
            return JSONObject(body)
        } finally {
            conn.disconnect()
        }
    }

    private fun extractChatCompletionText(response: JSONObject): String {
        val message = response.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?: return ""
        val raw = message.opt("content") ?: return ""
        if (raw is String) return raw.trim()
        if (raw is JSONArray) {
            return buildString {
                for (index in 0 until raw.length()) {
                    val part = raw.opt(index)
                    val text = when (part) {
                        is String -> part
                        is JSONObject -> part.optString("text")
                        else -> ""
                    }.trim()
                    if (text.isNotBlank()) {
                        if (isNotEmpty()) append('\n')
                        append(text)
                    }
                }
            }.trim()
        }
        return raw.toString().trim()
    }

    private fun extractOpenAiResponseText(response: JSONObject): String {
        response.optString("output_text").trim().takeIf { it.isNotBlank() }?.let { return it }
        val output = response.optJSONArray("output") ?: return ""
        return buildString {
            for (index in 0 until output.length()) {
                val item = output.optJSONObject(index) ?: continue
                val content = item.optJSONArray("content") ?: continue
                for (partIndex in 0 until content.length()) {
                    val part = content.optJSONObject(partIndex) ?: continue
                    val text = part.optString("text").trim()
                    if (text.isNotBlank()) {
                        if (isNotEmpty()) append('\n')
                        append(text)
                    }
                }
            }
        }.trim()
    }

    private fun extractGeminiText(response: JSONObject): String {
        val parts = response.optJSONArray("candidates")
            ?.optJSONObject(0)
            ?.optJSONObject("content")
            ?.optJSONArray("parts")
            ?: return ""
        return buildString {
            for (index in 0 until parts.length()) {
                val text = parts.optJSONObject(index)?.optString("text")?.trim().orEmpty()
                if (text.isNotBlank()) {
                    if (isNotEmpty()) append('\n')
                    append(text)
                }
            }
        }.trim()
    }
}

/** Chat/voice/Lens inference is Cloud-only. Provider failures never silently switch engines. */
object AiAssistantRouter {
    interface ChatStreamCallbacks {
        fun onStatus(status: String) {}
        fun onToken(token: String) {}
    }

    suspend fun chatReply(
        context: Context,
        chatId: String,
        userPrompt: String,
        messages: List<Map<String, String>>,
    ): String = chatReplyStreaming(context, chatId, userPrompt, messages, emptyList(), null, null)

    suspend fun chatReplyStreaming(
        context: Context,
        chatId: String,
        userPrompt: String,
        messages: List<Map<String, String>>,
        imagePaths: List<String>,
        audioPath: String?,
        callbacks: ChatStreamCallbacks?,
        webRequested: Boolean = false,
    ): String {
        val profile = AiProviderPrefs.getActiveProfile(context)
            ?: return "Cloud AI is not configured. Add a provider profile in Device Center."
        callbacks?.onStatus("Using ${profile.name} · ${profile.model}")
        return ApiTokenClient.chat(
            context = context,
            messages = messages,
            imagePaths = imagePaths,
            audioPath = audioPath,
            webRequested = webRequested,
        ).getOrElse {
            "Cloud AI unavailable (${it.message ?: it::class.java.simpleName})."
        }
    }

    suspend fun textReply(context: Context, prompt: String, webRequested: Boolean = false): String {
        val messages = listOf(mapOf("role" to "user", "content" to prompt))
        return ApiTokenClient.chat(context, messages, webRequested = webRequested).getOrElse {
            "Cloud AI unavailable (${it.message ?: it::class.java.simpleName})."
        }
    }

    suspend fun cancelCurrentGeneration(context: Context) = Unit
}

package com.ad_glasses.ai.router

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Base64
import com.ad_glasses.localmodels.provider.LocalModelsProvider
import com.ad_glasses.localmodels.storage.LocalModelStorageRepository
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/** One direct HTTP implementation for OpenAI-compatible provider APIs. */
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
        modelOverride: String? = null,
    ): Result<String> = runCatching {
        val provider = AiProviderPrefs.getApiProvider(context)
        val apiKey = AiProviderPrefs.getApiKey(context, provider)
        require(apiKey.isNotBlank()) { "${provider.label} API key is not configured" }
        val model = modelOverride?.trim()?.takeIf { it.isNotBlank() }
            ?: AiProviderPrefs.getModel(context, provider)
        require(model.isNotBlank()) { "${provider.label} model is not configured" }

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

        val payload = JSONObject()
            .put("model", model)
            .put("messages", payloadMessages)
            .put("max_tokens", maxTokens)

        val endpoint = provider.baseUrl.trimEnd('/') + "/chat/completions"
        retry { postJson(endpoint, apiKey, payload) }
            .let(::extractText)
            .ifBlank { throw IllegalStateException("${provider.label} returned an empty response") }
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

    private fun postJson(url: String, apiKey: String, payload: JSONObject): JSONObject {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = BufferedReader(InputStreamReader(stream ?: conn.inputStream)).use { it.readText() }
            if (code !in 200..299) throw IllegalStateException("API HTTP $code: $body")
            return JSONObject(body)
        } finally {
            conn.disconnect()
        }
    }

    private fun extractText(response: JSONObject): String {
        val message = response.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?: return ""
        val raw = message.opt("content") ?: return ""
        if (raw is String) return raw.trim()
        if (raw is JSONArray) {
            return buildString {
                for (i in 0 until raw.length()) {
                    val part = raw.opt(i)
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
}

/** Chat/voice/Lens inference has only AD-owned Cloud or Local routes. */
object AiAssistantRouter {
    interface ChatStreamCallbacks {
        fun onStatus(status: String) {}
        fun onToken(token: String) {}
    }

    private val localModelsProvider = LocalModelsProvider()

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
    ): String {
        return when (AiProviderPrefs.getProvider(context)) {
            AiProviderType.CLOUD_API -> {
                if (shouldUseOfflineTextFallback(context, imagePaths, audioPath)) {
                    callbacks?.onStatus("Offline — using Local AI")
                    localModelsProvider.streamChat(
                        context = context,
                        messages = messages,
                        onStatus = { callbacks?.onStatus(it) },
                        onToken = { callbacks?.onToken(it) },
                    )
                } else {
                    callbacks?.onStatus("Using ${AiProviderPrefs.getApiProvider(context).label} Cloud API")
                    ApiTokenClient.chat(context, messages, imagePaths, audioPath).getOrElse {
                        "Cloud AI unavailable (${it.message ?: it::class.java.simpleName})."
                    }
                }
            }
            AiProviderType.LOCAL_MODELS -> localModelsProvider.streamChat(
                context = context,
                messages = messages,
                onStatus = { callbacks?.onStatus(it) },
                onToken = { callbacks?.onToken(it) },
                imagePaths = imagePaths,
                audioPath = audioPath,
            )
        }
    }

    suspend fun textReply(context: Context, prompt: String): String {
        val messages = listOf(mapOf("role" to "user", "content" to prompt))
        return when (AiProviderPrefs.getProvider(context)) {
            AiProviderType.CLOUD_API -> {
                if (shouldUseOfflineTextFallback(context, emptyList(), null)) {
                    localModelsProvider.streamChat(context = context, messages = messages)
                } else {
                    ApiTokenClient.chat(context, messages).getOrElse {
                        "Cloud AI unavailable (${it.message ?: it::class.java.simpleName})."
                    }
                }
            }
            AiProviderType.LOCAL_MODELS -> localModelsProvider.streamChat(context = context, messages = messages)
        }
    }

    suspend fun cancelCurrentGeneration(context: Context) {
        if (AiProviderPrefs.getProvider(context) == AiProviderType.LOCAL_MODELS || !hasValidatedInternet(context)) {
            localModelsProvider.cancelGeneration()
        }
    }

    /**
     * Local is an automatic fallback only for confirmed-offline text turns. Provider/auth/server
     * failures do not silently change routes, and media turns remain on their selected modality path.
     */
    private fun shouldUseOfflineTextFallback(
        context: Context,
        imagePaths: List<String>,
        audioPath: String?,
    ): Boolean {
        if (imagePaths.isNotEmpty() || !audioPath.isNullOrBlank()) return false
        if (hasValidatedInternet(context)) return false
        return LocalModelStorageRepository.resolveSelectedModel(context) != null
    }

    private fun hasValidatedInternet(context: Context): Boolean {
        val connectivity = context.applicationContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = connectivity.activeNetwork ?: return false
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}

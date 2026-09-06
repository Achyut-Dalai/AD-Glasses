package com.adglasses.app.core.assistant

import com.adglasses.app.core.model.ChatMessage
import com.adglasses.app.core.model.MessageRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

class CloudAIClient {
    suspend fun response(
        messages: List<ChatMessage>,
        profile: AIProfile,
        credential: String,
        groundingContext: String? = null,
    ): String = withContext(Dispatchers.IO) {
        require(messages.any { it.role == MessageRole.User }) { "Ask AD something first" }
        val instruction = systemInstruction(groundingContext)
        when (profile.provider) {
            AIProviderKind.OpenAI -> openAI(messages, profile, credential, instruction)
            AIProviderKind.Google -> gemini(messages, profile, credential, instruction)
            AIProviderKind.DeepSeek,
            AIProviderKind.OpenRouter,
            AIProviderKind.Groq,
            AIProviderKind.Custom -> compatible(messages, profile, credential, instruction)
        }
    }

    private fun openAI(messages: List<ChatMessage>, profile: AIProfile, credential: String, instruction: String): String {
        val input = JSONArray()
        messages.forEach { message ->
            input.put(JSONObject().apply {
                put("role", wireRole(message.role))
                put("content", message.text)
            })
        }
        val payload = JSONObject().apply {
            put("model", profile.model)
            put("instructions", instruction)
            put("input", input)
            put("max_output_tokens", 4_096)
        }
        val root = post(
            endpoint(profile.baseUrl, "/responses"),
            headers = mapOf("Authorization" to "Bearer $credential"),
            payload = payload,
            label = "OpenAI",
        )
        root.optString("output_text").trim().takeIf { it.isNotEmpty() }?.let { return it }
        val output = root.optJSONArray("output") ?: error("OpenAI returned a response AD could not read")
        val text = buildString {
            for (outputIndex in 0 until output.length()) {
                val content = output.optJSONObject(outputIndex)?.optJSONArray("content") ?: continue
                for (contentIndex in 0 until content.length()) {
                    val part = content.optJSONObject(contentIndex) ?: continue
                    if (part.optString("type") == "output_text") append(part.optString("text"))
                }
            }
        }.trim()
        require(text.isNotEmpty()) { "OpenAI returned an empty response" }
        return text
    }

    private fun gemini(messages: List<ChatMessage>, profile: AIProfile, credential: String, instruction: String): String {
        val model = AIProfileStore.normalizeModel(profile.model, AIProviderKind.Google)
        val contents = JSONArray()
        messages.forEach { message ->
            contents.put(JSONObject().apply {
                put("role", if (message.role == MessageRole.Assistant) "model" else "user")
                put("parts", JSONArray().put(JSONObject().put("text", message.text)))
            })
        }
        val payload = JSONObject().apply {
            put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", instruction))))
            put("contents", contents)
            put("generationConfig", JSONObject().put("maxOutputTokens", 4_096))
        }
        val root = post(
            endpoint(profile.baseUrl, "/models/$model:generateContent"),
            headers = mapOf("x-goog-api-key" to credential),
            payload = payload,
            label = "Google Gemini",
        )
        val candidates = root.optJSONArray("candidates") ?: error("Gemini returned a response AD could not read")
        val parts = candidates.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
            ?: error("Gemini returned a response AD could not read")
        val text = buildString {
            for (index in 0 until parts.length()) append(parts.optJSONObject(index)?.optString("text").orEmpty())
        }.trim()
        require(text.isNotEmpty()) { "Gemini returned an empty response" }
        return text
    }

    private fun compatible(messages: List<ChatMessage>, profile: AIProfile, credential: String, instruction: String): String {
        val wireMessages = JSONArray().put(
            JSONObject().put("role", "system").put("content", instruction)
        )
        messages.forEach { message ->
            wireMessages.put(JSONObject().put("role", wireRole(message.role)).put("content", message.text))
        }
        val payload = JSONObject().apply {
            put("model", profile.model)
            put("messages", wireMessages)
            if (profile.provider == AIProviderKind.Groq) {
                put("max_completion_tokens", 4_096)
                val lower = profile.model.lowercase()
                if ("gpt-oss" in lower) {
                    put("reasoning_effort", "low")
                    put("include_reasoning", false)
                } else if ("qwen3.6" in lower || "qwen-3.6" in lower) {
                    put("reasoning_effort", "none")
                    put("reasoning_format", "hidden")
                }
            } else {
                put("max_tokens", 4_096)
            }
        }
        val root = post(
            endpoint(profile.baseUrl, "/chat/completions"),
            headers = mapOf("Authorization" to "Bearer $credential"),
            payload = payload,
            label = profile.provider.displayName,
        )
        val message = root.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
            ?: error("${profile.provider.displayName} returned a response AD could not read")
        val content = message.opt("content")
        val text = when (content) {
            is String -> content
            is JSONArray -> buildString {
                for (index in 0 until content.length()) append(content.optJSONObject(index)?.optString("text").orEmpty())
            }
            else -> ""
        }.trim()
        require(text.isNotEmpty()) { "${profile.provider.displayName} returned an empty response" }
        return text
    }

    private fun post(
        url: URL,
        headers: Map<String, String>,
        payload: JSONObject,
        label: String,
    ): JSONObject {
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 75_000
            doOutput = true
            instanceFollowRedirects = false
            useCaches = false
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            headers.forEach { (key, value) -> setRequestProperty(key, value) }
        }
        return try {
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val input = if (status in 200..299) connection.inputStream else connection.errorStream
            val bytes = input?.use { readBounded(it, 2_000_000) } ?: byteArrayOf()
            val text = bytes.toString(Charsets.UTF_8)
            if (status !in 200..299) {
                val message = runCatching {
                    val root = JSONObject(text)
                    root.optJSONObject("error")?.optString("message")
                        ?.takeIf { it.isNotBlank() }
                        ?: root.optString("message").takeIf { it.isNotBlank() }
                }.getOrNull()
                error(message ?: "$label returned HTTP $status")
            }
            require(text.isNotBlank()) { "$label returned an empty response" }
            JSONObject(text)
        } finally {
            connection.disconnect()
        }
    }

    private fun readBounded(input: java.io.InputStream, maximumBytes: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= maximumBytes) { "Cloud AI response exceeded the bounded size" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun endpoint(base: String, suffix: String): URL {
        val normalizedBase = base.trim().trimEnd('/')
        require(normalizedBase.startsWith("https://", ignoreCase = true)) { "Cloud AI endpoint must use HTTPS" }
        return URL(normalizedBase + suffix)
    }

    private fun systemInstruction(groundingContext: String?): String {
        val context = groundingContext?.trim()?.take(12_000).orEmpty()
        return if (context.isEmpty()) BASE_SYSTEM_INSTRUCTION else "$BASE_SYSTEM_INSTRUCTION\n\nPhone context supplied by AD Glasses for this request:\n$context\nUse only this supplied context for claims about the user's phone state."
    }

    private fun wireRole(role: MessageRole): String = when (role) {
        MessageRole.User -> "user"
        MessageRole.Assistant -> "assistant"
    }

    companion object {
        private const val BASE_SYSTEM_INSTRUCTION =
            "You are AD, the assistant inside AD Glasses. Give direct, useful answers. " +
                "Prefer concise spoken-friendly wording unless the user asks for detail. " +
                "Never claim that a phone, glasses, notification, call, message, or other device action succeeded unless the app reports that it completed."
    }
}

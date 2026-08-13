package com.achyut.adglasses.ai.router

import android.content.Context
import android.util.Base64
import com.achyut.adglasses.shared.settings.AgentProviderType
import com.achyut.adglasses.agent.LocalAgentPrefs as AutomationPrefs
import com.achyut.adglasses.agent.AiPrefs
import com.achyut.adglasses.agent.ServerPrefs
import com.achyut.adglasses.localmodels.provider.LocalModelsProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.net.UnknownHostException
import java.net.ConnectException
import java.net.SocketTimeoutException

private data class CloudRequest(
    val type: String,
    val payload: String,
    val createdAtMs: Long,
)

object CliCloudQueue {
    private const val PREFS_NAME = "cli_relay_queue"
    private const val KEY_ITEMS = "items"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    private fun read(context: Context): MutableList<CloudRequest> {
        val raw = prefs(context).getString(KEY_ITEMS, "[]") ?: "[]"
        val arr = JSONArray(raw)
        return MutableList(arr.length()) { idx ->
            val obj = arr.optJSONObject(idx) ?: JSONObject()
            CloudRequest(
                type = obj.optString("type"),
                payload = obj.optString("payload"),
                createdAtMs = obj.optLong("createdAtMs", System.currentTimeMillis()),
            )
        }
    }

    @Synchronized
    private fun write(context: Context, items: List<CloudRequest>) {
        val arr = JSONArray()
        items.forEach {
            arr.put(
                JSONObject()
                    .put("type", it.type)
                    .put("payload", it.payload)
                    .put("createdAtMs", it.createdAtMs)
            )
        }
        prefs(context).edit().putString(KEY_ITEMS, arr.toString()).apply()
    }

    fun enqueueVoice(context: Context, prompt: String) {
        val items = read(context)
        items += CloudRequest("voice", prompt, System.currentTimeMillis())
        write(context, items)
    }

    fun enqueueImage(context: Context, imagePath: String) {
        val items = read(context)
        items += CloudRequest("image", imagePath, System.currentTimeMillis())
        write(context, items)
    }

    fun size(context: Context): Int = read(context).size

    suspend fun flush(context: Context): Int {
        val pending = read(context)
        if (pending.isEmpty()) return 0
        val remaining = mutableListOf<CloudRequest>()
        var delivered = 0
        pending.forEach { req ->
            val success = when (req.type) {
                "voice" -> CliCloudClient.voiceQuery(context, req.payload).isSuccess
                "image" -> CliCloudClient.imageQuery(context, req.payload).isSuccess
                else -> true
            }
            if (success) {
                delivered++
            } else {
                remaining += req
            }
        }
        write(context, remaining)
        return delivered
    }
}

object CliCloudClient {
    private const val CONNECT_TIMEOUT_MS = 7000
    private const val READ_TIMEOUT_MS = 120000
    private const val MAX_RETRIES = 3
    private const val BASE_DELAY_MS = 2000L
    private const val CLOUD_DOWN_HINT =
        "Cloud server may be down or this app may need an update to use the new server address."

    data class ModelOption(
        val id: String,
        val label: String,
        val quotaMultiplier: Int,
    )

    data class VoiceQueryTelemetry(
        val inputTokens: Int,
        val outputTokens: Int,
        val promptTokensPerSec: Double,
        val generationTokensPerSec: Double,
        val totalMs: Long,
    )

    data class VoiceQueryDetails(
        val reply: String,
        val telemetry: VoiceQueryTelemetry,
    )

    fun cloudUnavailableHint(error: Throwable?): String? {
        var cur = error
        while (cur != null) {
            when (cur) {
                is UnknownHostException,
                is ConnectException,
                is SocketTimeoutException,
                is IOException -> return CLOUD_DOWN_HINT
            }
            cur = cur.cause
        }
        return null
    }

    fun relayUnavailableHintFromText(text: String): String? {
        return cloudUnavailableHint(IllegalStateException(text))
    }

    suspend fun healthCheck(context: Context): Result<String> = runCatching {
        retry(times = 2) {
            val response = postJson(
                context,
                endpoint(context, "/health"),
                JSONObject().put("backend", AiProviderPrefs.getCloudBackend(context).wire)
            )
            val status = response.optString("status", "unknown")
            val backend = response.optString("backend", "unknown")
            "$status ($backend)"
        }
    }

    suspend fun fetchAvailableModels(context: Context): Result<List<ModelOption>> = runCatching {
        val candidates = listOf("/models", "/v1/models")
        val seen = linkedMapOf<String, ModelOption>()

        for (path in candidates) {
            val parsed = runCatching {
                val response = postJson(
                    context,
                    endpoint(context, path),
                    JSONObject().put("backend", AiProviderPrefs.getCloudBackend(context).wire)
                )
                parseModels(response)
            }.getOrDefault(emptyList())
            parsed.forEach { option ->
                val key = option.id.trim().lowercase()
                if (key.isNotBlank() && !seen.containsKey(key)) {
                    seen[key] = option
                }
            }
        }

        if (seen.isEmpty()) {
            throw IllegalStateException("No models returned by cloud")
        }
        seen.values.toList()
    }

    suspend fun chat(
        context: Context,
        chatId: String,
        prompt: String,
        messages: List<Map<String, String>>,
        modelOverride: String? = null,
    ): Result<String> = runCatching {
        CloudServerCapabilitiesClient.get(context).getOrNull()?.let { caps ->
            if (!caps.chat) {
                throw IllegalStateException("Server capability unavailable: chat")
            }
        }

        retry {
            val messagesArray = JSONArray()
            for (m in messages) {
                messagesArray.put(JSONObject().put("role", m["role"]).put("content", m["content"]))
            }
            val response = postJson(
                context,
                endpoint(context, "/chat"),
                JSONObject()
                    .put("backend", AiProviderPrefs.getCloudBackend(context).wire)
                    .put("chatId", chatId)
                    .put("prompt", prompt)
                    .put("messages", messagesArray)
                    .apply {
                        val model = modelOverride?.trim().orEmpty()
                        if (model.isNotBlank()) put("model", model)
                    }
            )
            response.optString("reply").ifBlank {
                throw IllegalStateException("Cloud returned empty chat reply")
            }
        }
    }

    suspend fun voiceQuery(
        context: Context,
        prompt: String,
        backendOverride: CloudApiBackend? = null,
        modelOverride: String? = null,
    ): Result<String> = runCatching {
        voiceQueryDetailed(
            context = context,
            prompt = prompt,
            backendOverride = backendOverride,
            modelOverride = modelOverride,
        ).getOrThrow().reply
    }

    suspend fun voiceQueryDetailed(
        context: Context,
        prompt: String,
        backendOverride: CloudApiBackend? = null,
        modelOverride: String? = null,
    ): Result<VoiceQueryDetails> = runCatching {
        CloudServerCapabilitiesClient.get(context).getOrNull()?.let { caps ->
            if (!caps.voiceQuery) {
                throw IllegalStateException("Server capability unavailable: voice_query")
            }
        }

        retry {
            val started = System.currentTimeMillis()
            val response = postJson(
                context,
                endpoint(context, "/voice-query"),
                JSONObject()
                    .put("backend", (backendOverride ?: AiProviderPrefs.getCloudBackend(context)).wire)
                    .put("prompt", prompt)
                    .apply {
                        val model = modelOverride?.trim().orEmpty()
                        if (model.isNotBlank()) put("model", model)
                    }
            )
            val elapsedMs = (System.currentTimeMillis() - started).coerceAtLeast(1L)
            val reply = response.optString("reply").ifBlank {
                throw IllegalStateException("Cloud returned empty voice reply")
            }

            VoiceQueryDetails(
                reply = reply,
                telemetry = parseVoiceTelemetry(
                    prompt = prompt,
                    reply = reply,
                    response = response,
                    elapsedMs = elapsedMs,
                ),
            )
        }
    }

    suspend fun imageQuery(
        context: Context,
        imagePath: String,
        prompt: String? = null,
        backendOverride: CloudApiBackend? = null,
        modelOverride: String? = null,
    ): Result<String> = runCatching {
        CloudServerCapabilitiesClient.get(context).getOrNull()?.let { caps ->
            if (!caps.imageQuery) {
                throw IllegalStateException("Server capability unavailable: image_query")
            }
        }

        val file = File(imagePath)
        require(file.exists()) { "Image file not found: $imagePath" }
        require(file.length() > 1000) { "Image file too small (${file.length()} bytes), likely corrupted" }
        
        val imageBase64 = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
        
        val response = postJson(
            context,
            endpoint(context, "/image-query"),
            JSONObject()
                .put("backend", (backendOverride ?: AiProviderPrefs.getCloudBackend(context)).wire)
                .put("filename", file.name)
                .put("imageBase64", imageBase64)
                .apply {
                    val requestPrompt = prompt?.trim().orEmpty()
                    if (requestPrompt.isNotBlank()) put("prompt", requestPrompt)
                    val model = modelOverride?.trim().orEmpty()
                    if (model.isNotBlank()) put("model", model)
                }
        )
        response.optString("reply").ifBlank {
            throw IllegalStateException("Cloud returned empty image reply")
        }
    }

    private suspend fun <T> retry(
        times: Int = MAX_RETRIES,
        initialDelay: Long = BASE_DELAY_MS,
        block: () -> T,
    ): T {
        var lastError: Throwable? = null
        repeat(times) { index ->
            try {
                return block()
            } catch (t: Throwable) {
                lastError = t
                if (t is CancellationException) throw t
                if (index < times - 1) {
                    delay(initialDelay * (index + 1))
                }
            }
        }
        throw lastError ?: IllegalStateException("retry failed")
    }

    private fun endpoint(context: Context, path: String): String {
        val base = AiProviderPrefs.getRelayBaseUrl(context).trimEnd('/')
        require(base.startsWith("http://") || base.startsWith("https://")) {
            "Cloud URL must start with http:// or https://"
        }
        return "$base$path"
    }

    private fun postJson(context: Context, url: String, payload: JSONObject): JSONObject {
        val conn = (URL(url).openConnection() as HttpURLConnection)
        conn.requestMethod = "POST"
        conn.connectTimeout = CONNECT_TIMEOUT_MS
        conn.readTimeout = READ_TIMEOUT_MS
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        val serverToken = ServerPrefs.getApiToken(context)
        if (serverToken.isNotBlank()) {
            conn.setRequestProperty("Authorization", "Bearer $serverToken")
        }
        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val body = BufferedReader(InputStreamReader(stream ?: conn.inputStream)).use { it.readText() }
        conn.disconnect()
        if (code !in 200..299) {
            throw IllegalStateException("Cloud HTTP $code: $body")
        }
        return JSONObject(body)
    }

    private fun parseModels(payload: JSONObject): List<ModelOption> {
        val out = mutableListOf<ModelOption>()
        val data = payload.optJSONArray("data") ?: payload.optJSONArray("models") ?: return out
        for (i in 0 until data.length()) {
            val item = data.optJSONObject(i) ?: continue
            out.add(
                ModelOption(
                    id = item.optString("id"),
                    label = item.optString("label").ifBlank { item.optString("id") },
                    quotaMultiplier = item.optInt("quota_multiplier", 1)
                )
            )
        }
        return out
    }

    private fun parseVoiceTelemetry(
        prompt: String,
        reply: String,
        response: JSONObject,
        elapsedMs: Long,
    ): VoiceQueryTelemetry {
        val usage = response.optJSONObject("usage") ?: JSONObject()
        val perf = response.optJSONObject("performance") ?: response.optJSONObject("metrics") ?: JSONObject()
        val inputTokens = usage.optInt("prompt_tokens", 0).let { if (it > 0) it else (prompt.length / 4) }
        val outputTokens = usage.optInt("completion_tokens", 0).let { if (it > 0) it else (reply.length / 4) }
        return VoiceQueryTelemetry(
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            promptTokensPerSec = 0.0,
            generationTokensPerSec = 0.0,
            totalMs = elapsedMs
        )
    }
}

object DirectApiClient {
    private const val CONNECT_TIMEOUT_MS = 10000
    private const val READ_TIMEOUT_MS = 120000
    private const val MAX_RETRIES = 3
    private const val BASE_DELAY_MS = 2000L

    suspend fun chatCompletion(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<Map<String, String>>,
    ): Result<String> = runCatching {
        val payload = JSONObject()
            .put("model", model)
            .put("messages", JSONArray().apply {
                messages.forEach { m ->
                    put(JSONObject().put("role", m["role"]?.lowercase() ?: "user").put("content", m["content"]))
                }
            })

        val url = if (baseUrl.endsWith("/chat/completions")) baseUrl else baseUrl.trimEnd('/') + "/chat/completions"
        
        var lastError: Throwable? = null
        repeat(MAX_RETRIES) { attempt ->
            try {
                val conn = (URL(url).openConnection() as HttpURLConnection)
                conn.requestMethod = "POST"
                conn.connectTimeout = CONNECT_TIMEOUT_MS
                conn.readTimeout = READ_TIMEOUT_MS
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Authorization", "Bearer $apiKey")
                OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }
                
                val code = conn.responseCode
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                
                val json = JSONObject(body)
                return@runCatching json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
            } catch (e: Exception) {
                lastError = e
                delay(BASE_DELAY_MS * (attempt + 1))
            }
        }
        throw lastError ?: IllegalStateException("Direct API failed")
    }
}

object CliCloudRouter {
    interface ChatStreamCallbacks {
        fun onStatus(status: String) {}
        fun onToken(token: String) {}
    }

    private val localModelsProvider = LocalModelsProvider()

    suspend fun chatReply(context: Context, chatId: String, userPrompt: String, messages: List<Map<String, String>>): String {
        return chatReplyStreaming(context, chatId, userPrompt, messages, emptyList(), null, null)
    }

    suspend fun chatReplyStreaming(
        context: Context,
        chatId: String,
        userPrompt: String,
        messages: List<Map<String, String>>,
        imagePaths: List<String>,
        audioPath: String?,
        callbacks: ChatStreamCallbacks?,
    ): String {
        val providerType = AiProviderPrefs.getProvider(context)
        return when (providerType) {
            AiProviderType.CLOUD_API -> {
                val result = CliCloudClient.chat(context, chatId, userPrompt, messages, AiPrefs.getRequestsModel(context))
                result.getOrElse { "Cloud unavailable (${it.message})." }
            }
            AiProviderType.LOCAL_MODELS -> {
                localModelsProvider.streamChat(
                    context = context,
                    messages = messages,
                    onStatus = { callbacks?.onStatus(it) },
                    onToken = { callbacks?.onToken(it) },
                    imagePaths = imagePaths,
                    audioPath = audioPath,
                )
            }
            else -> "Provider not implemented"
        }
    }

    suspend fun textReply(context: Context, prompt: String): String {
        val providerType = AiProviderPrefs.getProvider(context)
        return when (providerType) {
            AiProviderType.CLOUD_API -> {
                val result = CliCloudClient.voiceQuery(context, prompt, null, AiPrefs.getTasksModel(context))
                result.getOrElse { "Cloud unavailable (${it.message})." }
            }
            AiProviderType.LOCAL_MODELS -> {
                localModelsProvider.streamChat(
                    context = context,
                    messages = listOf(mapOf("role" to "User", "content" to prompt)),
                )
            }
            else -> "Provider not implemented"
        }
    }

    suspend fun cancelCurrentGeneration(context: Context) {
        if (AiProviderPrefs.getProvider(context) == AiProviderType.LOCAL_MODELS) {
            localModelsProvider.cancelGeneration()
        }
    }
}

object CloudServerCapabilitiesClient {
    private var cached: CloudServerCapabilities? = null
    private var cachedBaseUrl: String = ""
    private var cachedAtMs: Long = 0L
    private const val CACHE_TTL_MS = 60_000L

    suspend fun get(context: Context): Result<CloudServerCapabilities> = runCatching {
        val baseUrl = AiProviderPrefs.getCloudBaseUrl(context).trimEnd('/')
        val now = System.currentTimeMillis()
        if (cached != null && cachedBaseUrl == baseUrl && now - cachedAtMs < CACHE_TTL_MS) {
            return@runCatching cached!!
        }
        val url = "$baseUrl/capabilities"
        val body = withContext(Dispatchers.IO) {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            } finally {
                conn.disconnect()
            }
        }
        val json = JSONObject(body)
        val caps = CloudServerCapabilities(
            voiceQuery = json.optBoolean("voice_query", true),
            imageQuery = json.optBoolean("image_query", true),
            audioQuery = json.optBoolean("audio_query", true),
            chat = json.optBoolean("chat", true)
        )
        cached = caps
        cachedBaseUrl = baseUrl
        cachedAtMs = now
        caps
    }
}

data class CloudServerCapabilities(
    val voiceQuery: Boolean,
    val imageQuery: Boolean,
    val audioQuery: Boolean,
    val chat: Boolean
)

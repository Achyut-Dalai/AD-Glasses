package com.ad_glasses.ai.router

import android.content.Context
import android.util.Base64
import com.ad_glasses.shared.ai.AiVisionDetail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runInterruptible
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

internal fun geminiModelsUrl(baseUrl: String): String =
    "${baseUrl.trim().trimEnd('/')}/models?pageSize=1000"

internal fun geminiGenerateContentUrl(baseUrl: String, model: String): String =
    "${baseUrl.trim().trimEnd('/')}/models/${ApiProvider.GOOGLE.normalizeModelId(model)}:generateContent"

internal fun geminiStreamGenerateContentUrl(baseUrl: String, model: String): String =
    "${baseUrl.trim().trimEnd('/')}/models/${ApiProvider.GOOGLE.normalizeModelId(model)}:streamGenerateContent?alt=sse"

internal fun geminiImageMimeType(extension: String): String = when (extension.trim().trimStart('.').lowercase()) {
    "png" -> "image/png"
    "webp" -> "image/webp"
    "gif" -> "image/gif"
    "heic" -> "image/heic"
    "heif" -> "image/heif"
    "avif" -> "image/avif"
    else -> "image/jpeg"
}

internal fun geminiAudioMimeType(extension: String): String = when (extension.trim().trimStart('.').lowercase()) {
    "mp3", "mpeg" -> "audio/mpeg"
    "m4a", "mp4" -> "audio/mp4"
    "ogg", "opus" -> "audio/ogg"
    "webm" -> "audio/webm"
    "flac" -> "audio/flac"
    "aac" -> "audio/aac"
    else -> "audio/wav"
}

internal data class GeminiVisibleTextPart(
    val text: String,
    val thought: Boolean = false,
)

/** Pure helper so reasoning-filter behavior is testable without Android's org.json JVM stubs. */
internal fun geminiVisibleText(
    parts: List<GeminiVisibleTextPart>,
    preserveWhitespace: Boolean,
): String {
    if (preserveWhitespace) {
        return buildString {
            parts.forEach { part ->
                if (!part.thought) append(part.text)
            }
        }
    }

    return buildString {
        parts.forEach { part ->
            if (part.thought) return@forEach
            val text = part.text.trim()
            if (text.isNotBlank()) {
                if (isNotEmpty()) append('\n')
                append(text)
            }
        }
    }.trim()
}

/**
 * Return only user-visible Gemini text parts. `Part.thought=true` is structured reasoning metadata,
 * never assistant answer text, so it must be discarded before streaming, persistence, or TTS.
 */
internal fun geminiVisibleText(parts: JSONArray, preserveWhitespace: Boolean): String {
    val values = buildList {
        for (index in 0 until parts.length()) {
            val part = parts.optJSONObject(index) ?: continue
            add(
                GeminiVisibleTextPart(
                    text = part.optString("text"),
                    thought = part.optBoolean("thought", false),
                ),
            )
        }
    }
    return geminiVisibleText(values, preserveWhitespace)
}

/** Direct Cloud AI transport resolved through the active encrypted profile. */
object ApiTokenClient {
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 120_000
    private const val MAX_SERVER_ATTEMPTS = 2
    private const val SERVER_RETRY_DELAY_MS = 500L

    suspend fun chat(
        context: Context,
        messages: List<Map<String, String>>,
        imagePaths: List<String> = emptyList(),
        audioPath: String? = null,
        maxTokens: Int = 2048,
        webRequested: Boolean = false,
        generationMode: CloudGenerationMode = CloudGenerationMode.DEFAULT,
        visionDetail: AiVisionDetail = AiVisionDetail.STANDARD,
    ): Result<String> = runCatching {
        val (profile, apiKey) = AiProviderPrefs.activeProfileWithKey(context)
        require(profile.model.isNotBlank()) { "${profile.name} does not have a model selected." }
        val baseUrl = profile.provider.resolveBaseUrl(profile.baseUrl)
        require(baseUrl.startsWith("https://")) { "${profile.name} does not have a valid HTTPS API base URL." }

        val useNativeWeb = webRequested && profile.webAvailable && imagePaths.isEmpty() && audioPath.isNullOrBlank()
        val text = when {
            profile.provider == ApiProvider.GOOGLE -> {
                val response = retryTransientServerFailure {
                    postGeminiGenerateContent(
                        profile = profile,
                        apiKey = apiKey,
                        messages = messages,
                        imagePaths = imagePaths,
                        audioPath = audioPath,
                        maxTokens = maxTokens,
                        webRequested = useNativeWeb,
                        generationMode = generationMode,
                        visionDetail = visionDetail,
                    )
                }
                extractGeminiText(response).ifBlank {
                    throw IllegalStateException(
                        geminiNoVisibleAnswerDetail(profile.model, extractGeminiDiagnostics(response)),
                    )
                }
            }
            useNativeWeb && profile.provider == ApiProvider.OPENAI ->
                retryTransientServerFailure {
                    postOpenAiResponses(profile, apiKey, messages, maxTokens, generationMode)
                }.let(::extractOpenAiResponseText)
            else -> {
                val payload = JSONObject()
                    .put("model", profile.model)
                    .put("messages", buildOpenAiMessages(messages, imagePaths, audioPath))
                applyOpenAiCompatibleTuning(payload, profile, maxTokens, generationMode)
                if (useNativeWeb && profile.provider == ApiProvider.OPENROUTER) {
                    payload.put(
                        "tools",
                        JSONArray().put(JSONObject().put("type", "openrouter:web_search")),
                    )
                }
                retryTransientServerFailure {
                    postJson(
                        url = OpenAiCompatibleEndpoint.chatCompletionsUrl(baseUrl),
                        apiKey = apiKey,
                        payload = payload,
                    )
                }.let(::extractChatCompletionText)
            }
        }
        text.ifBlank { throw IllegalStateException("${profile.name} returned an empty response") }
    }

    /**
     * Stream user-visible text as it is generated while also returning the complete completion.
     * Structured reasoning fields are intentionally ignored; only provider answer text is emitted.
     */
    suspend fun chatStreaming(
        context: Context,
        messages: List<Map<String, String>>,
        imagePaths: List<String> = emptyList(),
        audioPath: String? = null,
        maxTokens: Int = 2048,
        webRequested: Boolean = false,
        generationMode: CloudGenerationMode = CloudGenerationMode.DEFAULT,
        visionDetail: AiVisionDetail = AiVisionDetail.STANDARD,
        onToken: (String) -> Unit,
    ): Result<String> = runCatching {
        val (profile, apiKey) = AiProviderPrefs.activeProfileWithKey(context)
        require(profile.model.isNotBlank()) { "${profile.name} does not have a model selected." }
        val baseUrl = profile.provider.resolveBaseUrl(profile.baseUrl)
        require(baseUrl.startsWith("https://")) { "${profile.name} does not have a valid HTTPS API base URL." }

        val useNativeWeb = webRequested && profile.webAvailable && imagePaths.isEmpty() && audioPath.isNullOrBlank()
        val full = StringBuilder()
        var geminiDiagnostics = GeminiResponseDiagnostics()
        fun emit(delta: String) {
            if (delta.isEmpty()) return
            full.append(delta)
            onToken(delta)
        }

        runInterruptible {
            when {
                profile.provider == ApiProvider.GOOGLE -> {
                    val payload = buildGeminiGeneratePayload(
                        profile = profile,
                        messages = messages,
                        imagePaths = imagePaths,
                        audioPath = audioPath,
                        maxTokens = maxTokens,
                        webRequested = useNativeWeb,
                        generationMode = generationMode,
                        visionDetail = visionDetail,
                    )
                    requestSse(
                        url = geminiStreamGenerateContentUrl(baseUrl, profile.model),
                        apiKey = null,
                        payload = payload,
                        extraHeaders = mapOf("x-goog-api-key" to apiKey),
                    ) { data ->
                        if (data == "[DONE]") return@requestSse
                        val event = runCatching { JSONObject(data) }.getOrNull() ?: return@requestSse
                        geminiDiagnostics = geminiDiagnostics.merge(extractGeminiDiagnostics(event))
                        emit(extractGeminiDeltaText(event))
                    }
                }

                useNativeWeb && profile.provider == ApiProvider.OPENAI -> {
                    val payload = buildOpenAiResponsesPayload(
                        profile = profile,
                        messages = messages,
                        maxTokens = maxTokens,
                        generationMode = generationMode,
                    ).put("stream", true)
                    requestSse(
                        url = "$baseUrl/responses",
                        apiKey = apiKey,
                        payload = payload,
                    ) { data ->
                        if (data == "[DONE]") return@requestSse
                        val event = runCatching { JSONObject(data) }.getOrNull() ?: return@requestSse
                        if (event.optString("type") == "response.output_text.delta") {
                            emit(event.optString("delta"))
                        }
                    }
                }

                else -> {
                    val payload = JSONObject()
                        .put("model", profile.model)
                        .put("messages", buildOpenAiMessages(messages, imagePaths, audioPath))
                        .put("stream", true)
                    applyOpenAiCompatibleTuning(payload, profile, maxTokens, generationMode)
                    if (useNativeWeb && profile.provider == ApiProvider.OPENROUTER) {
                        payload.put(
                            "tools",
                            JSONArray().put(JSONObject().put("type", "openrouter:web_search")),
                        )
                    }
                    requestSse(
                        url = OpenAiCompatibleEndpoint.chatCompletionsUrl(baseUrl),
                        apiKey = apiKey,
                        payload = payload,
                    ) { data ->
                        if (data == "[DONE]") return@requestSse
                        val event = runCatching { JSONObject(data) }.getOrNull() ?: return@requestSse
                        // DeepSeek/OpenRouter/Groq may return reasoning_content alongside content.
                        // extractChatCompletionDelta intentionally reads only content, so hidden
                        // thoughts never enter the sanitizer/TTS pipeline through structured fields.
                        emit(extractChatCompletionDelta(event))
                    }
                }
            }
        }

        full.toString().trim().ifBlank {
            if (profile.provider == ApiProvider.GOOGLE) {
                throw IllegalStateException(geminiNoVisibleAnswerDetail(profile.model, geminiDiagnostics))
            }
            throw IllegalStateException("${profile.name} returned an empty streamed response")
        }
    }

    suspend fun image(
        context: Context,
        systemPrompt: String,
        userPrompt: String,
        imagePath: String,
        maxTokens: Int = 1200,
        generationMode: CloudGenerationMode = CloudGenerationMode.DEFAULT,
        visionDetail: AiVisionDetail = AiVisionDetail.STANDARD,
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
            generationMode = generationMode,
            visionDetail = visionDetail,
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
        val cleanBase = provider.resolveBaseUrl(baseUrl)
        require(cleanBase.startsWith("https://")) { "API base URL must use HTTPS." }

        val response = if (provider == ApiProvider.GOOGLE) {
            getJson(
                geminiModelsUrl(cleanBase),
                apiKey = null,
                extraHeaders = mapOf("x-goog-api-key" to key),
            )
        } else {
            getJson(OpenAiCompatibleEndpoint.modelsUrl(cleanBase), apiKey = key)
        }

        val models = selectableModelIds(response)
        if (models.isEmpty()) throw IllegalStateException("The provider returned no selectable generation models.")
        models.sortedWith(
            compareBy<String> { it != provider.defaultModel }
                .thenBy { !it.contains("flash", ignoreCase = true) }
                .thenBy { it.lowercase() },
        )
    }

    private fun selectableModelIds(response: JSONObject): Set<String> {
        val models = linkedSetOf<String>()
        response.optJSONArray("data")?.let { data ->
            for (index in 0 until data.length()) {
                data.optJSONObject(index)?.optString("id")?.trim()?.takeIf { it.isNotBlank() }?.let(models::add)
            }
        }
        response.optJSONArray("models")?.let { data ->
            for (index in 0 until data.length()) {
                val item = data.optJSONObject(index) ?: continue
                val methods = item.optJSONArray("supportedGenerationMethods")
                if (methods != null) {
                    var canGenerate = false
                    for (methodIndex in 0 until methods.length()) {
                        if (methods.optString(methodIndex) == "generateContent") {
                            canGenerate = true
                            break
                        }
                    }
                    if (!canGenerate) continue
                }
                val id = item.optString("id").trim().ifBlank {
                    item.optString("name").trim().removePrefix("models/")
                }
                if (id.isNotBlank()) models += id
            }
        }
        return models
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

    private fun applyOpenAiCompatibleTuning(
        payload: JSONObject,
        profile: CloudAiProfile,
        maxTokens: Int,
        generationMode: CloudGenerationMode,
    ) {
        val tuning = CloudModelPolicy.requestTuning(profile, generationMode)
        payload.put(tuning.completionTokenField, maxTokens)
        tuning.deepSeekThinkingType?.let { type ->
            payload.put("thinking", JSONObject().put("type", type))
        }
        tuning.reasoningEffort?.let { payload.put("reasoning_effort", it) }
        tuning.reasoningFormat?.let { payload.put("reasoning_format", it) }
        tuning.includeReasoning?.let { payload.put("include_reasoning", it) }
        tuning.openRouterReasoningEffort?.let { effort ->
            payload.put(
                "reasoning",
                JSONObject()
                    .put("effort", effort)
                    .put("exclude", tuning.excludeReasoning),
            )
        }
    }

    private fun buildOpenAiResponsesPayload(
        profile: CloudAiProfile,
        messages: List<Map<String, String>>,
        maxTokens: Int,
        generationMode: CloudGenerationMode,
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
        val tuning = CloudModelPolicy.requestTuning(profile, generationMode)
        tuning.reasoningEffort?.let { effort ->
            payload.put("reasoning", JSONObject().put("effort", effort))
        }
        tuning.responseVerbosity?.let { verbosity ->
            payload.put("text", JSONObject().put("verbosity", verbosity))
        }
        return payload
    }

    private fun postOpenAiResponses(
        profile: CloudAiProfile,
        apiKey: String,
        messages: List<Map<String, String>>,
        maxTokens: Int,
        generationMode: CloudGenerationMode,
    ): JSONObject {
        val baseUrl = profile.provider.resolveBaseUrl(profile.baseUrl)
        return postJson(
            "$baseUrl/responses",
            apiKey,
            buildOpenAiResponsesPayload(profile, messages, maxTokens, generationMode),
        )
    }

    /** Native Gemini REST request for text, images, audio, and optional Google Search grounding. */
    private fun postGeminiGenerateContent(
        profile: CloudAiProfile,
        apiKey: String,
        messages: List<Map<String, String>>,
        imagePaths: List<String>,
        audioPath: String?,
        maxTokens: Int,
        webRequested: Boolean,
        generationMode: CloudGenerationMode,
        visionDetail: AiVisionDetail,
    ): JSONObject {
        val baseUrl = profile.provider.resolveBaseUrl(profile.baseUrl)
        val endpoint = geminiGenerateContentUrl(baseUrl, profile.model)
        return postJson(
            endpoint,
            apiKey = null,
            payload = buildGeminiGeneratePayload(
                profile = profile,
                messages = messages,
                imagePaths = imagePaths,
                audioPath = audioPath,
                maxTokens = maxTokens,
                webRequested = webRequested,
                generationMode = generationMode,
                visionDetail = visionDetail,
            ),
            extraHeaders = mapOf("x-goog-api-key" to apiKey),
        )
    }

    private fun buildGeminiGeneratePayload(
        profile: CloudAiProfile,
        messages: List<Map<String, String>>,
        imagePaths: List<String>,
        audioPath: String?,
        maxTokens: Int,
        webRequested: Boolean,
        generationMode: CloudGenerationMode,
        visionDetail: AiVisionDetail,
    ): JSONObject {
        val systemParts = JSONArray()
        val contents = JSONArray()
        val lastUserIndex = messages.indexOfLast { message ->
            message["role"]?.trim()?.lowercase().orEmpty().ifBlank { "user" } == "user"
        }

        messages.forEachIndexed { index, message ->
            val role = message["role"]?.trim()?.lowercase().orEmpty().ifBlank { "user" }
            val text = message["content"].orEmpty()
            if (role == "system") {
                if (text.isNotBlank()) systemParts.put(JSONObject().put("text", text))
            } else {
                val parts = JSONArray()
                if (text.isNotBlank()) parts.put(JSONObject().put("text", text))
                if (index == lastUserIndex) {
                    appendGeminiMediaParts(parts, imagePaths, audioPath)
                }
                if (parts.length() > 0) {
                    contents.put(
                        JSONObject()
                            .put("role", if (role == "assistant") "model" else "user")
                            .put("parts", parts),
                    )
                }
            }
        }
        require(contents.length() > 0) { "Gemini request has no user/model content." }

        val generationConfig = JSONObject().put("maxOutputTokens", maxTokens)
        val tuning = CloudModelPolicy.requestTuning(profile, generationMode)
        if (tuning.geminiThinkingLevel != null || tuning.geminiThinkingBudget != null) {
            val thinkingConfig = JSONObject().put("includeThoughts", false)
            tuning.geminiThinkingLevel?.let { level -> thinkingConfig.put("thinkingLevel", level) }
            tuning.geminiThinkingBudget?.let { budget -> thinkingConfig.put("thinkingBudget", budget) }
            generationConfig.put("thinkingConfig", thinkingConfig)
        }
        if (imagePaths.isNotEmpty() && profile.model.trim().lowercase().startsWith("gemini-3")) {
            generationConfig.put(
                "mediaResolution",
                when (visionDetail) {
                    AiVisionDetail.STANDARD -> "MEDIA_RESOLUTION_MEDIUM"
                    AiVisionDetail.TEXT_DETAIL -> "MEDIA_RESOLUTION_HIGH"
                },
            )
        }

        val payload = JSONObject()
            .put("contents", contents)
            .put("generationConfig", generationConfig)
        if (systemParts.length() > 0) {
            payload.put("systemInstruction", JSONObject().put("parts", systemParts))
        }
        if (webRequested) {
            payload.put(
                "tools",
                JSONArray().put(JSONObject().put("google_search", JSONObject())),
            )
        }
        return payload
    }

    private fun appendGeminiMediaParts(
        parts: JSONArray,
        imagePaths: List<String>,
        audioPath: String?,
    ) {
        imagePaths.forEach { path ->
            val file = File(path)
            require(file.isFile) { "Image file not found: $path" }
            val mime = geminiImageMimeType(file.extension)
            val data = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
            parts.put(
                JSONObject().put(
                    "inline_data",
                    JSONObject()
                        .put("mime_type", mime)
                        .put("data", data),
                ),
            )
        }
        audioPath?.takeIf { it.isNotBlank() }?.let { path ->
            val file = File(path)
            require(file.isFile) { "Audio file not found: $path" }
            val mime = geminiAudioMimeType(file.extension)
            val data = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
            parts.put(
                JSONObject().put(
                    "inline_data",
                    JSONObject()
                        .put("mime_type", mime)
                        .put("data", data),
                ),
            )
        }
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

    private suspend fun retryTransientServerFailure(block: () -> JSONObject): JSONObject {
        var last: Throwable? = null
        repeat(MAX_SERVER_ATTEMPTS) { attempt ->
            try {
                // HttpURLConnection is blocking. Make the actual socket section interruptible so
                // cancellation of a chat/turn can stop occupying its worker instead of lingering
                // until the long read timeout and finishing as a stale request later.
                return runInterruptible { block() }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                last = error
                val detail = generateSequence(error) { it.cause }
                    .joinToString(" ") { it.message.orEmpty() }
                // Quota/rate-limit failures are not improved by immediately replaying the exact
                // wearable request. Fail 429 fast so voice can speak the provider status instead
                // of adding seconds of silence. Give only transient server faults one short retry.
                val serverFailure = Regex("(?:API )?HTTP 5\\d\\d", RegexOption.IGNORE_CASE)
                    .containsMatchIn(detail)
                if (!serverFailure || attempt == MAX_SERVER_ATTEMPTS - 1) throw error
                delay(SERVER_RETRY_DELAY_MS)
            }
        }
        throw last ?: IllegalStateException("API request failed")
    }

    private fun postJson(
        url: String,
        apiKey: String?,
        payload: JSONObject,
        extraHeaders: Map<String, String> = emptyMap(),
    ): JSONObject = requestJson("POST", url, apiKey, payload, extraHeaders)

    private fun getJson(
        url: String,
        apiKey: String?,
        extraHeaders: Map<String, String> = emptyMap(),
    ): JSONObject = requestJson("GET", url, apiKey, null, extraHeaders)

    private fun requestJson(
        method: String,
        url: String,
        apiKey: String?,
        payload: JSONObject?,
        extraHeaders: Map<String, String> = emptyMap(),
    ): JSONObject {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.setRequestProperty("Accept", "application/json")
            if (!apiKey.isNullOrBlank()) {
                conn.setRequestProperty(
                    "Authorization",
                    OpenAiCompatibleEndpoint.authorizationHeader(apiKey),
                )
            }
            extraHeaders.forEach { (name, value) -> conn.setRequestProperty(name, value) }
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

    private fun requestSse(
        url: String,
        apiKey: String?,
        payload: JSONObject,
        extraHeaders: Map<String, String> = emptyMap(),
        onData: (String) -> Unit,
    ) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.setRequestProperty("Accept", "text/event-stream")
            if (!apiKey.isNullOrBlank()) {
                conn.setRequestProperty(
                    "Authorization",
                    OpenAiCompatibleEndpoint.authorizationHeader(apiKey),
                )
            }
            extraHeaders.forEach { (name, value) -> conn.setRequestProperty(name, value) }
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }

            val code = conn.responseCode
            if (code !in 200..299) {
                val body = BufferedReader(InputStreamReader(conn.errorStream ?: conn.inputStream)).use { it.readText() }
                throw IllegalStateException("API HTTP $code: $body")
            }

            BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { reader ->
                val dataLines = mutableListOf<String>()
                fun flushEvent() {
                    if (dataLines.isEmpty()) return
                    onData(dataLines.joinToString("\n"))
                    dataLines.clear()
                }

                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) {
                        flushEvent()
                        continue
                    }
                    if (line.startsWith("data:")) {
                        dataLines += line.substring(5).trimStart()
                    }
                }
                flushEvent()
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun extractChatCompletionText(response: JSONObject): String {
        val message = response.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?: return ""
        return extractTextValue(message.opt("content")).trim()
    }

    private fun extractChatCompletionDelta(response: JSONObject): String {
        val delta = response.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("delta")
            ?: return ""
        return extractTextValue(delta.opt("content"))
    }

    private fun extractTextValue(raw: Any?): String {
        if (raw == null || raw === JSONObject.NULL) return ""
        if (raw is String) return raw
        if (raw is JSONArray) {
            return buildString {
                for (index in 0 until raw.length()) {
                    val part = raw.opt(index)
                    when (part) {
                        is String -> append(part)
                        is JSONObject -> append(part.optString("text"))
                    }
                }
            }
        }
        return raw.toString()
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
        return geminiVisibleText(parts, preserveWhitespace = false)
    }

    private fun extractGeminiDeltaText(response: JSONObject): String {
        val parts = response.optJSONArray("candidates")
            ?.optJSONObject(0)
            ?.optJSONObject("content")
            ?.optJSONArray("parts")
            ?: return ""
        return geminiVisibleText(parts, preserveWhitespace = true)
    }

    private fun extractGeminiDiagnostics(response: JSONObject): GeminiResponseDiagnostics {
        val candidate = response.optJSONArray("candidates")?.optJSONObject(0)
        val usage = response.optJSONObject("usageMetadata")
        val promptFeedback = response.optJSONObject("promptFeedback")

        fun optionalInt(source: JSONObject?, key: String): Int? =
            source?.takeIf { it.has(key) && !it.isNull(key) }?.optInt(key)

        return GeminiResponseDiagnostics(
            finishReason = candidate?.optString("finishReason")?.trim()?.takeIf { it.isNotBlank() },
            blockReason = promptFeedback?.optString("blockReason")?.trim()?.takeIf { it.isNotBlank() },
            promptTokens = optionalInt(usage, "promptTokenCount"),
            candidateTokens = optionalInt(usage, "candidatesTokenCount")
                ?: optionalInt(candidate, "tokenCount"),
            thoughtTokens = optionalInt(usage, "thoughtsTokenCount"),
            totalTokens = optionalInt(usage, "totalTokenCount"),
        )
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
        val request = if (callbacks != null) {
            ApiTokenClient.chatStreaming(
                context = context,
                messages = messages,
                imagePaths = imagePaths,
                audioPath = audioPath,
                webRequested = webRequested,
                onToken = callbacks::onToken,
            )
        } else {
            ApiTokenClient.chat(
                context = context,
                messages = messages,
                imagePaths = imagePaths,
                audioPath = audioPath,
                webRequested = webRequested,
            )
        }
        return request.getOrElse {
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

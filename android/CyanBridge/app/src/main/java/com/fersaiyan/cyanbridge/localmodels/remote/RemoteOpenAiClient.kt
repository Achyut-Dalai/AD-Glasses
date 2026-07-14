package com.fersaiyan.cyanbridge.localmodels.remote

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Lightweight client for OpenAI-compatible chat/completions endpoints.
 *
 * Supports both non-streaming and streaming (SSE) responses.
 * Works with Ollama (/v1/chat/completions), llama.cpp server, vLLM, TGI
 * with the OpenAI compatibility layer, and any other server that speaks
 * the same protocol.
 */
object RemoteOpenAiClient {
    private const val TAG = "RemoteOpenAiClient"
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 120_000

    /**
     * Non-streaming chat completion.
     */
    suspend fun chatCompletion(
        context: Context,
        messages: List<Map<String, String>>,
        maxTokens: Int = 2048,
        temperature: Double = 0.7,
    ): String {
        val baseUrl = RemoteOpenAiPrefs.getBaseUrl(context)
        val apiKey = RemoteOpenAiPrefs.getApiKey(context)
        val model = RemoteOpenAiPrefs.getModel(context)

        require(baseUrl.isNotBlank()) { "Remote server base URL is not configured" }
        require(model.isNotBlank()) { "Remote server model name is not configured" }

        val messagesArray = JSONArray()
        for (m in messages) {
            val role = m["role"]?.lowercase() ?: "user"
            messagesArray.put(JSONObject().put("role", role).put("content", m["content"]))
        }

        val payload = JSONObject()
            .put("model", model)
            .put("messages", messagesArray)
            .put("max_tokens", maxTokens)
            .put("temperature", temperature)

        val url = buildUrl(baseUrl)
        Log.i(TAG, "chatCompletion -> $url model=$model")

        return postJson(url, apiKey, payload)
            .let { response ->
                val choices = response.optJSONArray("choices")
                    ?: throw IllegalStateException("No choices in remote response")
                if (choices.length() == 0) throw IllegalStateException("Empty choices array")
                val message = choices.getJSONObject(0).optJSONObject("message")
                message?.optString("content")?.trim()?.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("Empty content in remote response")
            }
    }

    /**
     * Streaming chat completion. Calls [onToken] for each chunk of text as it arrives.
     * Returns the full assembled response.
     */
    suspend fun chatCompletionStreaming(
        context: Context,
        messages: List<Map<String, String>>,
        maxTokens: Int = 2048,
        temperature: Double = 0.7,
        onToken: ((String) -> Unit)? = null,
    ): String {
        val baseUrl = RemoteOpenAiPrefs.getBaseUrl(context)
        val apiKey = RemoteOpenAiPrefs.getApiKey(context)
        val model = RemoteOpenAiPrefs.getModel(context)

        require(baseUrl.isNotBlank()) { "Remote server base URL is not configured" }
        require(model.isNotBlank()) { "Remote server model name is not configured" }

        val messagesArray = JSONArray()
        for (m in messages) {
            val role = m["role"]?.lowercase() ?: "user"
            messagesArray.put(JSONObject().put("role", role).put("content", m["content"]))
        }

        val payload = JSONObject()
            .put("model", model)
            .put("messages", messagesArray)
            .put("max_tokens", maxTokens)
            .put("temperature", temperature)
            .put("stream", true)

        val url = buildUrl(baseUrl)
        Log.i(TAG, "chatCompletionStreaming -> $url model=$model")

        return postJsonStreaming(url, apiKey, payload, onToken)
    }

    /**
     * Health check: tries to reach the server and list models.
     * Returns a human-readable status string.
     */
    suspend fun healthCheck(context: Context): String {
        val baseUrl = RemoteOpenAiPrefs.getBaseUrl(context)
        if (baseUrl.isBlank()) return "No base URL configured"

        val modelsUrl = baseUrl.trimEnd('/').replace("/v1$", "") + "/v1/models"
        return try {
            val conn = (URL(modelsUrl).openConnection() as HttpURLConnection)
            conn.requestMethod = "GET"
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = 10_000
            conn.setRequestProperty("Accept", "application/json")
            val apiKey = RemoteOpenAiPrefs.getApiKey(context)
            if (apiKey.isNotBlank()) {
                conn.setRequestProperty("Authorization", "Bearer $apiKey")
            }
            val code = conn.responseCode
            val body = BufferedReader(InputStreamReader(
                if (code in 200..299) conn.inputStream else conn.errorStream
            )).use { it.readText() }
            conn.disconnect()

            if (code !in 200..299) {
                "HTTP $code: ${body.take(200)}"
            } else {
                val obj = runCatching { JSONObject(body) }.getOrNull()
                val models = obj?.optJSONArray("data")
                val count = models?.length() ?: 0
                if (count > 0) {
                    val names = (0 until count).mapNotNull { i ->
                        models?.optJSONObject(i)?.optString("id")
                    }.take(5).joinToString(", ")
                    "OK ($count models: $names)"
                } else {
                    "OK (server reachable)"
                }
            }
        } catch (e: Exception) {
            "Unreachable: ${e.message}"
        }
    }

    private fun buildUrl(baseUrl: String): String {
        val clean = baseUrl.trimEnd('/')
        return if (clean.endsWith("/chat/completions")) {
            clean
        } else {
            "$clean/chat/completions"
        }
    }

    private fun postJson(url: String, apiKey: String, payload: JSONObject): JSONObject {
        val conn = (URL(url).openConnection() as HttpURLConnection)
        conn.requestMethod = "POST"
        conn.connectTimeout = CONNECT_TIMEOUT_MS
        conn.readTimeout = READ_TIMEOUT_MS
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        if (apiKey.isNotBlank()) {
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
        }
        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val body = BufferedReader(InputStreamReader(stream ?: conn.inputStream)).use { it.readText() }
        conn.disconnect()
        if (code !in 200..299) {
            throw IllegalStateException("Remote server HTTP $code: ${body.take(500)}")
        }
        return JSONObject(body)
    }

    /**
     * Streaming POST: reads SSE lines (`data: {...}`) and extracts content deltas.
     */
    private fun postJsonStreaming(
        url: String,
        apiKey: String,
        payload: JSONObject,
        onToken: ((String) -> Unit)?,
    ): String {
        val conn = (URL(url).openConnection() as HttpURLConnection)
        conn.requestMethod = "POST"
        conn.connectTimeout = CONNECT_TIMEOUT_MS
        conn.readTimeout = READ_TIMEOUT_MS
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        conn.setRequestProperty("Accept", "text/event-stream")
        if (apiKey.isNotBlank()) {
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
        }
        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }

        val code = conn.responseCode
        if (code !in 200..299) {
            val errBody = BufferedReader(InputStreamReader(conn.errorStream ?: conn.inputStream))
                .use { it.readText() }
            conn.disconnect()
            throw IllegalStateException("Remote server HTTP $code: ${errBody.take(500)}")
        }

        val result = StringBuilder()
        BufferedReader(InputStreamReader(conn.inputStream)).use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line ?: continue
                if (!l.startsWith("data:")) continue
                val data = l.removePrefix("data:").trim()
                if (data == "[DONE]") break
                if (data.isBlank()) continue

                val chunk = runCatching {
                    val obj = JSONObject(data)
                    val choices = obj.optJSONArray("choices") ?: return@runCatching ""
                    val delta = choices.optJSONObject(0)?.optJSONObject("delta") ?: return@runCatching ""
                    delta.optString("content", "")
                }.getOrDefault("")

                if (chunk.isNotBlank()) {
                    result.append(chunk)
                    onToken?.invoke(chunk)
                }
            }
        }
        conn.disconnect()
        return result.toString()
    }
}

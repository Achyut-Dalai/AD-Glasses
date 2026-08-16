package com.fersaiyan.cyanbridge.ai.orchestrator

import android.content.Context
import com.fersaiyan.cyanbridge.agent.CloudServerPrefs
import com.fersaiyan.cyanbridge.ai.router.AiProviderPrefs
import com.fersaiyan.cyanbridge.ai.router.RelayServerCapabilitiesClient
import com.fersaiyan.cyanbridge.shared.chat.ChatMessage
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Explicit web-grounded relay request. The existing generic chat path stays untouched;
 * AD only enters this path after AssistantWebPolicy selects fresh web information.
 *
 * Contract:
 * - relay advertises `web_search`, `webSearch`, or `grounding` from /capabilities
 * - /chat accepts webSearch=true (and grounding=true during migration)
 * - /chat returns the normal `reply` field, optionally with richer source data ignored here
 */
object RelayWebSearchClient {
    private const val CONNECT_TIMEOUT_MS = 7_000
    private const val READ_TIMEOUT_MS = 120_000

    suspend fun chat(
        context: Context,
        threadId: String,
        prompt: String,
        history: List<ChatMessage>,
    ): Result<String> = runCatching {
        val capabilities = RelayServerCapabilitiesClient.get(context).getOrThrow()
        check(capabilities.webSearch) {
            "Your configured relay does not advertise Web Search yet. Update the relay or choose a web-capable backend."
        }

        val messages = JSONArray().apply {
            history.forEach { message ->
                put(
                    JSONObject()
                        .put("role", message.role.name.lowercase())
                        .put("content", message.content),
                )
            }
        }
        val payload = JSONObject()
            .put("backend", AiProviderPrefs.getRelayBackend(context).wire)
            .put("chatId", threadId)
            .put("prompt", prompt)
            .put("messages", messages)
            .put("webSearch", true)
            .put("grounding", true)

        val baseUrl = AiProviderPrefs.getRelayBaseUrl(context).trim().trimEnd('/')
        require(baseUrl.startsWith("http://") || baseUrl.startsWith("https://")) {
            "Relay URL must start with http:// or https://"
        }
        val connection = URL("$baseUrl/chat").openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        CloudServerPrefs.getApiToken(context).takeIf { it.isNotBlank() }?.let {
            connection.setRequestProperty("Authorization", "Bearer $it")
        }
        OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = BufferedReader(InputStreamReader(stream ?: connection.inputStream)).use { it.readText() }
        connection.disconnect()
        if (code !in 200..299) throw IllegalStateException("Web Search relay HTTP $code: $body")

        JSONObject(body).optString("reply").ifBlank {
            throw IllegalStateException("Web Search relay returned an empty reply")
        }
    }
}

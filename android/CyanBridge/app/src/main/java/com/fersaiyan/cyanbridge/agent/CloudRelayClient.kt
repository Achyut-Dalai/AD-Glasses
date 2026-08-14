package com.fersaiyan.cyanbridge.agent

import android.content.Context
import com.fersaiyan.cyanbridge.ai.router.AiProviderPrefs
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

/** Small, provider-neutral client for discovery against the user's own relay. */
object CloudRelayClient {
    data class ModelOption(val id: String, val label: String)

    private const val CONNECT_TIMEOUT_MS = 7_000
    private const val READ_TIMEOUT_MS = 15_000
    private const val RELAY_DOWN_HINT =
        "Your cloud relay is unavailable. Check its URL, network access, and server status."

    fun fetchAvailableModels(context: Context): Result<List<ModelOption>> = runCatching {
        val seen = linkedMapOf<String, ModelOption>()
        var lastError: Throwable? = null
        for (path in listOf("/models", "/v1/models")) {
            val result = runCatching { parseModels(requestGetJson(context, endpoint(context, path))) }
            result.getOrNull().orEmpty().forEach { option ->
                seen.putIfAbsent(option.id.lowercase(), option)
            }
            if (result.isFailure) lastError = result.exceptionOrNull()
        }
        if (seen.isEmpty()) throw lastError ?: IllegalStateException("No models returned by your relay")
        seen.values.toList()
    }

    private fun parseModels(payload: JSONObject): List<ModelOption> {
        val out = linkedMapOf<String, ModelOption>()
        fun read(array: JSONArray?) {
            if (array == null) return
            for (index in 0 until array.length()) {
                val item = array.opt(index)
                val id: String
                val label: String
                when (item) {
                    is String -> {
                        id = item.trim()
                        label = id
                    }
                    is JSONObject -> {
                        id = sequenceOf("id", "model", "name")
                            .map { item.optString(it).trim() }
                            .firstOrNull { it.isNotBlank() }
                            .orEmpty()
                        label = sequenceOf("label", "display_name", "name")
                            .map { item.optString(it).trim() }
                            .firstOrNull { it.isNotBlank() }
                            ?: id
                    }
                    else -> continue
                }
                if (id.isNotBlank()) out.putIfAbsent(id.lowercase(), ModelOption(id, label))
            }
        }
        read(payload.optJSONArray("data"))
        read(payload.optJSONArray("models"))
        read(payload.optJSONObject("result")?.optJSONArray("models"))
        return out.values.toList()
    }

    private fun endpoint(context: Context, path: String): String {
        val base = AiProviderPrefs.getRelayBaseUrl(context).trim().trimEnd('/')
        require(base.startsWith("https://") || base.startsWith("http://")) {
            "Configure your cloud relay URL in Settings"
        }
        return "$base$path"
    }

    private fun requestGetJson(context: Context, url: String): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")
            CloudServerPrefs.getApiToken(context).takeIf { it.isNotBlank() }?.let {
                connection.setRequestProperty("Authorization", "Bearer $it")
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = BufferedReader(InputStreamReader(stream)).use { it.readText() }
            check(code in 200..299) { "HTTP $code: $body" }
            JSONObject(body.ifBlank { "{}" })
        } finally {
            connection.disconnect()
        }
    }

    fun relayUnavailableHint(error: Throwable?): String? {
        var current = error
        while (current != null) {
            if (current is UnknownHostException || current is ConnectException ||
                current is SocketTimeoutException || current is IOException
            ) return RELAY_DOWN_HINT
            current = current.cause
        }
        val message = error?.message.orEmpty().lowercase()
        return if (listOf("failed to connect", "connection refused", "timeout", "unreachable",
                "unable to resolve host", "no address associated", "relay unavailable")
                .any(message::contains)
        ) RELAY_DOWN_HINT else null
    }

    fun relayUnavailableHintFromText(text: String): String? =
        relayUnavailableHint(IllegalStateException(text))
}

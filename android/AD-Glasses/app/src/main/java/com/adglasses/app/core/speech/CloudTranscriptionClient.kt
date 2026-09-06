package com.adglasses.app.core.speech

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * Groq Whisper transcription matching the native iOS speech contract.
 *
 * Android keeps this as a cloud fallback/verifier behind the local Moonshine path rather than
 * replacing the local-first implementation. The API key should come from the existing encrypted
 * AI profile store, never from a second plaintext preference.
 */
class CloudTranscriptionClient {
    companion object {
        const val MODEL_LARGE_V3 = "whisper-large-v3"
        const val MODEL_LARGE_V3_TURBO = "whisper-large-v3-turbo"
        const val DEFAULT_MODEL = MODEL_LARGE_V3
        const val DEFAULT_BASE_URL = "https://api.groq.com/openai/v1"
        private const val MAX_AUDIO_SECONDS = 60
        private const val REQUEST_TIMEOUT_MS = 20_000
    }

    suspend fun transcribe(
        pcm16: ShortArray,
        apiKey: String,
        model: String = DEFAULT_MODEL,
        language: String? = null,
        prompt: String? = null,
        baseUrl: String = DEFAULT_BASE_URL,
    ): String = withContext(Dispatchers.IO) {
        require(pcm16.isNotEmpty()) { "There is no captured audio to transcribe" }
        require(pcm16.size <= GlassesOpusDecoder.SAMPLE_RATE * MAX_AUDIO_SECONDS) {
            "Voice turn exceeded the cloud-transcription safety limit"
        }
        require(apiKey.isNotBlank()) { "Groq Whisper needs an API key" }

        val endpoint = "${baseUrl.trim().trimEnd('/')}/audio/transcriptions"
        val boundary = "----ADGlasses${UUID.randomUUID().toString().replace("-", "")}"
        val wav = wavBytes(pcm16)
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = REQUEST_TIMEOUT_MS
            readTimeout = REQUEST_TIMEOUT_MS
            doOutput = true
            useCaches = false
            instanceFollowRedirects = false
            setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setRequestProperty("Accept", "application/json")
        }

        try {
            connection.outputStream.buffered().use { output ->
                fun textPart(name: String, value: String) {
                    output.write("--$boundary\r\n".toByteArray())
                    output.write("Content-Disposition: form-data; name=\"$name\"\r\n\r\n".toByteArray())
                    output.write(value.toByteArray())
                    output.write("\r\n".toByteArray())
                }

                output.write("--$boundary\r\n".toByteArray())
                output.write("Content-Disposition: form-data; name=\"file\"; filename=\"audio.wav\"\r\n".toByteArray())
                output.write("Content-Type: audio/wav\r\n\r\n".toByteArray())
                output.write(wav)
                output.write("\r\n".toByteArray())

                textPart("model", model.trim().ifBlank { DEFAULT_MODEL })
                textPart("response_format", "json")
                textPart("temperature", "0")
                language?.trim()?.takeIf { it.isNotEmpty() }?.let { textPart("language", it) }
                prompt?.trim()?.takeIf { it.isNotEmpty() }?.let { textPart("prompt", it) }
                output.write("--$boundary--\r\n".toByteArray())
            }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val message = runCatching {
                    val root = JSONObject(response)
                    root.optJSONObject("error")?.optString("message")
                        ?.takeIf { it.isNotBlank() }
                }.getOrNull()
                error(message ?: "Groq Whisper returned HTTP $status${response.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()}")
            }

            val text = JSONObject(response).optString("text").trim()
            require(text.isNotEmpty()) { "Groq Whisper returned no transcript" }
            text
        } finally {
            connection.disconnect()
        }
    }

    private fun wavBytes(pcm16: ShortArray): ByteArray {
        val dataBytes = pcm16.size * 2
        val output = ByteArrayOutputStream(44 + dataBytes)
        val data = DataOutputStream(output)
        fun leShort(value: Int) {
            data.write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value.toShort()).array())
        }
        fun leInt(value: Int) {
            data.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array())
        }

        data.writeBytes("RIFF")
        leInt(36 + dataBytes)
        data.writeBytes("WAVE")
        data.writeBytes("fmt ")
        leInt(16)
        leShort(1) // PCM
        leShort(1) // mono
        leInt(GlassesOpusDecoder.SAMPLE_RATE)
        leInt(GlassesOpusDecoder.SAMPLE_RATE * 2)
        leShort(2)
        leShort(16)
        data.writeBytes("data")
        leInt(dataBytes)
        pcm16.forEach { sample -> leShort(sample.toInt()) }
        data.flush()
        return output.toByteArray()
    }
}

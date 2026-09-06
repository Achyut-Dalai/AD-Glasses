package com.adglasses.app.integrations.heycyan

import android.net.Network
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private const val MAXIMUM_MANIFEST_BYTES = 1_048_576
private const val VENDOR_COMPATIBLE_USER_AGENT = "okhttp/4.9.2"

enum class HeyCyanMediaKind {
    Photo,
    Video,
    Audio,
}

data class HeyCyanMediaItem(
    val fileName: String,
    val kind: HeyCyanMediaKind,
)

class HeyCyanHttpStatusException(val statusCode: Int) : IOException("Glasses HTTP $statusCode")

/**
 * Client for the current production `/files/media.config` + `/files/<name>` contract.
 * Every connection is opened through the Network returned by the glasses Wi-Fi request, so Android
 * cannot accidentally send these private-host requests over cellular or another Wi-Fi network.
 */
class HeyCyanMediaClient {
    suspend fun listMedia(network: Network, deviceIp: String): List<HeyCyanMediaItem> = withContext(Dispatchers.IO) {
        val connection = open(network, "http://$deviceIp/files/media.config")
        try {
            validate(connection)
            val bytes = readBounded(connection, MAXIMUM_MANIFEST_BYTES)
            val content = bytes.toString(Charsets.UTF_8)
            content.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { fileName ->
                    requireSafeFileName(fileName)
                    fileName
                }
                .mapNotNull(::mediaItem)
                .distinctBy { it.fileName }
                .toList()
        } finally {
            connection.disconnect()
        }
    }

    /** Kept for diagnostics; normal Library sync should use [listMedia]. */
    suspend fun fetchMediaConfig(network: Network, deviceIp: String): String = withContext(Dispatchers.IO) {
        val connection = open(network, "http://$deviceIp/files/media.config")
        try {
            validate(connection)
            readBounded(connection, MAXIMUM_MANIFEST_BYTES).toString(Charsets.UTF_8)
        } finally {
            connection.disconnect()
        }
    }

    suspend fun download(
        network: Network,
        deviceIp: String,
        item: HeyCyanMediaItem,
        destination: File,
    ) = withContext(Dispatchers.IO) {
        requireSafeFileName(item.fileName)
        require(destination.name == item.fileName) { "Destination filename changed" }
        destination.parentFile?.mkdirs()
        val partial = File(destination.parentFile, "${destination.name}.part")
        partial.delete()

        val connection = open(network, "http://$deviceIp/files/${encodePathSegment(item.fileName)}")
        try {
            validate(connection)
            connection.inputStream.use { input ->
                partial.outputStream().buffered().use { output ->
                    input.copyTo(output, bufferSize = 64 * 1024)
                }
            }
            require(partial.length() > 0L) { "Glasses returned an empty media file" }
            if (destination.exists() && !destination.delete()) {
                error("Could not replace existing local media")
            }
            if (!partial.renameTo(destination)) {
                partial.copyTo(destination, overwrite = true)
                if (!partial.delete()) partial.deleteOnExit()
            }
        } catch (error: Throwable) {
            partial.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    suspend fun fetchFile(network: Network, deviceIp: String, remoteName: String): ByteArray = withContext(Dispatchers.IO) {
        requireSafeFileName(remoteName)
        val connection = open(network, "http://$deviceIp/files/${encodePathSegment(remoteName)}")
        try {
            validate(connection)
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    private fun readBounded(connection: HttpURLConnection, maximumBytes: Int): ByteArray {
        val output = ByteArrayOutputStream()
        connection.inputStream.use { input ->
            val buffer = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= maximumBytes) { "Glasses media manifest exceeded the safety limit" }
                output.write(buffer, 0, count)
            }
        }
        return output.toByteArray()
    }

    private fun validate(connection: HttpURLConnection) {
        val status = connection.responseCode
        if (status !in 200..299) throw HeyCyanHttpStatusException(status)
    }

    private fun open(network: Network, url: String): HttpURLConnection =
        (network.openConnection(URL(url)) as HttpURLConnection).apply {
            connectTimeout = 5_000
            readTimeout = 12_000
            instanceFollowRedirects = false
            useCaches = false
            setRequestProperty("User-Agent", VENDOR_COMPATIBLE_USER_AGENT)
            setRequestProperty("Connection", "keep-alive")
            setRequestProperty("Accept", "*/*")
        }

    private fun mediaItem(fileName: String): HeyCyanMediaItem? = when (fileName.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> HeyCyanMediaItem(fileName, HeyCyanMediaKind.Photo)
        "mp4" -> HeyCyanMediaItem(fileName, HeyCyanMediaKind.Video)
        "opus" -> HeyCyanMediaItem(fileName, HeyCyanMediaKind.Audio)
        else -> null
    }

    private fun requireSafeFileName(value: String) {
        require(
            value.isNotBlank() &&
                value.length <= 180 &&
                value != "." && value != ".." &&
                !value.contains("..") &&
                !value.contains('/') && !value.contains('\\') &&
                !value.contains('?') && !value.contains('#') &&
                !value.contains('%') && !value.contains(':') &&
                value.none { it.code < 0x20 || it.code == 0x7F }
        ) { "Unsafe media filename" }
    }

    private fun encodePathSegment(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
}

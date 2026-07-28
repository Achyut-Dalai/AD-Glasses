package com.fersaiyan.cyanbridge.shared.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

actual class PlatformHttpClient actual constructor() {

    actual suspend fun get(url: String, headers: Map<String, String>): HttpResponse = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            conn.connectTimeout = 30_000
            conn.readTimeout = 60_000
            conn.connect()
            readResponse(conn)
        } finally {
            conn.disconnect()
        }
    }

    actual suspend fun post(url: String, body: String, headers: Map<String, String>): HttpResponse = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            if (headers.containsKey("Content-Type").not()) {
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }
            conn.connectTimeout = 30_000
            conn.readTimeout = 60_000
            conn.connect()
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            readResponse(conn)
        } finally {
            conn.disconnect()
        }
    }

    actual suspend fun postMultipart(
        url: String,
        parts: Map<String, String>,
        files: Map<String, ByteArray>,
        headers: Map<String, String>,
    ): HttpResponse = withContext(Dispatchers.IO) {
        val boundary = "----CyanBridgeBoundary${System.currentTimeMillis()}"
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            conn.connectTimeout = 30_000
            conn.readTimeout = 60_000
            conn.connect()
            conn.outputStream.use { os ->
                parts.forEach { (name, value) ->
                    os.write("--$boundary\r\n".toByteArray())
                    os.write("Content-Disposition: form-data; name=\"$name\"\r\n\r\n".toByteArray())
                    os.write(value.toByteArray())
                    os.write("\r\n".toByteArray())
                }
                files.forEach { (name, data) ->
                    os.write("--$boundary\r\n".toByteArray())
                    os.write("Content-Disposition: form-data; name=\"$name\"; filename=\"$name\"\r\n".toByteArray())
                    os.write("Content-Type: application/octet-stream\r\n\r\n".toByteArray())
                    os.write(data)
                    os.write("\r\n".toByteArray())
                }
                os.write("--$boundary--\r\n".toByteArray())
            }
            readResponse(conn)
        } finally {
            conn.disconnect()
        }
    }

    actual suspend fun download(
        url: String,
        destinationPath: String,
        headers: Map<String, String>,
        onProgress: ((bytesReceived: Long, totalBytes: Long?) -> Unit)?,
    ): HttpResponse = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            conn.connectTimeout = 30_000
            conn.readTimeout = 300_000
            conn.connect()
            val totalBytes = conn.contentLength.toLong().takeIf { it > 0 }
            var bytesReceived = 0L
            FileOutputStream(File(destinationPath)).use { fos ->
                conn.inputStream.use { ins ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (ins.read(buffer).also { read = it } != -1) {
                        fos.write(buffer, 0, read)
                        bytesReceived += read
                        onProgress?.invoke(bytesReceived, totalBytes)
                    }
                }
            }
            HttpResponse(
                statusCode = conn.responseCode,
                headers = conn.headerFields,
                body = "",
                downloadedFileSize = bytesReceived,
            )
        } finally {
            conn.disconnect()
        }
    }

    actual fun close() {}

    private fun readResponse(conn: HttpURLConnection): HttpResponse {
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
        return HttpResponse(
            statusCode = code,
            headers = conn.headerFields,
            body = body,
            bodyBytes = body.toByteArray(),
        )
    }
}

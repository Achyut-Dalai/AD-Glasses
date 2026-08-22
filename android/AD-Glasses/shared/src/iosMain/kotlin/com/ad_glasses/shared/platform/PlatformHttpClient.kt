package com.ad_glasses.shared.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSFileManager
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.HTTPBody
import platform.Foundation.HTTPMethod
import platform.Foundation.allHTTPHeaderFields
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.dataUsingEncoding
import platform.Foundation.setValue

@OptIn(ExperimentalForeignApi::class)
actual class PlatformHttpClient actual constructor() {

    actual suspend fun get(url: String, headers: Map<String, String>): HttpResponse {
        val request = NSMutableURLRequest(NSURL(string = url))
        request.HTTPMethod = "GET"
        headers.forEach { (k, v) -> request.setValue(v, forHTTPHeaderField = k) }
        return executeRequest(request)
    }

    actual suspend fun post(url: String, body: String, headers: Map<String, String>): HttpResponse {
        val request = NSMutableURLRequest(NSURL(string = url))
        request.HTTPMethod = "POST"
        request.HTTPBody = (body as NSString).dataUsingEncoding(NSUTF8StringEncoding)
        headers.forEach { (k, v) -> request.setValue(v, forHTTPHeaderField = k) }
        if (!headers.containsKey("Content-Type")) {
            request.setValue("application/json; charset=UTF-8", forHTTPHeaderField = "Content-Type")
        }
        return executeRequest(request)
    }

    actual suspend fun postMultipart(
        url: String,
        parts: Map<String, String>,
        files: Map<String, ByteArray>,
        headers: Map<String, String>,
    ): HttpResponse {
        val boundary = "----ADGlassesBoundary${platform.Foundation.NSDate().timeIntervalSinceReferenceDate.toLong()}"
        val request = NSMutableURLRequest(NSURL(string = url))
        request.HTTPMethod = "POST"
        request.setValue("multipart/form-data; boundary=$boundary", forHTTPHeaderField = "Content-Type")
        headers.forEach { (k, v) -> request.setValue(v, forHTTPHeaderField = k) }

        val chunks = mutableListOf<ByteArray>()
        fun appendText(value: String) {
            chunks += value.encodeToByteArray()
        }

        parts.forEach { (name, value) ->
            appendText("--$boundary\r\n")
            appendText("Content-Disposition: form-data; name=\"$name\"\r\n\r\n")
            appendText("$value\r\n")
        }
        files.forEach { (name, data) ->
            appendText("--$boundary\r\n")
            appendText("Content-Disposition: form-data; name=\"$name\"; filename=\"$name\"\r\n")
            appendText("Content-Type: application/octet-stream\r\n\r\n")
            chunks += data.copyOf()
            appendText("\r\n")
        }
        appendText("--$boundary--\r\n")
        val body = ByteArray(chunks.sumOf { it.size })
        var offset = 0
        chunks.forEach { chunk ->
            chunk.copyInto(body, destinationOffset = offset)
            offset += chunk.size
        }
        request.HTTPBody = body.toIosNSData()
        return executeRequest(request)
    }

    actual suspend fun download(
        url: String,
        destinationPath: String,
        headers: Map<String, String>,
        onProgress: ((bytesReceived: Long, totalBytes: Long?) -> Unit)?,
    ): HttpResponse {
        val request = NSMutableURLRequest(NSURL(string = url))
        request.HTTPMethod = "GET"
        headers.forEach { (k, v) -> request.setValue(v, forHTTPHeaderField = k) }
        val response = executeRequest(request)
        val downloadedFileSize = if (response.isSuccessful) response.bodyBytes?.let { bytes ->
            val fileManager = NSFileManager.defaultManager
            if (fileManager.fileExistsAtPath(destinationPath)) {
                fileManager.removeItemAtPath(destinationPath, error = null)
            }
            val stored = fileManager.createFileAtPath(
                path = destinationPath,
                contents = bytes.toIosNSData(),
                attributes = null,
            )
            check(stored) { "Unable to store downloaded file at $destinationPath" }
            onProgress?.invoke(bytes.size.toLong(), bytes.size.toLong())
            bytes.size.toLong()
        } else {
            null
        }
        return response.copy(downloadedFileSize = downloadedFileSize)
    }

    actual fun close() {}

    private suspend fun executeRequest(request: NSMutableURLRequest): HttpResponse = suspendCancellableCoroutine { cont ->
        val session = NSURLSession.sharedSession()
        val task = session.dataTaskWithRequest(request) { data, response, error ->
            if (error != null) {
                cont.resumeWith(Result.failure(Exception(error.localizedDescription)))
                return@dataTaskWithRequest
            }
            val httpResponse = response as? NSHTTPURLResponse
            val statusCode = httpResponse?.statusCode?.toInt() ?: -1
            val headerMap = httpResponse?.allHeaderFields?.mapKeys { it.key.toString() }
                ?.mapValues { listOf(it.value.toString()) } ?: emptyMap()
            val body = data?.let { nsData ->
                val bytes = nsData.toByteArray()
                bytes?.decodeToString() ?: ""
            } ?: ""

            cont.resumeWith(Result.success(
                HttpResponse(
                    statusCode = statusCode,
                    headers = headerMap,
                    body = body,
                    bodyBytes = data?.toByteArray(),
                )
            ))
        }
        cont.invokeOnCancellation { task.cancel() }
        task.resume()
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray? {
    if (length == 0uL) return ByteArray(0)
    val ptr = this.bytes ?: return null
    return ptr.readBytes(length.toInt())
}

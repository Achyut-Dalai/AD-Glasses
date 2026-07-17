package com.fersaiyan.cyanbridge.shared.platform

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.readBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSHTTPURLResponse
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
import platform.Foundation.readData
import platform.Foundation.setValue
import platform.Foundation.writeToFile
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.timeoutIntervalForRequest
import platform.Foundation.timeoutIntervalForResource

actual class PlatformHttpClient actual constructor() {

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    actual suspend fun get(url: String, headers: Map<String, String>): HttpResponse {
        val request = NSMutableURLRequest(NSURL(string = url))
        request.HTTPMethod = "GET"
        headers.forEach { (k, v) -> request.setValue(v, forHTTPHeaderField = k) }
        return executeRequest(request)
    }

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    actual suspend fun post(url: String, body: String, headers: Map<String, String>): HttpResponse {
        val request = NSMutableURLRequest(NSURL(string = url))
        request.HTTPMethod = "POST"
        request.HTTPBody = (body as NSString).dataUsingEncoding(NSUTF8StringEncoding)
        headers.forEach { (k, v) -> request.setValue(v, forHTTPHeaderField = k) }
        if (headers.containsKey("Content-Type").not()) {
            request.setValue("application/json; charset=UTF-8", forHTTPHeaderField = "Content-Type")
        }
        return executeRequest(request)
    }

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    actual suspend fun postMultipart(
        url: String,
        parts: Map<String, String>,
        files: Map<String, ByteArray>,
        headers: Map<String, String>,
    ): HttpResponse {
        val boundary = "----CyanBridgeBoundary${platform.Foundation.NSDate.date().timeIntervalSince1970.toLong()}"
        val request = NSMutableURLRequest(NSURL(string = url))
        request.HTTPMethod = "POST"
        request.setValue("multipart/form-data; boundary=$boundary", forHTTPHeaderField = "Content-Type")
        headers.forEach { (k, v) -> request.setValue(v, forHTTPHeaderField = k) }

        val bodyBuilder = StringBuilder()
        parts.forEach { (name, value) ->
            bodyBuilder.append("--$boundary\r\n")
            bodyBuilder.append("Content-Disposition: form-data; name=\"$name\"\r\n\r\n")
            bodyBuilder.append("$value\r\n")
        }
        // Note: file data would need NSData handling for binary; simplified for string-only parts
        bodyBuilder.append("--$boundary--\r\n")
        request.HTTPBody = (bodyBuilder.toString() as NSString).dataUsingEncoding(NSUTF8StringEncoding)
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
        request.timeoutIntervalForRequest = 300.0
        return executeRequest(request)
    }

    actual fun close() {}

    @OptIn(ExperimentalForeignApi::class)
    private suspend fun executeRequest(request: NSMutableURLRequest): HttpResponse = withContext(Dispatchers.Main) {
        val config = NSURLSessionConfiguration.defaultSessionConfiguration()
        config.timeoutIntervalForRequest = 30.0
        config.timeoutIntervalForResource = 60.0
        val session = NSURLSession.sessionWithConfiguration(config)

        suspendCancellableCoroutine { cont ->
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
                    val bytes = nsData.readBytes()
                    String(bytes, Charsets.UTF_8)
                } ?: ""

                cont.resumeWith(Result.success(
                    HttpResponse(
                        statusCode = statusCode,
                        headers = headerMap,
                        body = body,
                        bodyBytes = data?.readBytes(),
                    )
                ))
            }
            task.resume()
        }
    }
}

// Helper for suspendCancellableCoroutine
import kotlinx.coroutines.suspendCancellableCoroutine

private suspend fun <T> suspendCancellableCoroutine(block: (kotlinx.coroutines.CancellableContinuation<T>) -> Unit): T =
    kotlinx.coroutines.suspendCancellableCoroutine(block)

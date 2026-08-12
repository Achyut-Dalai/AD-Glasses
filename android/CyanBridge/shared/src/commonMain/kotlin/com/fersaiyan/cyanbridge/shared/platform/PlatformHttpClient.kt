package com.achyut.adglasses.shared.platform

/**
 * Cross-platform HTTP client abstraction.
 * Android uses OkHttp/HttpURLConnection; iOS uses NSURLSession.
 */
expect class PlatformHttpClient() {
    /**
     * Perform an HTTP GET request.
     * @param url The URL to fetch
     * @param headers Optional request headers
     * @return HttpResponse with status code, headers, and body
     */
    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): HttpResponse

    /**
     * Perform an HTTP POST request with a JSON body.
     * @param url The URL to post to
     * @param body The request body as a string
     * @param headers Optional request headers
     * @return HttpResponse with status code, headers, and body
     */
    suspend fun post(url: String, body: String, headers: Map<String, String> = emptyMap()): HttpResponse

    /**
     * Perform an HTTP POST request with multipart form data.
     * @param url The URL to post to
     * @param parts Map of field name to value
     * @param files Map of field name to file data
     * @param headers Optional request headers
     * @return HttpResponse with status code, headers, and body
     */
    suspend fun postMultipart(
        url: String,
        parts: Map<String, String> = emptyMap(),
        files: Map<String, ByteArray> = emptyMap(),
        headers: Map<String, String> = emptyMap(),
    ): HttpResponse

    /**
     * Download a file from a URL to a local path.
     * @param url The URL to download from
     * @param destinationPath Local file path to write to
     * @param headers Optional request headers
     * @param onProgress Optional progress callback (bytesReceived, totalBytes)
     * @return HttpResponse with status code
     */
    suspend fun download(
        url: String,
        destinationPath: String,
        headers: Map<String, String> = emptyMap(),
        onProgress: ((bytesReceived: Long, totalBytes: Long?) -> Unit)? = null,
    ): HttpResponse

    /** Release any resources held by this client. */
    fun close()
}

data class HttpResponse(
    val statusCode: Int,
    val headers: Map<String, List<String>>,
    val body: String,
    val bodyBytes: ByteArray? = null,
    val downloadedFileSize: Long? = null,
) {
    val isSuccessful: Boolean get() = statusCode in 200..299
}

package com.fersaiyan.cyanbridge.plugins.walkingaid

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class WalkingAidModelDownloadProgress(
    val downloadedBytes: Long,
    val totalBytes: Long,
) {
    val percent: Int
        get() = if (totalBytes <= 0L) 0 else ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
}

object WalkingAidModelInstaller {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .build()

    fun installedFile(context: Context, entry: WalkingAidModelCatalogEntry): File =
        File(context.filesDir, entry.expectedFilename)

    fun isInstalled(context: Context, entry: WalkingAidModelCatalogEntry): Boolean =
        installedFile(context, entry).let { it.exists() && it.length() >= 1024L }

    suspend fun install(
        context: Context,
        entry: WalkingAidModelCatalogEntry,
        onProgress: (WalkingAidModelDownloadProgress) -> Unit,
    ) {
        val target = installedFile(context, entry)
        val partial = File(target.parentFile, "${target.name}.part")
        val requiredBytes = entry.sizeBytes + 250L * 1024L * 1024L
        check(context.filesDir.usableSpace >= requiredBytes) { "Not enough free storage for ${entry.displayName}" }
        partial.delete()

        client.newCall(Request.Builder().url(entry.sourceUrl).build()).execute().use { response ->
            check(response.isSuccessful) { "Download failed: HTTP ${response.code}" }
            val body = requireNotNull(response.body) { "Download failed: empty response" }
            val totalBytes = body.contentLength().takeIf { it > 0L } ?: entry.sizeBytes
            body.byteStream().use { input ->
                FileOutputStream(partial).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloadedBytes = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count <= 0) break
                        output.write(buffer, 0, count)
                        downloadedBytes += count
                        onProgress(WalkingAidModelDownloadProgress(downloadedBytes, totalBytes))
                    }
                    output.flush()
                    check(downloadedBytes >= 1024L) { "Downloaded model is incomplete" }
                    if (body.contentLength() > 0L) {
                        check(downloadedBytes == body.contentLength()) { "Downloaded model is incomplete" }
                    }
                }
            }
        }

        if (target.exists()) target.delete()
        check(partial.renameTo(target)) { "Could not install ${entry.displayName}" }
    }
}

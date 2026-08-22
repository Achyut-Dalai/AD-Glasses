package com.ad_glasses.shared.media

import com.ad_glasses.shared.persistence.MediaRecordEntity
import com.ad_glasses.shared.persistence.MediaRecordRepository
import com.ad_glasses.shared.platform.PlatformFilePaths
import com.ad_glasses.shared.platform.PlatformHttpClient
import com.ad_glasses.shared.platform.PlatformLogger
import com.ad_glasses.shared.platform.platformCurrentTimeMillis
import platform.Foundation.NSFileManager
import kotlinx.cinterop.ExperimentalForeignApi

/**
 * iOS implementation of the glasses HTTP media protocol.
 *
 * BLE supplies the glasses IP; this class deliberately does not guess a
 * group-owner address. It downloads media.config, validates each filename,
 * stores files in the app Documents directory, and records them for the
 * shared gallery presentation.
 */
@OptIn(ExperimentalForeignApi::class)
class IosMediaTransfer(
    private val repository: MediaRecordRepository,
    private val httpClient: PlatformHttpClient = PlatformHttpClient(),
) {
    suspend fun sync(
        glassesIpAddress: String,
        onProgress: ((completed: Int, total: Int) -> Unit)? = null,
    ): List<MediaRecordEntity> {
        val baseUrl = normalizeBaseUrl(glassesIpAddress)
        val configResponse = httpClient.get("$baseUrl/files/media.config")
        check(configResponse.isSuccessful) {
            "media.config request failed with HTTP ${configResponse.statusCode}"
        }

        val filenames = configResponse.body
            .lineSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() }
            .mapNotNull(::safeFilename)
            .distinct()
            .toList()
        val destinationDirectory = PlatformFilePaths.dataDirectory() + "/AD-GlassesMedia"
        NSFileManager.defaultManager.createDirectoryAtPath(
            path = destinationDirectory,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )

        val downloaded = mutableListOf<MediaRecordEntity>()
        filenames.forEachIndexed { index, filename ->
            val destination = "$destinationDirectory/$filename"
            val existing = repository.getByFilename(filename)
            val existsOnDisk = NSFileManager.defaultManager.fileExistsAtPath(destination)
            if (existing != null && existsOnDisk) {
                downloaded += existing
                onProgress?.invoke(index + 1, filenames.size)
                return@forEachIndexed
            }

            val response = httpClient.download(
                url = "$baseUrl/files/${filename.encodePathSegment()}",
                destinationPath = destination,
            )
            check(response.isSuccessful) {
                "Media download failed for $filename with HTTP ${response.statusCode}"
            }
            val record = MediaRecordEntity(
                id = filename,
                filename = filename,
                mimeType = mimeTypeFor(filename),
                filePath = destination,
                downloadedAt = platformCurrentTimeMillis(),
                fileSize = response.downloadedFileSize ?: response.bodyBytes?.size?.toLong() ?: -1L,
                source = "glasses-ios",
            )
            repository.insert(record)
            downloaded += record
            onProgress?.invoke(index + 1, filenames.size)
        }
        PlatformLogger.i(TAG, "Downloaded ${downloaded.size} media files from $baseUrl")
        return downloaded
    }

    private fun normalizeBaseUrl(value: String): String {
        val trimmed = value.trim().removeSuffix("/")
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "http://$trimmed"
        }
    }

    companion object {
        private const val TAG = "IosMediaTransfer"

        private fun safeFilename(value: String): String? {
            if (value == "." || value == "..") return null
            if (value.contains('/') || value.contains('\\') || value.contains("..")) return null
            return value
        }

        private fun mimeTypeFor(filename: String): String = when {
            filename.endsWith(".jpg", ignoreCase = true) || filename.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
            filename.endsWith(".png", ignoreCase = true) -> "image/png"
            filename.endsWith(".mp4", ignoreCase = true) -> "video/mp4"
            filename.endsWith(".opus", ignoreCase = true) -> "audio/ogg"
            else -> "application/octet-stream"
        }
    }
}

private fun String.encodePathSegment(): String = buildString(length) {
    for (character in this@encodePathSegment) {
        when (character) {
            ' ' -> append("%20")
            '#' -> append("%23")
            '?' -> append("%3F")
            '%' -> append("%25")
            else -> append(character)
        }
    }
}

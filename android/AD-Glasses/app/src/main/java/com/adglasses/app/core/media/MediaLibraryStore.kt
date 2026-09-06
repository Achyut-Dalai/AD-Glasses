package com.adglasses.app.core.media

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

enum class LocalMediaKind {
    Photo,
    Video,
    Audio,
}

data class LocalMediaItem(
    val fileName: String,
    val kind: LocalMediaKind,
    val file: File,
    val bytes: Long,
    val modifiedAtEpochMs: Long,
)

/**
 * App-private store for byte-for-byte originals downloaded from the glasses.
 * Analysis results, thumbnails and other derivatives must live elsewhere and never overwrite these files.
 */
class MediaLibraryStore(context: Context) {
    private val originalsDirectory = File(context.applicationContext.filesDir, "library/originals").apply { mkdirs() }
    private val _items = MutableStateFlow(scan())
    val items: StateFlow<List<LocalMediaItem>> = _items.asStateFlow()

    fun hasOriginal(fileName: String): Boolean {
        val file = originalFile(fileName)
        return file.isFile && file.length() > 0L
    }

    fun originalFile(fileName: String): File {
        requireSafeFileName(fileName)
        return File(originalsDirectory, fileName)
    }

    fun refresh() {
        _items.value = scan()
    }

    private fun scan(): List<LocalMediaItem> = originalsDirectory
        .listFiles()
        .orEmpty()
        .asSequence()
        .filter { it.isFile && it.length() > 0L }
        .mapNotNull { file ->
            val kind = when (file.extension.lowercase()) {
                "jpg", "jpeg" -> LocalMediaKind.Photo
                "mp4" -> LocalMediaKind.Video
                "opus" -> LocalMediaKind.Audio
                else -> null
            } ?: return@mapNotNull null
            LocalMediaItem(
                fileName = file.name,
                kind = kind,
                file = file,
                bytes = file.length(),
                modifiedAtEpochMs = file.lastModified(),
            )
        }
        .sortedByDescending { it.modifiedAtEpochMs }
        .toList()

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
}

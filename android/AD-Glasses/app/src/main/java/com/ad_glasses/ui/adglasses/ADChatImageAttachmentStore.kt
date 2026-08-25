package com.ad_glasses.ui.adglasses

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Copies a picker URI into app cache so the asynchronous AI turn owns a stable file path.
 * The staged source is one-shot: callers delete it after the image request finishes.
 */
internal object ADChatImageAttachmentStore {
    private const val MAX_SOURCE_BYTES = 24L * 1024L * 1024L

    fun stage(context: Context, uri: Uri): String {
        val resolver = context.contentResolver
        val extension = extensionForMime(resolver.getType(uri))
            ?: throw IllegalArgumentException("Unsupported image type")
        val directory = File(context.cacheDir, "chat-image-input").apply { mkdirs() }
        val output = File(directory, "chat-${UUID.randomUUID()}.$extension")

        try {
            val input = resolver.openInputStream(uri)
                ?: throw IllegalArgumentException("The selected image could not be opened")
            input.use { source ->
                FileOutputStream(output).use { target ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val read = source.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_SOURCE_BYTES) {
                            throw IllegalArgumentException("The selected image is too large")
                        }
                        target.write(buffer, 0, read)
                    }
                }
            }
            check(output.length() > 0L) { "The selected image was empty" }
            return output.absolutePath
        } catch (error: Throwable) {
            runCatching { output.delete() }
            throw error
        }
    }

    fun delete(path: String?) {
        path?.takeIf { it.isNotBlank() }?.let { runCatching { File(it).delete() } }
    }

    private fun extensionForMime(mimeType: String?): String? = when (mimeType?.trim()?.lowercase()) {
        "image/jpeg", "image/jpg" -> "jpg"
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        "image/heic" -> "heic"
        "image/heif" -> "heif"
        "image/avif" -> "avif"
        else -> null
    }
}

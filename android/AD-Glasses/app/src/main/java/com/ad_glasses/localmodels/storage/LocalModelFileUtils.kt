package com.ad_glasses.localmodels.storage

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest

enum class LocalModelFileFormat(val registryValue: String) {
    GGUF("gguf"),
    LITERT("litert"),
    UNKNOWN("unknown"),
    ;

    companion object {
        fun fromRegistryValue(value: String?): LocalModelFileFormat? = when (value?.trim()?.lowercase()) {
            "gguf" -> GGUF
            "litert", "litertlm", "task" -> LITERT
            "unknown" -> UNKNOWN
            else -> null
        }
    }
}

object LocalModelFileUtils {
    private val unsafeChars = Regex("[^A-Za-z0-9._-]+")
    private val knownExtensions = listOf(".gguf", ".litertlm", ".task")

    fun sanitizeFileName(fileName: String, defaultExtension: String = ".gguf"): String {
        val normalizedDefault = if (defaultExtension.startsWith(".")) {
            defaultExtension.lowercase()
        } else {
            ".${defaultExtension.lowercase()}"
        }
        val trimmed = fileName.trim().ifBlank { "model$normalizedDefault" }
        val replaced = trimmed.replace(unsafeChars, "_")
        val withExtension = if (knownExtensions.any { replaced.endsWith(it, ignoreCase = true) }) {
            replaced
        } else {
            "$replaced$normalizedDefault"
        }
        if (withExtension.length <= MAX_MANAGED_FILE_NAME_LENGTH) return withExtension
        val extension = knownExtensions.firstOrNull { withExtension.endsWith(it, ignoreCase = true) }
            ?: normalizedDefault
        val baseLength = (MAX_MANAGED_FILE_NAME_LENGTH - extension.length).coerceAtLeast(1)
        return withExtension.removeSuffix(extension).take(baseLength) + extension
    }

    fun isGgufFile(file: File): Boolean {
        if (!file.exists() || !file.isFile || file.length() < 4) return false
        return runCatching {
            FileInputStream(file).use { input ->
                val header = ByteArray(4)
                if (input.read(header) != 4) return false
                header[0] == 'G'.code.toByte() &&
                    header[1] == 'G'.code.toByte() &&
                    header[2] == 'U'.code.toByte() &&
                    header[3] == 'F'.code.toByte()
            }
        }.getOrDefault(false)
    }

    fun isLiteRtPackageFile(file: File): Boolean {
        if (!file.exists() || !file.isFile || file.length() <= 1_048_576L) return false
        val name = file.name.lowercase()
        val extensionOk = name.endsWith(".litertlm") ||
            name.endsWith(".task") ||
            name.endsWith(".litertlm.part") ||
            name.endsWith(".task.part")
        if (!extensionOk) return false
        if (looksLikeTextOrHtml(file)) return false
        return true
    }

    private fun looksLikeTextOrHtml(file: File): Boolean {
        return runCatching {
            FileInputStream(file).use { input ->
                val sample = ByteArray(512)
                val n = input.read(sample)
                if (n <= 0) return@use true
                val head = String(sample, 0, n, Charsets.UTF_8).trimStart().lowercase()
                head.startsWith("<!doctype html") ||
                    head.startsWith("<html") ||
                    head.startsWith("<xml") ||
                    head.startsWith("{\"error\"") ||
                    head.startsWith("{\"message\"")
            }
        }.getOrDefault(true)
    }

    fun isSupportedModelFile(file: File): Boolean {
        return isGgufFile(file) || isLiteRtPackageFile(file)
    }

    fun formatFromFileName(fileName: String?): LocalModelFileFormat? {
        val normalized = fileName?.trim()?.lowercase().orEmpty()
        return when {
            normalized.endsWith(".gguf") -> LocalModelFileFormat.GGUF
            normalized.endsWith(".litertlm") || normalized.endsWith(".task") -> LocalModelFileFormat.LITERT
            else -> null
        }
    }

    fun detectFormat(file: File): LocalModelFileFormat? = when {
        isGgufFile(file) -> LocalModelFileFormat.GGUF
        isLiteRtPackageFile(file) -> LocalModelFileFormat.LITERT
        else -> null
    }

    fun hasSupportedModelExtension(fileName: String?): Boolean = formatFromFileName(fileName) != null

    fun isFileCompatibleWithFormat(file: File, format: String?): Boolean {
        return when (format?.lowercase()) {
            "gguf" -> isGgufFile(file)
            "litertlm", "task", "litert" -> isLiteRtPackageFile(file)
            else -> isSupportedModelFile(file)
        }
    }

    /**
     * Copies [input] to [target] while enforcing the storage budget established before import.
     * Any incomplete target is removed before the error is returned to the caller.
     */
    fun copyToFileWithLimit(
        input: InputStream,
        target: File,
        maxBytes: Long,
        onProgress: ((copiedBytes: Long) -> Unit)? = null,
    ): Long {
        require(maxBytes > 0L) { "Not enough free storage to import this model" }
        target.parentFile?.mkdirs()
        var completed = false
        try {
            val copied = FileOutputStream(target).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count <= 0) break
                    if (total > maxBytes - count) {
                        throw IllegalStateException("Model import exceeds available storage")
                    }
                    output.write(buffer, 0, count)
                    total += count
                    onProgress?.invoke(total)
                }
                output.flush()
                output.fd.sync()
                total
            }
            require(copied > 0L) { "The selected model file is empty" }
            completed = true
            return copied
        } finally {
            if (!completed) target.delete()
        }
    }

    fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buf = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString(separator = "") { b -> "%02x".format(b) }
    }

    private const val MAX_MANAGED_FILE_NAME_LENGTH = 160
}

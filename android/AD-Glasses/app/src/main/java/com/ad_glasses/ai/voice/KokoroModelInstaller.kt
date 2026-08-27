package com.ad_glasses.ai.voice

import android.content.Context
import com.ad_glasses.shared.voice.KokoroHeartVoice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

internal data class KokoroModelFiles(
    val root: File,
    val model: File,
    val voices: File,
    val tokens: File,
    val espeakData: File,
    val englishLexicon: File,
)

/**
 * Downloads and installs the fp32 Kokoro multilingual model on first use.
 *
 * The model is deliberately kept out of the APK and in noBackupFilesDir. That keeps normal APK
 * iteration reasonable and avoids Android restoring hundreds of MB of generated model data.
 */
internal object KokoroModelInstaller {
    private const val READY_MARKER = ".ad-glasses-kokoro-fp32-v1.ready"
    private const val ARCHIVE_NAME = "kokoro-multi-lang-v1_0.tar.bz2"
    private const val IO_BUFFER_SIZE = 128 * 1024

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.MINUTES)
        .callTimeout(20, TimeUnit.MINUTES)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun ensureInstalled(
        context: Context,
        onProgress: ((downloadedBytes: Long, totalBytes: Long) -> Unit)? = null,
    ): KokoroModelFiles = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val modelsRoot = File(appContext.noBackupFilesDir, "voice-models").apply { mkdirs() }
        val target = File(modelsRoot, KokoroHeartVoice.MODEL_ID)
        findReadyModel(target)?.let { return@withContext it }

        val staging = File(modelsRoot, ".${KokoroHeartVoice.MODEL_ID}.staging")
        val archive = File(appContext.cacheDir, ARCHIVE_NAME)
        staging.deleteRecursively()
        staging.mkdirs()

        try {
            downloadArchive(archive, onProgress)
            extractArchive(archive, staging)

            val extractedRoot = File(staging, KokoroHeartVoice.MODEL_ID)
            val files = requireModelFiles(extractedRoot)
            File(extractedRoot, READY_MARKER).writeText(
                "model=${KokoroHeartVoice.MODEL_ID}\nvoice=${KokoroHeartVoice.VOICE_ID}\nspeaker=${KokoroHeartVoice.SPEAKER_ID}\n",
            )

            target.deleteRecursively()
            if (!extractedRoot.renameTo(target)) {
                extractedRoot.copyRecursively(target, overwrite = true)
                extractedRoot.deleteRecursively()
            }
            requireModelFiles(target)
        } finally {
            archive.delete()
            staging.deleteRecursively()
        }
    }

    fun installedModel(context: Context): KokoroModelFiles? {
        val target = File(
            File(context.applicationContext.noBackupFilesDir, "voice-models"),
            KokoroHeartVoice.MODEL_ID,
        )
        return findReadyModel(target)
    }

    private fun findReadyModel(root: File): KokoroModelFiles? {
        if (!File(root, READY_MARKER).isFile) return null
        return runCatching { requireModelFiles(root) }.getOrNull()
    }

    private fun requireModelFiles(root: File): KokoroModelFiles {
        val model = File(root, "model.onnx")
        val voices = File(root, "voices.bin")
        val tokens = File(root, "tokens.txt")
        val espeakData = File(root, "espeak-ng-data")
        val englishLexicon = File(root, "lexicon-us-en.txt")

        val missing = buildList {
            if (!model.isFile) add(model.name)
            if (!voices.isFile) add(voices.name)
            if (!tokens.isFile) add(tokens.name)
            if (!espeakData.isDirectory) add(espeakData.name)
            if (!englishLexicon.isFile) add(englishLexicon.name)
        }
        check(missing.isEmpty()) {
            "Incomplete Kokoro model install; missing ${missing.joinToString()} in ${root.absolutePath}"
        }
        check(!model.name.contains("int8", ignoreCase = true)) {
            "AD Glasses intentionally requires the fp32 Kokoro model on Android"
        }

        return KokoroModelFiles(
            root = root,
            model = model,
            voices = voices,
            tokens = tokens,
            espeakData = espeakData,
            englishLexicon = englishLexicon,
        )
    }

    private fun downloadArchive(
        destination: File,
        onProgress: ((downloadedBytes: Long, totalBytes: Long) -> Unit)?,
    ) {
        val request = Request.Builder()
            .url(KokoroHeartVoice.MODEL_ARCHIVE_URL)
            .header("User-Agent", "AD-Glasses/KokoroModelInstaller")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Kokoro model download failed: HTTP ${response.code}")
            }
            val body = response.body ?: throw IOException("Kokoro model download returned no body")
            val totalBytes = body.contentLength()
            destination.parentFile?.mkdirs()

            body.byteStream().use { input ->
                BufferedOutputStream(FileOutputStream(destination), IO_BUFFER_SIZE).use { output ->
                    val buffer = ByteArray(IO_BUFFER_SIZE)
                    var downloaded = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloaded += count
                        onProgress?.invoke(downloaded, totalBytes)
                    }
                }
            }
        }
    }

    private fun extractArchive(archive: File, stagingRoot: File) {
        FileInputStream(archive).use { fileInput ->
            BufferedInputStream(fileInput, IO_BUFFER_SIZE).use { buffered ->
                BZip2CompressorInputStream(buffered, true).use { bzip ->
                    TarArchiveInputStream(bzip).use { tar ->
                        while (true) {
                            val entry = tar.nextTarEntry ?: break
                            val output = safeOutputFile(stagingRoot, entry.name)
                            if (entry.isDirectory) {
                                output.mkdirs()
                                continue
                            }
                            output.parentFile?.mkdirs()
                            BufferedOutputStream(FileOutputStream(output), IO_BUFFER_SIZE).use { sink ->
                                tar.copyTo(sink, IO_BUFFER_SIZE)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun safeOutputFile(root: File, archivePath: String): File {
        val output = File(root, archivePath)
        val rootPath = root.canonicalFile.toPath()
        val outputPath = output.canonicalFile.toPath()
        if (!outputPath.startsWith(rootPath)) {
            throw IOException("Unsafe path in Kokoro model archive: $archivePath")
        }
        return output
    }
}

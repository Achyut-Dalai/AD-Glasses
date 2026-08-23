package com.ad_glasses.ai.transcription.moonshine

import ai.moonshine.voice.JNI
import ai.moonshine.voice.ModelCache
import ai.moonshine.voice.ModelSpec
import android.content.Context
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

/**
 * Downloads + installs the Moonshine Voice model used by Ask AI.
 *
 * NOTE: Moonshine's published Maven artifact currently declares minSdk=35.
 * In this app we vendor Moonshine and build JNI locally (see :moonshine-voice module),
 * so we can keep app minSdk=24.
 *
 * Model files are stored in the same cache layout Moonshine's MicTranscriber resolves when a
 * custom models root is supplied. Older AD Glasses builds used `moonshine/<kind.id>` directly;
 * runtime preparation moves those files into the compatible directory in place so an
 * already-downloaded model is reused instead of silently downloading a second copy.
 */
object MoonshineModelManager {
    private const val TAG = "MoonshineModel"

    data class Progress(
        val percent: Int,
        val message: String,
    )

    enum class ModelKind(
        val id: String,
        val languageCode: String,
        val baseUrl: String,
        val modelArch: Int,
        val components: List<String>,
    ) {
        // Ask AI intentionally has one production STT model: Small Streaming English.
        SMALL_STREAMING_EN(
            id = "small-streaming-en",
            languageCode = "en",
            baseUrl = "https://download.moonshine.ai/model/small-streaming-en/quantized",
            modelArch = JNI.MOONSHINE_MODEL_ARCH_SMALL_STREAMING,
            components = listOf(
                "adapter.ort",
                "cross_kv.ort",
                "decoder_kv.ort",
                "encoder.ort",
                "frontend.ort",
                "streaming_config.json",
                "tokenizer.bin",
            ),
        ),
    }

    fun chooseDefault(languageHint: String? = null): ModelKind {
        // Only the English model is shipped today. Do not switch engines for other device locales.
        return ModelKind.SMALL_STREAMING_EN
    }

    /** Root passed to Moonshine's `modelsFrom(...)`; the runtime appends its ModelSpec cache key. */
    fun modelRoot(context: Context): File = File(context.filesDir, "moonshine").apply { mkdirs() }

    /** Exact directory Moonshine resolves for [kind] below [modelRoot]. */
    fun modelDir(context: Context, kind: ModelKind): File =
        ModelCache.directoryFor(context.applicationContext, modelSpec(kind), modelRoot(context))

    data class Validation(
        val ok: Boolean,
        val problems: List<String>,
        val topLevel: List<String>,
    )

    /**
     * Fast readiness check safe for UI. A model split across the legacy/new directories after an
     * interrupted migration still counts as installed because background runtime preparation can
     * finish the move without downloading it again.
     */
    fun isInstalled(context: Context, kind: ModelKind): Boolean {
        val destination = modelDir(context, kind)
        val legacy = legacyModelDir(context, kind)
        return kind.components.all { component ->
            validComponent(File(destination, component)) || validComponent(File(legacy, component))
        }
    }

    /**
     * Prepare the exact directory MicTranscriber will resolve. Call off the main thread because an
     * old installation may need a one-time component copy when an atomic directory move is not
     * available on the device filesystem.
     */
    fun prepareForRuntime(context: Context, kind: ModelKind): File {
        migrateLegacyModelIfNeeded(context, kind)
        val dir = modelDir(context, kind)
        check(validateDir(dir, kind).ok) {
            "Moonshine ${kind.id} is not installed. Install the Moonshine voice model in Cloud AI settings."
        }
        return dir
    }

    fun validationReport(dir: File, kind: ModelKind): String {
        val v = validateDir(dir, kind)
        val parts = mutableListOf<String>()
        parts += "path=${dir.absolutePath}"
        parts += "exists=${dir.exists()}"
        parts += "topLevel=${v.topLevel}"
        if (!v.ok) parts += "problems=${v.problems.joinToString("; ")}"
        return parts.joinToString(" | ")
    }

    private fun validateDir(dir: File, kind: ModelKind): Validation {
        val problems = mutableListOf<String>()
        val topLevel = dir.listFiles()?.map { it.name }?.sorted()?.take(40) ?: emptyList()

        if (!dir.exists() || !dir.isDirectory) {
            problems += "modelDir missing"
            return Validation(ok = false, problems = problems, topLevel = topLevel)
        }

        for (component in kind.components) {
            if (!validComponent(File(dir, component))) {
                problems += "missing:$component"
            }
        }

        return Validation(ok = problems.isEmpty(), problems = problems, topLevel = topLevel)
    }

    suspend fun installIfNeeded(
        context: Context,
        kind: ModelKind,
        onProgress: (Progress) -> Unit = {},
    ): File {
        migrateLegacyModelIfNeeded(context, kind)
        val dir = modelDir(context, kind)
        if (validateDir(dir, kind).ok) return dir

        dir.mkdirs()

        val total = kind.components.size.coerceAtLeast(1)
        val client = OkHttpClient.Builder().build()

        for ((idx, component) in kind.components.withIndex()) {
            val url = "${kind.baseUrl}/$component"
            val out = File(dir, component)
            if (validComponent(out)) continue

            val basePct = (idx * 100) / total
            val maxSpan = (100 / total).coerceAtLeast(1)

            onProgress(Progress(basePct.coerceIn(0, 99), "Downloading $component…"))

            downloadToFile(client, url, out) { bytesRead, contentLen ->
                val filePct = if (contentLen > 0L) {
                    ((bytesRead * 100L) / contentLen).toInt().coerceIn(0, 100)
                } else {
                    0
                }
                val pct = (basePct + (filePct * maxSpan / 100)).coerceIn(0, 99)
                onProgress(Progress(pct, "Downloading $component… ${filePct}%"))
            }
        }

        val validation = validateDir(dir, kind)
        if (!validation.ok) {
            val msg = "Moonshine model install failed: ${validation.problems.joinToString()} | ${validationReport(dir, kind)}"
            Log.e(TAG, msg)
            throw IllegalStateException(msg)
        }

        Log.i(TAG, "Installed Moonshine model ${kind.id} to ${dir.absolutePath}")
        onProgress(Progress(100, "Model installed"))
        return dir
    }

    /**
     * Moves models installed by the previous AD layout into Moonshine's own ModelSpec cache layout.
     * The whole directory is renamed first when possible. Component-by-component recovery then
     * tolerates an interrupted prior attempt and avoids a second large model download.
     */
    @Synchronized
    private fun migrateLegacyModelIfNeeded(context: Context, kind: ModelKind) {
        val destination = modelDir(context, kind)
        if (validateDir(destination, kind).ok) return

        val legacy = legacyModelDir(context, kind)
        if (!legacy.isDirectory || legacy.canonicalPath == destination.canonicalPath) return

        try {
            if (destination.listFiles().isNullOrEmpty()) {
                runCatching { destination.delete() }
                if (legacy.renameTo(destination) && validateDir(destination, kind).ok) {
                    Log.i(TAG, "Migrated Moonshine ${kind.id} model to ${destination.absolutePath}")
                    return
                }
            }

            destination.mkdirs()
            kind.components.forEach { component ->
                val source = File(legacy, component)
                val target = File(destination, component)
                if (validComponent(target) || !validComponent(source)) return@forEach

                if (target.exists()) target.delete()
                if (!source.renameTo(target)) {
                    source.copyTo(target, overwrite = true)
                    check(target.length() == source.length()) {
                        "Moonshine model migration copied an incomplete $component"
                    }
                }
            }

            if (validateDir(destination, kind).ok) {
                runCatching { legacy.deleteRecursively() }
                Log.i(TAG, "Migrated Moonshine ${kind.id} model to ${destination.absolutePath}")
            }
        } catch (error: Throwable) {
            Log.w(TAG, "Moonshine model migration was incomplete; existing files were preserved", error)
        }
    }

    private fun validComponent(file: File): Boolean = file.isFile && file.length() > 0L

    private fun legacyModelDir(context: Context, kind: ModelKind): File =
        File(modelRoot(context), kind.id)

    private fun modelSpec(kind: ModelKind): ModelSpec =
        ModelSpec.stt(kind.languageCode, kind.modelArch, false)

    private fun downloadToFile(
        client: OkHttpClient,
        url: String,
        out: File,
        onProgress: (bytesRead: Long, contentLength: Long) -> Unit,
    ) {
        val req = Request.Builder().url(url).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("Failed to download: HTTP ${resp.code} url=$url")
            }
            val body = resp.body ?: throw IllegalStateException("Empty response body: $url")
            val contentLen = body.contentLength()

            val parent = out.parentFile ?: throw IllegalStateException("Invalid output path: ${out.absolutePath}")
            parent.mkdirs()

            val tmp = File(parent, out.name + ".part")
            if (tmp.exists()) tmp.delete()

            FileOutputStream(tmp).use { fos ->
                val buf = ByteArray(256 * 1024)
                var readTotal = 0L
                body.byteStream().use { input ->
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        fos.write(buf, 0, n)
                        readTotal += n
                        onProgress(readTotal, contentLen)
                    }
                }
                fos.flush()

                if (contentLen > 0L && readTotal != contentLen) {
                    runCatching { tmp.delete() }
                    throw IllegalStateException("Download incomplete: got=${readTotal}B expected=${contentLen}B url=$url")
                }
            }

            if (out.exists()) out.delete()
            if (!tmp.renameTo(out)) {
                tmp.copyTo(out, overwrite = true)
                runCatching { tmp.delete() }
            }
        }
    }
}

package com.ad_glasses.ai.transcription.moonshine

import ai.moonshine.voice.AssetDownloader
import ai.moonshine.voice.JNI
import ai.moonshine.voice.ModelCache
import ai.moonshine.voice.ModelSpec
import android.content.Context
import android.util.Log
import java.io.File
import kotlin.math.roundToInt

/**
 * Downloads + installs the Moonshine Voice model used by Ask AI.
 *
 * NOTE: Moonshine's published Maven artifact currently declares minSdk=35. AD Glasses vendors the
 * pinned Moonshine source and builds JNI locally (see :moonshine-voice), so the app can keep its
 * lower minSdk while using the exact model catalog compiled into that native library.
 *
 * The app intentionally does not hard-code Moonshine CDN URLs. [AssetDownloader] resolves the
 * manifest from the pinned native catalog, which prevents a library/model mismatch when Moonshine
 * moves a quantized release to a new versioned directory.
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
        val modelArch: Int,
        val components: List<String>,
    ) {
        // One production Ask AI STT model. Small Streaming keeps the streaming architecture while
        // reducing sustained mobile CPU/RAM pressure compared with Medium Streaming.
        SMALL_STREAMING_EN(
            id = "small-streaming-en",
            languageCode = "en",
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
        // Only English is supported by Ask voice today. A different device locale never causes an
        // engine/model fallback; the required Moonshine English model remains explicit.
        return ModelKind.SMALL_STREAMING_EN
    }

    /** App-owned root for Moonshine model caches. */
    fun modelRoot(context: Context): File = File(context.filesDir, "moonshine").apply { mkdirs() }

    /** Exact ModelSpec cache directory for [kind] below [modelRoot]. */
    fun modelDir(context: Context, kind: ModelKind): File =
        ModelCache.directoryFor(context.applicationContext, modelSpec(kind), modelRoot(context))

    data class Validation(
        val ok: Boolean,
        val problems: List<String>,
        val topLevel: List<String>,
    )

    /**
     * Fast readiness check safe for UI. A model split across the legacy/new directories after an
     * interrupted migration still counts as installed because runtime preparation can finish the
     * move without a second download.
     */
    fun isInstalled(context: Context, kind: ModelKind): Boolean {
        val destination = modelDir(context, kind)
        val legacy = legacyModelDir(context, kind)
        return kind.components.all { component ->
            validComponent(File(destination, component)) || validComponent(File(legacy, component))
        }
    }

    /**
     * Prepare the exact directory the Moonshine [ai.moonshine.voice.Transcriber] will load. Call
     * off the main thread because an older AD layout may need a one-time component move/copy.
     */
    fun prepareForRuntime(context: Context, kind: ModelKind): File {
        migrateLegacyModelIfNeeded(context, kind)
        val dir = modelDir(context, kind)
        check(validateDir(dir, kind).ok) {
            "Moonshine is not installed. Install Moonshine in Cloud AI settings."
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

        kind.components.forEach { component ->
            if (!validComponent(File(dir, component))) problems += "missing:$component"
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
        if (validateDir(dir, kind).ok) {
            removeRetiredMediumModel(context)
            return dir
        }

        dir.mkdirs()
        // AssetDownloader treats an existing path as present, so remove only zero-length/corrupt
        // placeholders first. Valid existing components are retained and resumable .part files are
        // left for Moonshine's downloader to continue.
        kind.components.forEach { component ->
            File(dir, component).takeIf { it.exists() && !validComponent(it) }?.delete()
        }

        val downloader = AssetDownloader()
        val spec = modelSpec(kind)
        onProgress(Progress(0, "Preparing Moonshine…"))
        downloader.ensureModelPresent(
            dir,
            spec,
            AssetDownloader.ProgressListener { relativePath, fileIndex, totalFiles, bytesDownloaded, bytesTotal ->
                val withinFile = if (bytesTotal > 0L) {
                    (bytesDownloaded.toDouble() / bytesTotal.toDouble()).coerceIn(0.0, 1.0)
                } else {
                    0.0
                }
                val denominator = totalFiles.coerceAtLeast(1)
                val overall = ((fileIndex - 1).coerceAtLeast(0) + withinFile) / denominator.toDouble()
                val percent = (overall * 100.0).roundToInt().coerceIn(0, 99)
                val fileName = relativePath.substringAfterLast('/').ifBlank { "model data" }
                onProgress(Progress(percent, "Downloading $fileName…"))
            },
        )

        val validation = validateDir(dir, kind)
        if (!validation.ok) {
            val message = "Moonshine model install failed: ${validation.problems.joinToString()} | ${validationReport(dir, kind)}"
            Log.e(TAG, message)
            throw IllegalStateException(message)
        }

        // Keep the old Medium cache until Small is fully present and validated. After that point it
        // only wastes mobile storage and could accidentally hide a rollback/fallback in future code.
        removeRetiredMediumModel(context)
        Log.i(TAG, "Installed Moonshine model ${kind.id} from pinned native catalog to ${dir.absolutePath}")
        onProgress(Progress(100, "Moonshine installed"))
        return dir
    }

    /**
     * Moves models installed by the previous AD layout into Moonshine's ModelSpec cache layout.
     * Component-by-component recovery tolerates interrupted migration and never downloads while
     * this method runs.
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

    /** Medium Streaming is retired only after the Small Streaming replacement is ready. */
    private fun removeRetiredMediumModel(context: Context) {
        val root = modelRoot(context)
        val mediumSpec = ModelSpec.stt("en", JNI.MOONSHINE_MODEL_ARCH_MEDIUM_STREAMING, false)
        val mediumCache = ModelCache.directoryFor(context.applicationContext, mediumSpec, root)
        val mediumLegacy = File(root, "medium-streaming-en")
        listOf(mediumCache, mediumLegacy).distinctBy { it.absolutePath }.forEach { dir ->
            if (dir.exists()) {
                runCatching { dir.deleteRecursively() }
                    .onSuccess { deleted ->
                        if (deleted) Log.i(TAG, "Removed retired Moonshine Medium Streaming cache ${dir.absolutePath}")
                    }
                    .onFailure { Log.w(TAG, "Could not remove retired Moonshine Medium cache", it) }
            }
        }
    }

    private fun validComponent(file: File): Boolean = file.isFile && file.length() > 0L

    private fun legacyModelDir(context: Context, kind: ModelKind): File =
        File(modelRoot(context), kind.id)

    private fun modelSpec(kind: ModelKind): ModelSpec =
        ModelSpec.stt(kind.languageCode, kind.modelArch, false)
}

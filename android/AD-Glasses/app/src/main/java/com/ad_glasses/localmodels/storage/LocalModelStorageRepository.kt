package com.ad_glasses.localmodels.storage

import android.content.Context
import android.net.Uri
import android.os.StatFs
import android.provider.OpenableColumns
import com.ad_glasses.localmodels.catalog.LocalModelCatalogEntry
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class InstalledLocalModel(
    val id: String,
    val catalogId: String?,
    val displayName: String,
    val fileName: String,
    val absolutePath: String,
    val sizeBytes: Long,
    val sha256: String?,
    val quantization: String?,
    val promptTemplateId: String?,
    val sourceUrl: String?,
    val licenseTermsNote: String?,
    val importedAtMs: Long,
    val format: LocalModelFileFormat = LocalModelFileFormat.UNKNOWN,
)

data class LocalModelImportMetadata(
    val displayName: String,
    val declaredSizeBytes: Long?,
)

object LocalModelStorageRepository {
    private const val PREFS = "local_models_registry"
    private const val KEY_INSTALLED_MODELS = "installed_models"
    private const val KEY_SELECTED_MODEL_ID = "selected_model_id"
    internal const val IMPORT_STORAGE_RESERVE_BYTES = 64L * 1024L * 1024L

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun baseDir(context: Context): File = File(context.filesDir, "local_models")
    fun modelsDir(context: Context): File = File(baseDir(context), "models")
    fun tempDir(context: Context): File = File(baseDir(context), "tmp")

    fun ensureDirs(context: Context) {
        modelsDir(context).mkdirs()
        tempDir(context).mkdirs()
    }

    fun availableStorageBytes(context: Context): Long {
        ensureDirs(context)
        val statFs = StatFs(baseDir(context).absolutePath)
        return statFs.availableBytes
    }

    internal fun hasImportStorageHeadroom(
        availableBytes: Long,
        declaredSizeBytes: Long?,
        reserveBytes: Long = IMPORT_STORAGE_RESERVE_BYTES,
    ): Boolean {
        if (availableBytes <= reserveBytes) return false
        val size = declaredSizeBytes?.takeIf { it >= 0L } ?: return true
        return size <= availableBytes - reserveBytes
    }

    fun listInstalled(context: Context): List<InstalledLocalModel> {
        val raw = prefs(context).getString(KEY_INSTALLED_MODELS, "[]") ?: "[]"
        val arr = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
        val models = mutableListOf<InstalledLocalModel>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val path = obj.optString("absolute_path")
            val fileName = obj.optString("file_name")
            val storedFormat = LocalModelFileFormat.fromRegistryValue(obj.optString("model_format"))
            val storedFile = File(path)
            val detectedFormat = if (storedFile.isFile) {
                LocalModelFileUtils.detectFormat(storedFile) ?: LocalModelFileFormat.UNKNOWN
            } else {
                LocalModelFileUtils.formatFromFileName(fileName)
            }
            val model = InstalledLocalModel(
                id = obj.optString("id"),
                catalogId = obj.optString("catalog_id").ifBlank { null },
                displayName = obj.optString("display_name"),
                fileName = fileName,
                absolutePath = path,
                sizeBytes = obj.optLong("size_bytes"),
                sha256 = obj.optString("sha256").ifBlank { null },
                quantization = obj.optString("quantization").ifBlank { null },
                promptTemplateId = obj.optString("prompt_template_id").ifBlank { null },
                sourceUrl = obj.optString("source_url").ifBlank { null },
                licenseTermsNote = obj.optString("license_terms_note").ifBlank { null },
                importedAtMs = obj.optLong("imported_at_ms", 0L),
                format = detectedFormat ?: storedFormat ?: LocalModelFileFormat.UNKNOWN,
            )
            models += model
        }
        return models
            .filter { it.id.isNotBlank() }
            .sortedByDescending { it.importedAtMs }
    }

    fun getInstalled(context: Context, id: String?): InstalledLocalModel? {
        if (id.isNullOrBlank()) return null
        return listInstalled(context).firstOrNull { it.id == id }
    }

    fun findByCatalogId(context: Context, catalogId: String): InstalledLocalModel? {
        return listInstalled(context).firstOrNull { it.catalogId == catalogId }
    }

    fun persistInstalled(context: Context, model: InstalledLocalModel) {
        val current = listInstalled(context).toMutableList()
        val idx = current.indexOfFirst { it.id == model.id }
        if (idx >= 0) {
            current[idx] = model
        } else {
            current += model
        }
        saveInstalledList(context, current)
    }

    fun removeInstalled(context: Context, id: String): Boolean {
        val current = listInstalled(context).toMutableList()
        val model = current.firstOrNull { it.id == id } ?: return false
        current.removeAll { it.id == id }
        saveInstalledList(context, current)
        if (getSelectedModelId(context) == id) {
            setSelectedModelId(context, current.firstOrNull()?.id)
        }
        runCatching { File(model.absolutePath).delete() }
        return true
    }

    fun cleanupMissingModels(context: Context): Int {
        ensureDirs(context)
        val current = listInstalled(context)
        val filtered = current.filter { model ->
            val file = File(model.absolutePath)
            val usable = file.isFile && LocalModelFileUtils.isSupportedModelFile(file)
            if (file.isFile && !usable && file.parentFile == modelsDir(context)) {
                // Old app versions could register arbitrary picker content as a model.
                // Remove only invalid files inside app-owned model storage.
                runCatching { file.delete() }
            }
            usable
        }
        val registeredPaths = filtered.mapTo(mutableSetOf()) { File(it.absolutePath).absolutePath }
        val invalidOrphansRemoved = modelsDir(context).listFiles().orEmpty().count { file ->
            file.isFile &&
                file.absolutePath !in registeredPaths &&
                LocalModelFileUtils.hasSupportedModelExtension(file.name) &&
                !LocalModelFileUtils.isSupportedModelFile(file) &&
                runCatching { file.delete() }.getOrDefault(false)
        }
        val registryEntriesRemoved = current.size - filtered.size
        if (registryEntriesRemoved > 0) {
            saveInstalledList(context, filtered)
            if (getSelectedModelId(context)?.let { sid -> filtered.none { it.id == sid } } == true) {
                setSelectedModelId(context, filtered.firstOrNull()?.id)
            }
        }
        return registryEntriesRemoved + invalidOrphansRemoved
    }

    fun getSelectedModelId(context: Context): String? {
        return prefs(context).getString(KEY_SELECTED_MODEL_ID, null)
    }

    fun setSelectedModelId(context: Context, modelId: String?) {
        prefs(context).edit().putString(KEY_SELECTED_MODEL_ID, modelId).apply()
    }

    fun resolveSelectedModel(context: Context): InstalledLocalModel? {
        cleanupMissingModels(context)
        val all = listInstalled(context)
        if (all.isEmpty()) return null
        val selected = getSelectedModelId(context)
        val picked = all.firstOrNull { it.id == selected } ?: all.first()
        if (picked.id != selected) {
            setSelectedModelId(context, picked.id)
        }
        return picked
    }

    fun registerCatalogModel(
        context: Context,
        entry: LocalModelCatalogEntry,
        file: File,
        verifiedSha256: String? = null,
    ): InstalledLocalModel {
        val expectedFormat = LocalModelFileFormat.fromRegistryValue(entry.format)
            ?: error("Unsupported catalog model format: ${entry.format}")
        val detectedFormat = LocalModelFileUtils.detectFormat(file)
            ?: error("Downloaded file is not a supported local model")
        require(detectedFormat == expectedFormat) {
            "Downloaded ${detectedFormat.registryValue} file does not match catalog format ${entry.format}"
        }
        val sha = verifiedSha256 ?: LocalModelFileUtils.sha256Hex(file)
        val model = InstalledLocalModel(
            id = entry.id,
            catalogId = entry.id,
            displayName = entry.displayName,
            fileName = file.name,
            absolutePath = file.absolutePath,
            sizeBytes = file.length(),
            sha256 = sha,
            quantization = entry.quantization,
            promptTemplateId = entry.promptTemplateId,
            sourceUrl = entry.sourceUrl,
            licenseTermsNote = entry.licenseTermsNote,
            importedAtMs = System.currentTimeMillis(),
            format = detectedFormat,
        )
        persistInstalled(context, model)
        setSelectedModelId(context, model.id)
        return model
    }

    private fun registerImportedModel(
        context: Context,
        displayName: String,
        file: File,
        quantization: String? = null,
    ): InstalledLocalModel {
        val detectedFormat = LocalModelFileUtils.detectFormat(file)
            ?: error("Imported file must be a valid GGUF or LiteRT package")
        val sha = LocalModelFileUtils.sha256Hex(file)
        val id = "import-${sha.take(12)}"
        val model = InstalledLocalModel(
            id = id,
            catalogId = null,
            displayName = displayName,
            fileName = file.name,
            absolutePath = file.absolutePath,
            sizeBytes = file.length(),
            sha256 = sha,
            quantization = quantization,
            promptTemplateId = null,
            sourceUrl = null,
            licenseTermsNote = null,
            importedAtMs = System.currentTimeMillis(),
            format = detectedFormat,
        )
        val replacedPath = getInstalled(context, id)?.absolutePath
        persistInstalled(context, model)
        setSelectedModelId(context, model.id)
        if (!replacedPath.isNullOrBlank() && replacedPath != file.absolutePath) {
            runCatching { File(replacedPath).delete() }
        }
        return model
    }

    /**
     * Imports a user-selected model into app-owned storage as one validated transaction.
     * The registry is never updated until the complete file has passed format validation.
     */
    fun importModelFromUri(
        context: Context,
        uri: Uri,
        onProgress: ((copiedBytes: Long) -> Unit)? = null,
    ): InstalledLocalModel {
        ensureDirs(context)
        val metadata = queryImportMetadata(context, uri)
        val expectedFormat = LocalModelFileUtils.formatFromFileName(metadata.displayName)
            ?: error("Choose a model ending in .gguf, .litertlm, or .task")
        val cleanName = LocalModelFileUtils.sanitizeFileName(
            fileName = metadata.displayName,
            defaultExtension = ".${expectedFormat.registryValue}",
        )
        val availableBytes = availableStorageBytes(context)
        require(hasImportStorageHeadroom(availableBytes, metadata.declaredSizeBytes)) {
            "Not enough free storage to import this model and keep ${IMPORT_STORAGE_RESERVE_BYTES / (1024L * 1024L)} MB free"
        }
        val target = uniqueFileIn(modelsDir(context), cleanName)
        val partial = File(target.parentFile, "${target.name}.part")
        partial.delete()
        var movedToTarget = false
        try {
            val copied = context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Cannot open the selected model file" }
                LocalModelFileUtils.copyToFileWithLimit(
                    input = input,
                    target = partial,
                    maxBytes = availableBytes - IMPORT_STORAGE_RESERVE_BYTES,
                    onProgress = onProgress,
                )
            }
            metadata.declaredSizeBytes?.takeIf { it > 0L }?.let { declared ->
                require(copied >= declared) { "The model import ended before the complete file was copied" }
            }
            val detectedFormat = LocalModelFileUtils.detectFormat(partial)
                ?: error("The selected file is not a valid GGUF or LiteRT model package")
            require(detectedFormat == expectedFormat) {
                "The file contents do not match its .${metadata.displayName.substringAfterLast('.', "model")} extension"
            }
            require(partial.renameTo(target)) { "Could not finalize the imported model file" }
            movedToTarget = true
            return try {
                registerImportedModel(
                    context = context,
                    displayName = importDisplayName(metadata.displayName),
                    file = target,
                )
            } catch (error: Throwable) {
                target.delete()
                throw error
            }
        } finally {
            partial.delete()
            if (!movedToTarget) target.delete()
        }
    }

    fun queryImportMetadata(context: Context, uri: Uri): LocalModelImportMetadata {
        if (uri.scheme.equals("file", ignoreCase = true)) {
            val file = File(uri.path.orEmpty())
            return LocalModelImportMetadata(
                displayName = file.name.ifBlank { DEFAULT_IMPORT_FILE_NAME },
                declaredSizeBytes = file.takeIf { it.isFile }?.length(),
            )
        }
        var displayName: String? = null
        var sizeBytes: Long? = null
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0 && !cursor.isNull(nameIndex)) displayName = cursor.getString(nameIndex)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) sizeBytes = cursor.getLong(sizeIndex)
                }
            }
        }
        val fallbackName = uri.lastPathSegment
            ?.substringAfterLast('/')
            ?.substringAfterLast('\\')
            ?.takeIf { it.isNotBlank() }
        return LocalModelImportMetadata(
            displayName = displayName?.takeIf { it.isNotBlank() } ?: fallbackName ?: DEFAULT_IMPORT_FILE_NAME,
            declaredSizeBytes = sizeBytes?.takeIf { it >= 0L },
        )
    }

    private fun saveInstalledList(context: Context, items: List<InstalledLocalModel>) {
        val arr = JSONArray()
        items.forEach { model ->
            arr.put(
                JSONObject()
                    .put("id", model.id)
                    .put("catalog_id", model.catalogId.orEmpty())
                    .put("display_name", model.displayName)
                    .put("file_name", model.fileName)
                    .put("absolute_path", model.absolutePath)
                    .put("size_bytes", model.sizeBytes)
                    .put("sha256", model.sha256.orEmpty())
                    .put("quantization", model.quantization.orEmpty())
                    .put("prompt_template_id", model.promptTemplateId.orEmpty())
                    .put("source_url", model.sourceUrl.orEmpty())
                    .put("license_terms_note", model.licenseTermsNote.orEmpty())
                    .put("imported_at_ms", model.importedAtMs)
                    .put("model_format", model.format.registryValue),
            )
        }
        prefs(context).edit().putString(KEY_INSTALLED_MODELS, arr.toString()).apply()
    }

    private fun uniqueFileIn(dir: File, fileName: String): File {
        var attempt = 0
        val cleanName = LocalModelFileUtils.sanitizeFileName(fileName)
        val dot = cleanName.lastIndexOf('.')
        val base = if (dot > 0) cleanName.substring(0, dot) else cleanName
        val ext = if (dot > 0) cleanName.substring(dot) else ""
        while (true) {
            val candidate = if (attempt == 0) {
                File(dir, cleanName)
            } else {
                File(dir, "${base}_$attempt$ext")
            }
            if (!candidate.exists()) return candidate
            attempt += 1
        }
    }

    private fun importDisplayName(sourceName: String): String {
        val withoutPath = sourceName.substringAfterLast('/').substringAfterLast('\\')
        val withoutExtension = withoutPath.substringBeforeLast('.').trim()
        return withoutExtension
            .replace(Regex("[\\p{Cc}\\p{Cf}]+"), " ")
            .replace(Regex("\\s+"), " ")
            .take(120)
            .ifBlank { "Imported local model" }
    }

    private const val DEFAULT_IMPORT_FILE_NAME = "local-model.gguf"
}

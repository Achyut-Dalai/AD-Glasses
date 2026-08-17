package com.fersaiyan.cyanbridge.ai.orchestrator

import android.content.Context
import android.net.Uri
import com.fersaiyan.cyanbridge.ui.MyApplication
import com.fersaiyan.cyanbridge.ui.recordings.SyncedMediaQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

/** A durable reference to the artifact the user explicitly opened before continuing with AD. */
data class ADArtifactContext(
    val kind: String,
    val id: Long,
    val label: String,
)

data class ADResolvedArtifactContext(
    val description: String,
    val imagePath: String? = null,
)

object ADArtifactContextStore {
    private const val PREFS = "ad_artifact_context"
    private const val KEY_KIND = "kind"
    private const val KEY_ID = "id"
    private const val KEY_LABEL = "label"

    fun set(context: Context, artifact: ADArtifactContext) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_KIND, artifact.kind)
            .putLong(KEY_ID, artifact.id)
            .putString(KEY_LABEL, artifact.label)
            .apply()
    }

    fun get(context: Context): ADArtifactContext? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val kind = prefs.getString(KEY_KIND, null)?.trim().orEmpty()
        val id = prefs.getLong(KEY_ID, -1L)
        if (kind.isBlank() || id < 0L) return null
        return ADArtifactContext(
            kind = kind,
            id = id,
            label = prefs.getString(KEY_LABEL, "")?.trim().orEmpty(),
        )
    }

    fun clear(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}

/**
 * Turns Library records into model context without polluting the visible chat with hidden prompt
 * scaffolding. The original artifact remains in Library; temporary image copies are cache-only.
 */
object ADArtifactContextResolver {
    suspend fun resolve(context: Context, artifact: ADArtifactContext?): ADResolvedArtifactContext? {
        artifact ?: return null
        return when (artifact.kind.lowercase()) {
            "capture" -> resolveCapture(context, artifact)
            "recording" -> resolveRecording(artifact)
            "note" -> resolveNote(artifact)
            else -> null
        }
    }

    private suspend fun resolveCapture(context: Context, artifact: ADArtifactContext): ADResolvedArtifactContext? = withContext(Dispatchers.IO) {
        val item = SyncedMediaQuery.query(context).firstOrNull { it.id.toLong() == artifact.id } ?: return@withContext null
        val label = artifact.label.ifBlank { item.displayName }
        if (item.isVideo) {
            return@withContext ADResolvedArtifactContext(
                description = "The user is inspecting a glasses video named '$label'. The current contextual session refers to that video. Do not pretend to have watched frames that were not supplied.",
            )
        }
        val cached = copyContentToCache(context, item.contentUriString, artifact.id)
        ADResolvedArtifactContext(
            description = "The user is inspecting a photo captured by their glasses named '$label'. Treat the attached image as the current visual context.",
            imagePath = cached?.absolutePath,
        )
    }

    private suspend fun resolveRecording(artifact: ADArtifactContext): ADResolvedArtifactContext? = withContext(Dispatchers.IO) {
        val session = MyApplication.repository.getAllCaptureSessions().first().firstOrNull { it.id == artifact.id } ?: return@withContext null
        val transcript = MyApplication.repository.getTranscriptionByCaptureSessionId(session.id)?.transcriptText?.trim().orEmpty()
        val label = artifact.label.ifBlank { "recording ${session.id}" }
        val description = buildString {
            append("The user is inspecting glasses recording '$label' (${session.durationSec} seconds).")
            if (transcript.isNotBlank()) {
                append(" Use this stored transcript as artifact context:\n")
                append(transcript.take(MAX_CONTEXT_CHARS))
            } else {
                append(" No stored transcript is available yet; do not invent its contents.")
            }
        }
        ADResolvedArtifactContext(description = description)
    }

    private suspend fun resolveNote(artifact: ADArtifactContext): ADResolvedArtifactContext? = withContext(Dispatchers.IO) {
        val note = MyApplication.notesRepository.getAllNotes().first().firstOrNull { it.id == artifact.id } ?: return@withContext null
        val description = buildString {
            append("The user is inspecting a saved AD note titled '")
            append(note.title.ifBlank { artifact.label.ifBlank { "Untitled note" } })
            append("'.")
            if (note.summary.isNotBlank()) {
                append(" Note content/summary:\n")
                append(note.summary.take(MAX_CONTEXT_CHARS))
            }
        }
        ADResolvedArtifactContext(description = description)
    }

    private fun copyContentToCache(context: Context, rawUri: String, id: Long): File? {
        val uri = runCatching { Uri.parse(rawUri) }.getOrNull() ?: return null
        val extension = when (context.contentResolver.getType(uri)?.lowercase()) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        val target = File(context.cacheDir, "ad-artifact-$id.$extension")
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use(input::copyTo)
            } ?: return null
            target
        }.getOrNull()
    }

    private const val MAX_CONTEXT_CHARS = 16_000
}

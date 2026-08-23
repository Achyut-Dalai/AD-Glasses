package com.ad_glasses.media.autocapture

import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.Log
import com.ad_glasses.ai.transcription.AudioChunker
import com.ad_glasses.ai.transcription.DefaultTranscriptionService
import com.ad_glasses.ai.transcription.NoOpAudioChunker
import com.ad_glasses.ai.transcription.RetryPolicy
import com.ad_glasses.ai.transcription.RetryingTranscriptionProvider
import com.ad_glasses.ai.transcription.TranscriptionProvider
import com.ad_glasses.ai.transcription.TranscriptionResult
import com.ad_glasses.ai.transcription.TranscriptionService
import com.ad_glasses.ai.transcription.moonshine.MoonshineModelManager
import com.ad_glasses.ai.transcription.moonshine.MoonshineTranscriptionProvider
import com.ad_glasses.data.local.entity.CaptureSession
import com.ad_glasses.localagent.userfacts.TranscriptCandidateFactsAppender
import com.ad_glasses.ui.MyApplication
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object GlassesSyncedAudioIngestor {
    private const val TAG = "GlassesAudioIngest"
    private const val CAPTURE_SOURCE = "GLASSES_SYNC_P2P"
    private const val DEFAULT_DEVICE_CLASS = "HEY_CYAN"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlightSessionIds = ConcurrentHashMap.newKeySet<Long>()

    data class PersistResult(
        val createdSessionId: Long?,
        val localPath: String?,
    )

    suspend fun persistDownloadedAudio(
        context: Context,
        displayName: String,
        payloadBytes: ByteArray,
        takenTimeMs: Long,
    ): PersistResult {
        val appContext = context.applicationContext
        val localFile = prepareLocalFile(appContext, displayName, takenTimeMs)

        if (!localFile.exists() || localFile.length() <= 0L) {
            FileOutputStream(localFile).use { out ->
                out.write(payloadBytes)
                out.flush()
            }
        }

        val existingSession = MyApplication.repository.getCaptureSessionByAudioPath(localFile.absolutePath)
        if (existingSession != null) {
            maybeQueueTranscription(appContext, existingSession)
            return PersistResult(createdSessionId = null, localPath = localFile.absolutePath)
        }

        val startedAt = if (takenTimeMs > 0L) takenTimeMs else System.currentTimeMillis()
        val durationSec = estimateAudioDurationSec(localFile)
        val endedAt = if (durationSec > 0L) startedAt + (durationSec * 1000L) else startedAt

        val session = CaptureSession(
            startedAt = startedAt,
            endedAt = endedAt,
            durationSec = durationSec,
            deviceClass = DEFAULT_DEVICE_CLASS,
            captureSource = CAPTURE_SOURCE,
            audioPath = localFile.absolutePath,
            timerDurationSec = null,
            stopReason = "p2p_sync",
            error = null,
        )
        val id = MyApplication.repository.insertCaptureSession(session)
        maybeQueueTranscription(appContext, session.copy(id = id))
        return PersistResult(createdSessionId = id, localPath = localFile.absolutePath)
    }

    private fun maybeQueueTranscription(context: Context, session: CaptureSession) {
        if (!inFlightSessionIds.add(session.id)) return

        scope.launch {
            try {
                val existing = MyApplication.repository.getTranscriptionByCaptureSessionId(session.id)
                if (existing != null && existing.status.equals("SUCCEEDED", ignoreCase = true)) return@launch

                val engine = moonshineEngine(context)
                if (engine == null) {
                    Log.i(TAG, "Moonshine is not installed; leaving session=${session.id} untranscribed")
                    return@launch
                }
                val service = DefaultTranscriptionService(
                    context = context,
                    repository = MyApplication.repository,
                    provider = engine.provider,
                    chunker = engine.chunker,
                )
                val result = service.transcribe(
                    session = session,
                    options = TranscriptionService.Options(
                        chunkDurationSec = engine.chunkDurationSec,
                        mimeType = mimeTypeForPath(session.audioPath),
                    ),
                )

                when (result) {
                    is TranscriptionResult.Success -> {
                        val transcript = result.text.trim()
                        if (transcript.isNotBlank()) {
                            TranscriptCandidateFactsAppender.appendFromTranscript(
                                context = context,
                                session = session,
                                transcript = transcript,
                            )
                        }
                        Log.i(TAG, "Auto transcription completed for session=${session.id} provider=${result.provider}")
                    }
                    is TranscriptionResult.Failure -> {
                        Log.w(TAG, "Auto transcription failed for session=${session.id}: ${result.message}")
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Auto transcription pipeline failed for session=${session.id}: ${t.message}", t)
            } finally {
                inFlightSessionIds.remove(session.id)
            }
        }
    }

    private data class EngineSelection(
        val provider: TranscriptionProvider,
        val chunker: AudioChunker,
        val chunkDurationSec: Long,
    )

    private fun moonshineEngine(context: Context): EngineSelection? {
        val kind = MoonshineModelManager.chooseDefault(languageHint = null)
        if (!MoonshineModelManager.isInstalled(context, kind)) return null
        return EngineSelection(
            provider = RetryingTranscriptionProvider(
                MoonshineTranscriptionProvider(
                    context = context,
                    modelDir = MoonshineModelManager.modelDir(context, kind),
                    modelArch = kind.modelArch,
                ),
                policy = RetryPolicy(maxAttempts = 1),
            ),
            chunker = NoOpAudioChunker(),
            chunkDurationSec = 60L,
        )
    }

    private fun mimeTypeForPath(path: String): String = when (File(path).extension.lowercase(Locale.US)) {
        "ogg", "opus" -> "audio/ogg"
        "wav" -> "audio/wav"
        "mp3" -> "audio/mpeg"
        else -> "audio/mp4"
    }

    private fun estimateAudioDurationSec(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: 0L
            (durationMs / 1000L).coerceAtLeast(0L)
        } catch (_: Throwable) {
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun prepareLocalFile(context: Context, displayName: String, takenTimeMs: Long): File {
        val recordingsDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "recordings")
        if (!recordingsDir.exists()) recordingsDir.mkdirs()

        val fallbackTs = if (takenTimeMs > 0L) takenTimeMs else System.currentTimeMillis()
        val base = displayName
            .substringBeforeLast('.')
            .ifBlank { "glasses_audio_$fallbackTs" }
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .take(96)
            .ifBlank { "glasses_audio_$fallbackTs" }

        return File(recordingsDir, "glasses_sync_${base}.ogg")
    }
}

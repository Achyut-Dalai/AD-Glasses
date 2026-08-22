package com.ad_glasses.ai.transcription

import com.ad_glasses.ai.transcription.backend.FakeTranscriptionBackend
import com.ad_glasses.ai.transcription.retry.RetryPolicy
import com.ad_glasses.ai.transcription.storage.TranscriptStore
import kotlinx.coroutines.flow.Flow

/**
 * Convenience wrapper for tests.
 */
class FakeTranscriptionService(
    fixedText: String? = "hello from fake",
    failTimes: Int = 0,
    retryPolicy: RetryPolicy = RetryPolicy(),
    transcriptStore: TranscriptStore? = null,
) : TranscriptionService {

    private val delegate = ChunkingTranscriptionService(
        backend = FakeTranscriptionBackend(fixedText = fixedText, failTimes = failTimes),
        retryPolicy = retryPolicy,
        transcriptStore = transcriptStore,
    )

    override fun transcribe(request: TranscriptionRequest): Flow<TranscriptionEvent> = delegate.transcribe(request)

    override suspend fun transcribe(
        session: com.ad_glasses.data.local.entity.CaptureSession,
        options: TranscriptionService.Options,
        onProgress: (TranscriptionProgress) -> Unit,
    ): TranscriptionResult = delegate.transcribe(session, options, onProgress)
}

package com.achyut.adglasses.ai.transcription

import com.achyut.adglasses.ai.transcription.backend.FakeTranscriptionBackend
import com.achyut.adglasses.ai.transcription.retry.RetryPolicy
import com.achyut.adglasses.ai.transcription.storage.TranscriptStore
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
        session: com.achyut.adglasses.data.local.entity.CaptureSession,
        options: TranscriptionService.Options,
        onProgress: (TranscriptionProgress) -> Unit,
    ): TranscriptionResult = delegate.transcribe(session, options, onProgress)
}

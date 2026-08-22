package com.ad_glasses.ai.transcription

import com.ad_glasses.ai.transcription.backend.HttpTranscriptionBackend
import com.ad_glasses.ai.transcription.retry.RetryPolicy
import com.ad_glasses.ai.transcription.storage.TranscriptStore
import kotlinx.coroutines.flow.Flow

/**
 * Convenience wrapper around [ChunkingTranscriptionService] + [HttpTranscriptionBackend].
 *
 * The backend is a skeleton; the server contract is documented in [HttpTranscriptionBackend].
 */
class HttpTranscriptionService(
    endpointUrl: String,
    apiKey: String? = null,
    retryPolicy: RetryPolicy = RetryPolicy(),
    transcriptStore: TranscriptStore? = null,
) : TranscriptionService {

    private val delegate = ChunkingTranscriptionService(
        backend = HttpTranscriptionBackend(endpointUrl = endpointUrl, apiKey = apiKey),
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

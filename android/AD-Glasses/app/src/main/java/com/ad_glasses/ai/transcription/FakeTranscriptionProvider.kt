package com.ad_glasses.ai.transcription

import java.io.File

/**
 * Deterministic fake provider for tests.
 */
class FakeTranscriptionProvider(
    private val fixedText: String = "Hello from fake transcription provider."
) : TranscriptionProvider {
    override val name: String = "fake"

    override suspend fun transcribe(audioFile: File, mimeType: String, language: String?): String {
        return fixedText
    }
}

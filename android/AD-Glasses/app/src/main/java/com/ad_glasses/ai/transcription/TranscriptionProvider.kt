package com.ad_glasses.ai.transcription

import java.io.File

interface TranscriptionProvider {
    val name: String

    /**
     * Returns transcript text for a single audio chunk.
     */
    suspend fun transcribe(
        audioFile: File,
        mimeType: String,
        language: String? = null,
    ): String
}

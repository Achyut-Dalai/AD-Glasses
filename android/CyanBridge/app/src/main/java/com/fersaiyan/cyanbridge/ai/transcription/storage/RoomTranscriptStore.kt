package com.achyut.adglasses.ai.transcription.storage

import android.content.Context
import com.achyut.adglasses.data.local.dao.CaptureTranscriptDao
import com.achyut.adglasses.data.local.entity.CaptureTranscript
import com.achyut.adglasses.privacy.PrivacyPrefs

class RoomTranscriptStore(
    private val context: Context,
    private val dao: CaptureTranscriptDao,
) : TranscriptStore {

    override suspend fun maybePersist(
        captureSessionId: Long?,
        provider: String,
        language: String?,
        transcript: String,
    ): Boolean {
        if (captureSessionId == null) return false
        if (!PrivacyPrefs.isTranscriptStorageEnabled(context)) return false

        val now = System.currentTimeMillis()
        dao.upsert(
            CaptureTranscript(
                captureSessionId = captureSessionId,
                createdAt = now,
                updatedAt = now,
                provider = provider,
                language = language,
                transcript = transcript,
            )
        )
        return true
    }
}

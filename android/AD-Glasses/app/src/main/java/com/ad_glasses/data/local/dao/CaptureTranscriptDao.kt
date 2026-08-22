package com.ad_glasses.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ad_glasses.data.local.entity.CaptureTranscript

@Dao
interface CaptureTranscriptDao {

    @Query("SELECT * FROM capture_transcripts WHERE captureSessionId = :sessionId")
    suspend fun getForSession(sessionId: Long): CaptureTranscript?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(transcript: CaptureTranscript)

    @Query("DELETE FROM capture_transcripts WHERE captureSessionId = :sessionId")
    suspend fun deleteForSession(sessionId: Long)
}

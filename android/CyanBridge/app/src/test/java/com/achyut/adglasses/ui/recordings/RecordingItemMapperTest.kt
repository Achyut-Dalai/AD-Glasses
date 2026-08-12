package com.achyut.adglasses.ui.recordings

import com.achyut.adglasses.data.local.entity.CaptureSession
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingItemMapperTest {

    @Test
    fun glassesSyncUsesGlassesAudioTitle() {
        val item = captureSession(captureSource = "GLASSES_SYNC_P2P").toRecordingItem()

        assertTrue(item.title.startsWith("Glasses audio · "))
    }

    @Test
    fun meetingCaptureUsesMeetingTitle() {
        val item = captureSession(captureSource = "PHONE_MIC").toRecordingItem()

        assertTrue(item.title.startsWith("Meeting · "))
    }

    private fun captureSession(captureSource: String) = CaptureSession(
        id = 1L,
        startedAt = 1_700_000_000_000L,
        endedAt = 1_700_000_060_000L,
        durationSec = 60L,
        deviceClass = "HEY_CYAN",
        captureSource = captureSource,
        audioPath = "/tmp/recording.m4a",
        timerDurationSec = null,
        stopReason = null,
        error = null,
    )
}

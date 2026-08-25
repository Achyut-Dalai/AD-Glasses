package com.ad_glasses.ai.transcription.moonshine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoonshineAudioDiagnosticsTest {
    @Test
    fun silence_reports_floor_without_clipping() {
        val diagnostics = MoonshineAudioDiagnostics()
        diagnostics.add(ShortArray(800), 800)

        val snapshot = diagnostics.snapshot()
        assertEquals(800L, snapshot.sampleCount)
        assertTrue(snapshot.rmsDbfs <= -170.0)
        assertTrue(snapshot.peakDbfs <= -170.0)
        assertEquals(0L, snapshot.clippingPpm)
    }

    @Test
    fun moderate_pcm_reports_expected_level_without_clipping() {
        val diagnostics = MoonshineAudioDiagnostics()
        val samples = ShortArray(800) { index -> if (index % 2 == 0) 8_192 else -8_192 }
        diagnostics.add(samples, samples.size)

        val snapshot = diagnostics.snapshot()
        assertTrue(snapshot.rmsDbfs in -12.2..-11.8)
        assertTrue(snapshot.peakDbfs in -12.2..-11.8)
        assertEquals(0L, snapshot.clippingPpm)
    }

    @Test
    fun saturated_pcm_is_detected_as_clipping() {
        val diagnostics = MoonshineAudioDiagnostics()
        val samples = shortArrayOf(32_767, -32_768, 12_000, -12_000)
        diagnostics.add(samples, samples.size)

        val snapshot = diagnostics.snapshot()
        assertTrue(snapshot.peakDbfs > -0.01)
        assertEquals(500_000L, snapshot.clippingPpm)
    }

    @Test
    fun add_respects_actual_audio_record_read_count() {
        val diagnostics = MoonshineAudioDiagnostics()
        val samples = ShortArray(8) { 32_767 }
        diagnostics.add(samples, 2)

        assertEquals(2L, diagnostics.snapshot().sampleCount)
    }
}

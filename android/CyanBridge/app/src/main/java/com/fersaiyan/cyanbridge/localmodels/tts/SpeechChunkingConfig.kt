package com.achyut.adglasses.localmodels.tts

/**
 * Configuration parameters for the multilingual streaming speech chunker.
 */
data class SpeechChunkingConfig(
    val firstChunkMinCodePoints: Int = 18,
    val normalChunkMinCodePoints: Int = 35,
    val preferredChunkMaxCodePoints: Int = 120,
    val hardChunkMaxCodePoints: Int = 180,
    val candidateBoundaryDelayMs: Long = 80L,
    val firstChunkIdleFlushMs: Long = 500L,
    val normalChunkIdleFlushMs: Long = 900L,
    val maxPendingTtsChunks: Int = 2,
)

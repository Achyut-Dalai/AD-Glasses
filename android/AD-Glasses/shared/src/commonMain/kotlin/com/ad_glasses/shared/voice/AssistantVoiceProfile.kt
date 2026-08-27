package com.ad_glasses.shared.voice

/**
 * Platform-neutral description of the assistant's speech voice.
 *
 * Keep model identity and speaker selection in shared code so Android and a future iOS
 * implementation select the same voice without depending on platform TTS APIs.
 */
data class AssistantVoiceProfile(
    val modelId: String,
    val voiceId: String,
    val displayName: String,
    val speakerId: Int,
    val languageTag: String,
    val sampleRateHz: Int,
    val defaultSpeed: Float,
)

object KokoroHeartVoice {
    const val MODEL_ID = "kokoro-multi-lang-v1_0"
    const val VOICE_ID = "af_heart"
    const val SPEAKER_ID = 3
    const val SAMPLE_RATE_HZ = 24_000
    const val DEFAULT_SPEED = 1.0f

    const val MODEL_ARCHIVE_URL =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-multi-lang-v1_0.tar.bz2"

    val profile = AssistantVoiceProfile(
        modelId = MODEL_ID,
        voiceId = VOICE_ID,
        displayName = "Heart",
        speakerId = SPEAKER_ID,
        languageTag = "en-US",
        sampleRateHz = SAMPLE_RATE_HZ,
        defaultSpeed = DEFAULT_SPEED,
    )
}

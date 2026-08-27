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

/**
 * The complete Kokoro-82M multilingual v1.0 pack used by AD Glasses.
 *
 * The downloaded voices.bin is intentionally kept intact. A later settings UI can select another
 * speaker by supplying a different [AssistantVoiceProfile] without changing the Android/iOS speech
 * architecture or downloading a different model pack.
 */
object Kokoro82MVoicePack {
    const val MODEL_ID = "kokoro-multi-lang-v1_0"
    const val SAMPLE_RATE_HZ = 24_000
    const val DEFAULT_SPEED = 1.0f

    const val MODEL_ARCHIVE_URL =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-multi-lang-v1_0.tar.bz2"

    fun voice(
        voiceId: String,
        displayName: String,
        speakerId: Int,
        languageTag: String = "en-US",
        speed: Float = DEFAULT_SPEED,
    ): AssistantVoiceProfile = AssistantVoiceProfile(
        modelId = MODEL_ID,
        voiceId = voiceId,
        displayName = displayName,
        speakerId = speakerId,
        languageTag = languageTag,
        sampleRateHz = SAMPLE_RATE_HZ,
        defaultSpeed = speed,
    )
}

/** Current flagship/default AD Glasses voice. */
object KokoroHeartVoice {
    const val VOICE_ID = "af_heart"
    const val SPEAKER_ID = 3

    // Compatibility aliases keep call sites concise while the model pack remains voice-agnostic.
    const val MODEL_ID = Kokoro82MVoicePack.MODEL_ID
    const val SAMPLE_RATE_HZ = Kokoro82MVoicePack.SAMPLE_RATE_HZ
    const val DEFAULT_SPEED = Kokoro82MVoicePack.DEFAULT_SPEED
    const val MODEL_ARCHIVE_URL = Kokoro82MVoicePack.MODEL_ARCHIVE_URL

    val profile = Kokoro82MVoicePack.voice(
        voiceId = VOICE_ID,
        displayName = "Heart",
        speakerId = SPEAKER_ID,
        languageTag = "en-US",
    )
}

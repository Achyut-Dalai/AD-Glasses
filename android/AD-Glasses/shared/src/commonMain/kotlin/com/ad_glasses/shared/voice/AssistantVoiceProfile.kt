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
 * speaker without changing the Android/iOS speech architecture or downloading a different model
 * pack. Speaker IDs match the 53-speaker sherpa-onnx v1.0 voices.bin ordering.
 */
object Kokoro82MVoicePack {
    const val MODEL_ID = "kokoro-multi-lang-v1_0"
    const val SAMPLE_RATE_HZ = 24_000
    const val DEFAULT_SPEED = 1.0f

    const val MODEL_ARCHIVE_URL =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-multi-lang-v1_0.tar.bz2"

    val speakerIds: Map<String, Int> = linkedMapOf(
        "af_alloy" to 0,
        "af_aoede" to 1,
        "af_bella" to 2,
        "af_heart" to 3,
        "af_jessica" to 4,
        "af_kore" to 5,
        "af_nicole" to 6,
        "af_nova" to 7,
        "af_river" to 8,
        "af_sarah" to 9,
        "af_sky" to 10,
        "am_adam" to 11,
        "am_echo" to 12,
        "am_eric" to 13,
        "am_fenrir" to 14,
        "am_liam" to 15,
        "am_michael" to 16,
        "am_onyx" to 17,
        "am_puck" to 18,
        "am_santa" to 19,
        "bf_alice" to 20,
        "bf_emma" to 21,
        "bf_isabella" to 22,
        "bf_lily" to 23,
        "bm_daniel" to 24,
        "bm_fable" to 25,
        "bm_george" to 26,
        "bm_lewis" to 27,
        "ef_dora" to 28,
        "em_alex" to 29,
        "ff_siwis" to 30,
        "hf_alpha" to 31,
        "hf_beta" to 32,
        "hm_omega" to 33,
        "hm_psi" to 34,
        "if_sara" to 35,
        "im_nicola" to 36,
        "jf_alpha" to 37,
        "jf_gongitsune" to 38,
        "jf_nezumi" to 39,
        "jf_tebukuro" to 40,
        "jm_kumo" to 41,
        "pf_dora" to 42,
        "pm_alex" to 43,
        "pm_santa" to 44,
        "zf_xiaobei" to 45,
        "zf_xiaoni" to 46,
        "zf_xiaoxiao" to 47,
        "zf_xiaoyi" to 48,
        "zm_yunjian" to 49,
        "zm_yunxi" to 50,
        "zm_yunxia" to 51,
        "zm_yunyang" to 52,
    )

    fun speakerId(voiceId: String): Int =
        requireNotNull(speakerIds[voiceId]) { "Unknown Kokoro-82M v1.0 voice: $voiceId" }

    fun voice(
        voiceId: String,
        displayName: String,
        speakerId: Int = speakerId(voiceId),
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

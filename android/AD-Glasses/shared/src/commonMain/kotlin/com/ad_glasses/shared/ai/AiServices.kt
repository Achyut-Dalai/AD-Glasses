package com.ad_glasses.shared.ai

/**
 * Cross-platform AI service abstraction.
 * Supports both local inference and remote API calls.
 */

// ── Chat AI ──

data class ChatMessage(
    val role: String,  // "user", "assistant", "system"
    val content: String,
)

data class ChatResponse(
    val message: ChatMessage,
    val usage: TokenUsage? = null,
)

data class TokenUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int,
)

interface ChatAiService {
    /**
     * Send a chat message and get a response.
     * @param messages The conversation history
     * @param model Optional model override
     * @return The assistant's response
     */
    suspend fun chat(messages: List<ChatMessage>, model: String? = null): ChatResponse
}

// ── Voice/Audio AI ──

interface VoiceAiService {
    /**
     * Transcribe audio to text.
     * @param audioData The audio data (WAV, Opus, etc.)
     * @param mimeType The audio MIME type
     * @return The transcribed text
     */
    suspend fun transcribe(audioData: ByteArray, mimeType: String): String
}

// ── Image AI ──

interface ImageAiService {
    /**
     * Analyze an image with a text prompt.
     * @param imageData The image data (JPEG, PNG)
     * @param prompt The text prompt
     * @param mimeType The image MIME type
     * @return The AI's description/analysis
     */
    suspend fun analyzeImage(imageData: ByteArray, prompt: String, mimeType: String = "image/jpeg"): String
}

// ── Model info ──

data class AiModel(
    val id: String,
    val name: String,
    val provider: String,
    val isLocal: Boolean = false,
)

interface AiModelRegistry {
    /** List available models. */
    suspend fun listModels(): List<AiModel>

    /** Get the default model ID. */
    fun getDefaultModelId(): String
}

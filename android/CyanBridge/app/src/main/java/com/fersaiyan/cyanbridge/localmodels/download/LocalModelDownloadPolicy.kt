package com.fersaiyan.cyanbridge.localmodels.download

import java.util.concurrent.atomic.AtomicReference

/** Coarse phases surfaced by the downloader to its foreground-service host. */
enum class LocalModelDownloadPhase {
    DOWNLOADING,
    RETRYING,
    VERIFYING,
    INSTALLING,
}

data class LocalModelDownloadStatus(
    val modelId: String,
    val phase: LocalModelDownloadPhase,
    val message: String,
    val attempt: Int,
    val maxAttempts: Int,
)

/**
 * A process-local single-flight gate. The foreground service owns one instance so duplicate
 * start intents cannot create two writers for the same `.part` file.
 */
internal class LocalModelDownloadSlot {
    private val activeModelId = AtomicReference<String?>(null)

    fun tryAcquire(modelId: String): Boolean = activeModelId.compareAndSet(null, modelId)

    fun release(modelId: String): Boolean = activeModelId.compareAndSet(modelId, null)

    fun currentModelId(): String? = activeModelId.get()
}

internal object LocalModelDownloadRetryPolicy {
    const val MAX_ATTEMPTS = 3
    private const val MAX_BACKOFF_MS = 8_000L
    private const val MAX_SERVER_BACKOFF_MS = 15_000L

    private val retryableHttpCodes = setOf(408, 425, 429, 500, 502, 503, 504)

    fun isRetryableHttpCode(code: Int): Boolean = code in retryableHttpCodes

    /** [failedAttempt] is one-based. */
    fun retryDelayMillis(failedAttempt: Int, serverDelayMillis: Long? = null): Long {
        serverDelayMillis?.takeIf { it > 0L }?.let {
            return it.coerceAtMost(MAX_SERVER_BACKOFF_MS)
        }
        val shift = (failedAttempt - 1).coerceIn(0, 3)
        return (1_000L shl shift).coerceAtMost(MAX_BACKOFF_MS)
    }
}

package com.ad_glasses.localmodels.download

import android.content.Context
import android.util.Log
import com.ad_glasses.localmodels.catalog.LocalModelCatalogEntry
import com.ad_glasses.localmodels.device.DeviceCapabilityService
import com.ad_glasses.localmodels.settings.LocalModelSettingsRepository
import com.ad_glasses.localmodels.storage.InstalledLocalModel
import com.ad_glasses.localmodels.storage.LocalModelFileUtils
import com.ad_glasses.localmodels.storage.LocalModelStorageRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext

data class LocalModelDownloadProgress(
    val modelId: String,
    val downloadedBytes: Long,
    val totalBytes: Long,
) {
    val percent: Int
        get() = if (totalBytes <= 0L) 0 else ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
}

class LocalModelDownloadManager(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build(),
) {
    companion object {
        private const val TAG = "LocalModelDownload"
    }

    suspend fun downloadCatalogModel(
        context: Context,
        entry: LocalModelCatalogEntry,
        authToken: String?,
        cancelled: AtomicBoolean,
        onProgress: (LocalModelDownloadProgress) -> Unit,
        onStatus: (LocalModelDownloadStatus) -> Unit = {},
    ): InstalledLocalModel = withContext(Dispatchers.IO) {
        require(entry.enabled) { "Model is disabled in catalog" }
        val source = entry.sourceUrl ?: throw IllegalStateException("No direct source URL for this model")

        LocalModelStorageRepository.ensureDirs(context)
        LocalModelStorageRepository.findByCatalogId(context, entry.id)?.let { existing ->
            val f = File(existing.absolutePath)
            if (f.exists() && LocalModelFileUtils.isFileCompatibleWithFormat(f, entry.format)) {
                return@withContext existing
            }
        }

        val assessment = DeviceCapabilityService.assess(
            snapshot = DeviceCapabilityService.snapshot(context),
            entry = entry,
            requireDownloadHeadroom = true,
        )
        if (!assessment.supported) {
            throw IllegalStateException(assessment.blockers.joinToString(" "))
        }

        val tmpFile = File(
            LocalModelStorageRepository.tempDir(context),
            "${LocalModelFileUtils.sanitizeFileName(entry.expectedFilename)}.part",
        )
        var completed = false
        try {
            var lastError: Throwable? = null
            for (attempt in 1..LocalModelDownloadRetryPolicy.MAX_ATTEMPTS) {
                ensureNotCancelled(cancelled)
                if (tmpFile.exists() && !tmpFile.delete()) {
                    // FileOutputStream still truncates a stale part file. Log the failed delete
                    // because it can indicate storage trouble worth preserving in diagnostics.
                    Log.w(TAG, "Could not remove stale partial download: ${tmpFile.absolutePath}")
                }
                onStatus(
                    LocalModelDownloadStatus(
                        modelId = entry.id,
                        phase = LocalModelDownloadPhase.DOWNLOADING,
                        message = if (attempt == 1) {
                            "Downloading ${entry.displayName}…"
                        } else {
                            "Downloading ${entry.displayName} " +
                                "(attempt $attempt/${LocalModelDownloadRetryPolicy.MAX_ATTEMPTS})…"
                        },
                        attempt = attempt,
                        maxAttempts = LocalModelDownloadRetryPolicy.MAX_ATTEMPTS,
                    ),
                )

                try {
                    val installed = downloadOnce(
                        context = context,
                        entry = entry,
                        authToken = authToken,
                        cancelled = cancelled,
                        onProgress = onProgress,
                        onStatus = onStatus,
                        tmpFile = tmpFile,
                        source = source,
                        attempt = attempt,
                    )
                    LocalModelSettingsRepository.initializeCatalogDefaultsIfMissing(
                        context = context,
                        entry = entry,
                        profile = assessment.recommendedProfile,
                    )
                    completed = true
                    return@withContext installed
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    ensureNotCancelled(cancelled)
                    val retryDelayMs = retryDelayMillis(e, attempt)
                    if (retryDelayMs == null || attempt >= LocalModelDownloadRetryPolicy.MAX_ATTEMPTS) {
                        throw e
                    }
                    lastError = e
                    val nextAttempt = attempt + 1
                    Log.w(
                        TAG,
                        "Download attempt $attempt/${LocalModelDownloadRetryPolicy.MAX_ATTEMPTS} failed; " +
                            "retrying in ${retryDelayMs}ms: ${e.message}",
                        e,
                    )
                    onStatus(
                        LocalModelDownloadStatus(
                            modelId = entry.id,
                            phase = LocalModelDownloadPhase.RETRYING,
                            message = "Connection interrupted. Retrying " +
                                "$nextAttempt/${LocalModelDownloadRetryPolicy.MAX_ATTEMPTS}…",
                            attempt = attempt,
                            maxAttempts = LocalModelDownloadRetryPolicy.MAX_ATTEMPTS,
                        ),
                    )
                    delay(retryDelayMs)
                }
            }
            throw lastError ?: IllegalStateException(
                "Download failed after ${LocalModelDownloadRetryPolicy.MAX_ATTEMPTS} attempts",
            )
        } finally {
            if (!completed && tmpFile.exists() && !tmpFile.delete()) {
                Log.w(TAG, "Could not clean partial download: ${tmpFile.absolutePath}")
            }
        }
    }

    private suspend fun downloadOnce(
        context: Context,
        entry: LocalModelCatalogEntry,
        authToken: String?,
        cancelled: AtomicBoolean,
        onProgress: (LocalModelDownloadProgress) -> Unit,
        onStatus: (LocalModelDownloadStatus) -> Unit,
        tmpFile: File,
        source: String,
        attempt: Int,
    ): InstalledLocalModel {
        val requestBuilder = Request.Builder().url(source).get()
        if (!authToken.isNullOrBlank() && source.contains("huggingface.co", ignoreCase = true)) {
            requestBuilder.addHeader("Authorization", "Bearer ${authToken.trim()}")
        }
        val request = requestBuilder.build()
        val call = client.newCall(request)
        ensureNotCancelled(cancelled)
        val cancellationHandle = coroutineContext[Job]?.invokeOnCompletion { cause ->
            if (cause is CancellationException) call.cancel()
        }

        try {
            val response = try {
                call.execute()
            } catch (e: IOException) {
                ensureNotCancelled(cancelled)
                throw DownloadTransportException(
                    "Network connection failed: ${e.message ?: e.javaClass.simpleName}",
                    e,
                )
            }
            response.use {
                if (!response.isSuccessful) {
                    val reason = when (response.code) {
                        401, 403 -> {
                            if (entry.gatedDownload) {
                                "gated model access denied. Accept terms on Hugging Face and set a valid token"
                            } else {
                                "authorization failed"
                            }
                        }
                        else -> "HTTP ${response.code}"
                    }
                    throw DownloadHttpException(
                        statusCode = response.code,
                        serverDelayMillis = parseRetryAfterMillis(response.header("Retry-After")),
                        message = "Download failed: $reason",
                    )
                }
                val body = response.body ?: throw IllegalStateException("Download failed: empty body")
                val serverContentLength = body.contentLength()
                val total = when {
                    serverContentLength > 0L -> serverContentLength
                    entry.sizeBytes > 0L -> entry.sizeBytes
                    else -> 0L
                }

                val free = LocalModelStorageRepository.availableStorageBytes(context)
                if (total > 0 && free <= total + 250L * 1024L * 1024L) {
                    throw IllegalStateException("Not enough free space to download this model")
                }

                val digest = MessageDigest.getInstance("SHA-256")
                var downloaded = 0L
                onProgress(LocalModelDownloadProgress(entry.id, downloaded, total))
                body.byteStream().use { input ->
                    FileOutputStream(tmpFile, false).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            coroutineContext.ensureActive()
                            if (cancelled.get()) {
                                call.cancel()
                                throw CancellationException("Download cancelled")
                            }

                            val n = try {
                                input.read(buffer)
                            } catch (e: IOException) {
                                ensureNotCancelled(cancelled)
                                throw DownloadTransportException(
                                    "Network stream interrupted: ${e.message ?: e.javaClass.simpleName}",
                                    e,
                                )
                            }
                            if (n <= 0) break
                            output.write(buffer, 0, n)
                            digest.update(buffer, 0, n)
                            downloaded += n
                            onProgress(
                                LocalModelDownloadProgress(
                                    modelId = entry.id,
                                    downloadedBytes = downloaded,
                                    totalBytes = total,
                                ),
                            )
                        }
                        output.flush()

                        if (serverContentLength > 0L && downloaded != serverContentLength) {
                            throw DownloadTransportException(
                                "Download incomplete: expected ${serverContentLength} bytes, got $downloaded bytes",
                            )
                        }
                    }
                }

                onStatus(
                    LocalModelDownloadStatus(
                        modelId = entry.id,
                        phase = LocalModelDownloadPhase.VERIFYING,
                        message = "Verifying ${entry.displayName}…",
                        attempt = attempt,
                        maxAttempts = LocalModelDownloadRetryPolicy.MAX_ATTEMPTS,
                    ),
                )
                if (!LocalModelFileUtils.isFileCompatibleWithFormat(tmpFile, entry.format)) {
                    throw IllegalStateException(
                        "Downloaded file is not a valid ${entry.format} model package. " +
                            "For gated models, verify Hugging Face token + accepted terms.",
                    )
                }

                // SHA-256 is accumulated during the network copy, avoiding two additional full
                // reads of a potentially multi-gigabyte file during verification/registration.
                val fileSha = digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
                if (!entry.sha256.isNullOrBlank() && !entry.sha256.equals(fileSha, ignoreCase = true)) {
                    throw IllegalStateException("Checksum mismatch for downloaded model")
                }

                onStatus(
                    LocalModelDownloadStatus(
                        modelId = entry.id,
                        phase = LocalModelDownloadPhase.INSTALLING,
                        message = "Installing ${entry.displayName}…",
                        attempt = attempt,
                        maxAttempts = LocalModelDownloadRetryPolicy.MAX_ATTEMPTS,
                    ),
                )
                val finalName = LocalModelFileUtils.sanitizeFileName(entry.expectedFilename)
                val finalFile = File(LocalModelStorageRepository.modelsDir(context), finalName)
                if (finalFile.exists() && !finalFile.delete()) {
                    throw IllegalStateException("Cannot replace the existing model file")
                }
                if (!tmpFile.renameTo(finalFile)) {
                    tmpFile.copyTo(finalFile, overwrite = true)
                    if (!tmpFile.delete()) Log.w(TAG, "Could not remove copied partial file")
                }

                return LocalModelStorageRepository.registerCatalogModel(
                    context = context,
                    entry = entry,
                    file = finalFile,
                    verifiedSha256 = fileSha,
                )
            }
        } finally {
            cancellationHandle?.dispose()
        }
    }

    private suspend fun ensureNotCancelled(cancelled: AtomicBoolean) {
        coroutineContext.ensureActive()
        if (cancelled.get()) throw CancellationException("Download cancelled")
    }

    private fun retryDelayMillis(error: Throwable, failedAttempt: Int): Long? {
        return when (error) {
            is DownloadTransportException -> LocalModelDownloadRetryPolicy.retryDelayMillis(failedAttempt)
            is DownloadHttpException -> if (LocalModelDownloadRetryPolicy.isRetryableHttpCode(error.statusCode)) {
                LocalModelDownloadRetryPolicy.retryDelayMillis(failedAttempt, error.serverDelayMillis)
            } else {
                null
            }
            else -> null
        }
    }

    private fun parseRetryAfterMillis(value: String?): Long? {
        val seconds = value?.trim()?.toLongOrNull() ?: return null
        return seconds.coerceAtLeast(0L).let { safeSeconds ->
            if (safeSeconds > Long.MAX_VALUE / 1_000L) Long.MAX_VALUE else safeSeconds * 1_000L
        }
    }
}

private class DownloadTransportException(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

private class DownloadHttpException(
    val statusCode: Int,
    val serverDelayMillis: Long?,
    message: String,
) : IllegalStateException(message)

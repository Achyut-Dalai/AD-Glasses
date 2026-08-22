package com.ad_glasses.plugins.visualdiary

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.ad_glasses.devices.DeviceProfileStore
import com.ad_glasses.glasses.GlassesSessionCoordinator
import com.ad_glasses.localagent.userfacts.CandidateUserFactsStorage
import com.ad_glasses.localmodels.provider.LocalModelRequestPriority
import com.ad_glasses.localmodels.provider.LocalModelsProvider
import com.ad_glasses.localmodels.settings.LocalModelRuntime
import com.ad_glasses.localmodels.settings.LocalModelSettingsRepository
import com.ad_glasses.localmodels.storage.LocalModelStorageRepository
import com.oudmon.ble.base.bluetooth.BleOperateManager
import com.oudmon.ble.base.communication.LargeDataHandler
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Timeline's private scene-note pipeline.
 *
 * This is deliberately independent of the retired automatic-audio feature. For HeyCyan it
 * requests the existing low-resolution glasses thumbnail; camera adapters such as Meta can
 * provide a captured photo directly. The image is described locally and appended as a private
 * candidate fact.
 */
internal object TimelineVisualNoteGenerator {
    private const val TAG = "TimelineVisualNote"
    private const val MAX_IMAGE_AGE_MS = 3L * 60L * 1000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlight = AtomicBoolean(false)
    private val localModelsProvider = LocalModelsProvider()

    fun enqueueStandalone(context: Context, loopIndex: Int, promptOverride: String = "") {
        enqueueInternal(
            context = context.applicationContext,
            loopIndex = loopIndex,
            promptOverride = promptOverride.takeIf { it.isNotBlank() },
        )
    }

    fun enqueueCapturedPhoto(
        context: Context,
        loopIndex: Int,
        image: File,
        promptOverride: String = "",
    ) {
        val appContext = context.applicationContext
        if (!image.exists() || image.length() < 1024L) return
        if (!inFlight.compareAndSet(false, true)) {
            Log.i(TAG, "Timeline scene analysis already in progress; skipping supplied photo #$loopIndex")
            return
        }
        scope.launch {
            try {
                describeAndAppend(
                    context = appContext,
                    loopIndex = loopIndex,
                    image = image,
                    promptOverride = promptOverride.takeIf { it.isNotBlank() },
                )
            } catch (t: Throwable) {
                Log.e(TAG, "Supplied Timeline scene pipeline failed: ${t.message}", t)
            } finally {
                inFlight.set(false)
            }
        }
    }

    private fun enqueueInternal(context: Context, loopIndex: Int, promptOverride: String?) {
        GlassesSessionCoordinator.currentSession()?.let { session ->
            Log.i(TAG, "Skipping Timeline capture while ${session.label} owns the SDK BLE/P2P slots")
            return
        }
        if (!inFlight.compareAndSet(false, true)) {
            Log.i(TAG, "Timeline scene analysis already in progress; skipping #$loopIndex")
            return
        }
        scope.launch {
            try {
                captureDescribeAndAppend(context, loopIndex, promptOverride)
            } catch (t: Throwable) {
                Log.e(TAG, "Timeline scene pipeline failed: ${t.message}", t)
            } finally {
                inFlight.set(false)
            }
        }
    }

    private suspend fun captureDescribeAndAppend(
        context: Context,
        loopIndex: Int,
        promptOverride: String?,
    ) {
        if (!BleOperateManager.getInstance().isConnected) {
            Log.w(TAG, "Skipping Timeline note: glasses not connected")
            return
        }
        val image = captureThumbnail(context, loopIndex)
        if (image == null || !image.exists() || image.length() < 1024L) {
            Log.w(TAG, "Skipping Timeline note: no usable thumbnail")
            return
        }
        describeAndAppend(context, loopIndex, image, promptOverride)
    }

    private suspend fun describeAndAppend(
        context: Context,
        loopIndex: Int,
        image: File,
        promptOverride: String?,
    ) {
        if (!isImageFresh(image, MAX_IMAGE_AGE_MS)) {
            Log.w(TAG, "Skipping Timeline note: image is stale (lastModified=${image.lastModified()})")
            showToast(context, "Thumbnail too old, skipping scene analysis")
            return
        }

        val description = describeWithGemma(context, image, promptOverride) ?: return
        val fact = buildFact(description)
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(System.currentTimeMillis()))
        CandidateUserFactsStorage.append(context, date, listOf(fact))
        Log.i(TAG, "Appended Timeline visual candidate fact for capture #$loopIndex")
    }

    private fun buildFact(description: String): String {
        val time = SimpleDateFormat("HH:mm", Locale.US).format(Date(System.currentTimeMillis()))
        val cleaned = description
            .trim()
            .removePrefix("- ")
            .removePrefix("* ")
            .replace(Regex("\\s+"), " ")
            .trim()
        val clipped = if (cleaned.length > 190) cleaned.take(187).trimEnd() + "..." else cleaned
        return "Glasses scene $time: $clipped"
    }

    private suspend fun describeWithGemma(
        context: Context,
        image: File,
        promptOverride: String?,
    ): String? {
        val selected = LocalModelStorageRepository.resolveSelectedModel(context)
        if (selected == null) {
            Log.w(TAG, "Skipping Timeline note: no local model selected")
            return null
        }
        val settings = LocalModelSettingsRepository.getForModel(context, selected.id)
        if (settings.modelRuntime != LocalModelRuntime.LITERT) {
            Log.w(TAG, "Skipping Timeline note: selected model runtime is not LiteRT")
            return null
        }
        val hint = "${selected.displayName} ${selected.catalogId.orEmpty()}".lowercase(Locale.US)
        if (!hint.contains("gemma") || (!hint.contains("gemma-4") && !hint.contains("gemma4"))) {
            Log.w(TAG, "Skipping Timeline note: selected model is not Gemma 4")
            return null
        }

        val raw = localModelsProvider.streamChat(
            context = context,
            messages = listOf(
                mapOf(
                    "role" to "User",
                    "content" to (promptOverride?.takeIf { it.isNotBlank() }
                        ?: "Describe this scene in one concise sentence focusing on meaningful context (people, activity, location, tools, text on screen)."),
                ),
            ),
            imagePaths = listOf(image.absolutePath),
            requestPriority = LocalModelRequestPriority.HIGH,
        ).trim()

        if (raw.isBlank()) {
            Log.w(TAG, "Skipping Timeline note: empty Gemma response")
            return null
        }
        return raw
            .lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            ?.removePrefix("- ")
            ?.removePrefix("* ")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private suspend fun captureThumbnail(context: Context, loopIndex: Int): File? {
        val permit = GlassesSessionCoordinator.tryAcquireBackgroundCommand()
        if (permit == null) {
            Log.i(TAG, "Skipping Timeline thumbnail because the glasses SDK is busy")
            return null
        }
        var thumbnailTransferStarted = false
        try {
            val outDir = context.getExternalFilesDir("DCIM") ?: context.filesDir
            val file = File(outDir, "TIMELINE_THUMB_${loopIndex}_${System.currentTimeMillis()}.jpg")
            runCatching {
                file.parentFile?.mkdirs()
                if (file.exists()) file.delete()
            }

            val completed = AtomicBoolean(false)
            val done = CompletableDeferred<File?>()
            val thumbCallback: (Int, Boolean, ByteArray?) -> Unit = { _, isComplete, data ->
                if (data != null && data.isNotEmpty()) {
                    runCatching {
                        FileOutputStream(file, true).use { out -> out.write(data) }
                    }.onFailure {
                        Log.e(TAG, "Failed writing Timeline thumbnail chunk: ${it.message}", it)
                    }
                }
                if (isComplete && completed.compareAndSet(false, true)) {
                    if (!done.isCompleted) {
                        done.complete(if (file.exists() && file.length() >= 1024L) file else null)
                    }
                    GlassesSessionCoordinator.releaseBackgroundCommand(permit)
                }
            }

            // Preserve the known HeyCyan thumbnail command sequence. This transport sequence is
            // intentionally not modernized without glasses hardware validation.
            runCatching {
                LargeDataHandler.getInstance().glassesControl(byteArrayOf(0x02, 0x01, 0x06, 0x02, 0x02)) { _, _ -> }
            }
            delay(250)
            runCatching {
                LargeDataHandler.getInstance().glassesControl(byteArrayOf(0x02, 0x01, 0x01)) { _, _ -> }
            }
            delay(2500)

            LargeDataHandler.getInstance().getPictureThumbnails(thumbCallback)
            thumbnailTransferStarted = true
            val result = withTimeoutOrNull(14_000) { done.await() }
            if (result == null) {
                Log.w(TAG, "Timeline thumbnail timed out; keeping SDK isolation until completion or reconnect")
            }
            return result
        } catch (e: Exception) {
            if (!thumbnailTransferStarted) {
                GlassesSessionCoordinator.releaseBackgroundCommand(permit)
            }
            throw e
        }
    }

    private fun isImageFresh(file: File, maxAgeMs: Long): Boolean {
        val age = System.currentTimeMillis() - file.lastModified()
        return age >= 0 && age <= maxAgeMs
    }

    private fun showToast(context: Context, message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }
}

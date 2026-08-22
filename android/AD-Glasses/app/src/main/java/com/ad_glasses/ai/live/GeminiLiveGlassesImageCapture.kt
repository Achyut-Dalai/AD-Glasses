package com.ad_glasses.ai.live

import android.graphics.BitmapFactory
import com.ad_glasses.ai.image.ImageThumbnailQuality
import com.ad_glasses.shared.glasses.GlassesSessionCoordinator
import com.oudmon.ble.base.bluetooth.BleOperateManager
import com.oudmon.ble.base.communication.LargeDataHandler
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream

/** Reuses the glasses AI-image command and the user's configured BLE thumbnail quality. */
class GeminiLiveGlassesImageCapture {
    suspend fun capture(quality: ImageThumbnailQuality): ByteArray {
        check(BleOperateManager.getInstance().isConnected) { "Glasses are not connected" }
        val permit = GlassesSessionCoordinator.tryAcquireBackgroundCommand()
            ?: throw IllegalStateException("Glasses are busy with another operation")
        try {
            // Matches the existing Glasses-tab AI image flow: command 0x06 selects thumbnail fidelity.
            LargeDataHandler.getInstance().glassesControl(
                byteArrayOf(0x02, 0x01, 0x06, quality.sdkValue.toByte(), quality.sdkValue.toByte()),
            ) { _, _ -> }
            delay(CAPTURE_SETTLE_MS)
            return receiveThumbnail()
        } finally {
            GlassesSessionCoordinator.releaseBackgroundCommand(permit)
        }
    }

    /** Reads the thumbnail already taken by the glasses' physical AI-photo button. */
    suspend fun captureFromHardwareButton(): ByteArray {
        check(BleOperateManager.getInstance().isConnected) { "Glasses are not connected" }
        val permit = GlassesSessionCoordinator.tryAcquireBackgroundCommand()
            ?: throw IllegalStateException("Glasses are busy with another operation")
        try {
            return receiveThumbnail()
        } finally {
            GlassesSessionCoordinator.releaseBackgroundCommand(permit)
        }
    }

    private suspend fun receiveThumbnail(): ByteArray {
        val output = ByteArrayOutputStream()
        val complete = CompletableDeferred<Boolean>()
        LargeDataHandler.getInstance().getPictureThumbnails { _, isComplete, data ->
            if (data != null && data.isNotEmpty()) output.write(data)
            if (isComplete && !complete.isCompleted) complete.complete(output.size() > 0)
        }
        check(withTimeoutOrNull(TRANSFER_TIMEOUT_MS) { complete.await() } == true) {
            "Glasses thumbnail transfer timed out"
        }
        return output.toByteArray().also { image ->
            check(BitmapFactory.decodeByteArray(image, 0, image.size) != null) {
                "Glasses returned an invalid thumbnail"
            }
        }
    }

    private companion object {
        const val CAPTURE_SETTLE_MS = 4_000L
        const val TRANSFER_TIMEOUT_MS = 10_000L
    }
}

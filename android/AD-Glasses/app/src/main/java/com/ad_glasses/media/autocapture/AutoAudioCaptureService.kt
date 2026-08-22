package com.ad_glasses.media.autocapture

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder

/**
 * Non-runnable compatibility shell for inherited MainActivity references.
 *
 * Automatic background audio capture is removed from the product. The service is no longer
 * registered in the manifest, and these entry points intentionally do nothing until the old
 * host references are deleted during the MainActivity controller extraction.
 */
@Deprecated("Automatic background audio capture has been removed")
class AutoAudioCaptureService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        stopSelf(startId)
        return START_NOT_STICKY
    }

    companion object {
        const val ACTION_START = "com.ad_glasses.action.AUTO_AUDIO_CAPTURE_START"
        const val ACTION_STOP = "com.ad_glasses.action.AUTO_AUDIO_CAPTURE_STOP"

        fun start(context: Context) = Unit
        fun stop(context: Context) = Unit
        fun isRunning(): Boolean = false
    }
}

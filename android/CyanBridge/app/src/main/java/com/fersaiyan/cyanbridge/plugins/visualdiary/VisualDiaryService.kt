package com.fersaiyan.cyanbridge.plugins.visualdiary

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.fersaiyan.cyanbridge.MainActivity
import com.fersaiyan.cyanbridge.R
import com.fersaiyan.cyanbridge.devices.DeviceProfileStore
import com.fersaiyan.cyanbridge.devices.metarayban.MetaRaybanManager
import com.fersaiyan.cyanbridge.media.autocapture.AutoLoopVisualNoteGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Periodic visual-diary host. HeyCyan uses its existing thumbnail path; Meta
 * uses the shared DAT one-shot camera path before entering the same Gemma pipeline.
 */
class VisualDiaryService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loopJob: Job? = null
    private val captureIndex = AtomicInteger(0)

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startLoop()
            ACTION_STOP -> stopLoop()
            ACTION_CAPTURE_NOW -> captureNowAndKeepAlive()
            null -> if (VisualDiaryPreferences.isEnabled(this)) startLoop() else stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopLoop(clearPreference = false)
        scope.cancel()
        super.onDestroy()
    }

    private fun startLoop() {
        VisualDiaryPreferences.setEnabled(this, true)
        if (RUNNING.getAndSet(true)) return

        if (!startForegroundSafely("Visual Diary is ready")) {
            RUNNING.set(false)
            VisualDiaryPreferences.setEnabled(this, false)
            stopSelf()
            return
        }

        loopJob = scope.launch {
            if (DeviceProfileStore.isMetaSelected(this@VisualDiaryService)) {
                val manager = MetaRaybanManager.getInstance(this@VisualDiaryService)
                if (!manager.isInitialized.value) manager.initialize()
                if (!manager.awaitCameraReady()) {
                    val detail = manager.lastError.value
                        ?: "Register and connect a Meta camera before using Visual Diary"
                    Log.e(
                        TAG,
                        "Meta Visual Diary cannot start: $detail\n${manager.diagnosticsSnapshot()}",
                    )
                    updateNotification("Meta camera unavailable: ${detail.take(120)}")
                    VisualDiaryPreferences.setEnabled(this@VisualDiaryService, false)
                    stopLoop()
                    return@launch
                }
            }
            while (isActive && VisualDiaryPreferences.isEnabled(this@VisualDiaryService)) {
                captureNow()
                val delayMs = VisualDiaryPreferences.getIntervalMinutes(this@VisualDiaryService)
                    .toLong()
                    .coerceAtLeast(1L) * 60_000L
                delay(delayMs)
            }
            stopLoop()
        }
    }

    private fun captureNowAndKeepAlive() {
        if (!startForegroundSafely("Capturing a glasses scene")) {
            stopSelf()
            return
        }
        scope.launch {
            captureNow()
            delay(CAPTURE_KEEP_ALIVE_MS)
            if (!RUNNING.get()) stopSelf()
        }
    }

    private suspend fun captureNow() {
        val index = captureIndex.incrementAndGet()
        updateNotification("Capturing scene note #$index")

        if (DeviceProfileStore.isMetaSelected(this)) {
            val manager = MetaRaybanManager.getInstance(this)
            if (!manager.isInitialized.value) manager.initialize()
            val file = runCatching {
                val photo = manager.capturePhotoOnce()
                manager.savePhotoForProcessing(photo, "META_VISUAL_NOTE_$index")
            }.onFailure {
                val detail = manager.lastError.value ?: it.message ?: "camera unavailable"
                Log.e(TAG, "Meta DAT photo capture failed: $detail\n${manager.diagnosticsSnapshot()}", it)
                updateNotification("Meta capture failed: ${detail.take(120)}")
            }
                .getOrNull()
            if (file == null) {
                if (manager.lastError.value.isNullOrBlank()) {
                    updateNotification("Meta camera unavailable")
                }
                return
            }

            AutoLoopVisualNoteGenerator.enqueueCapturedPhoto(
                context = this,
                loopIndex = index,
                image = file,
                promptOverride = VisualDiaryPreferences.getCustomPrompt(this),
            )
            return
        }

        AutoLoopVisualNoteGenerator.enqueueStandalone(
            context = this,
            loopIndex = index,
            promptOverride = VisualDiaryPreferences.getCustomPrompt(this),
        )
    }

    private fun stopLoop(clearPreference: Boolean = true) {
        if (clearPreference) VisualDiaryPreferences.setEnabled(this, false)
        RUNNING.set(false)
        loopJob?.cancel()
        loopJob = null
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }

    private fun startForegroundSafely(content: String): Boolean {
        return runCatching {
            val notification = notification(content)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }.isSuccess
    }

    private fun updateNotification(content: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runCatching { manager.notify(NOTIFICATION_ID, notification(content)) }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Visual Diary",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Automatic glasses scene notes"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            },
        )
    }

    private fun notification(content: String): Notification {
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Visual Diary")
            .setContentText(content)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(RUNNING.get())
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(openPendingIntent)
            .build()
    }

    companion object {
        private const val TAG = "VisualDiaryService"
        private const val CHANNEL_ID = "visual_diary"
        private const val NOTIFICATION_ID = 55242
        private const val CAPTURE_KEEP_ALIVE_MS = 60_000L
        private val RUNNING = AtomicBoolean(false)

        const val ACTION_START = "com.fersaiyan.cyanbridge.action.VISUAL_DIARY_START"
        const val ACTION_STOP = "com.fersaiyan.cyanbridge.action.VISUAL_DIARY_STOP"
        const val ACTION_CAPTURE_NOW = "com.fersaiyan.cyanbridge.action.VISUAL_DIARY_CAPTURE_NOW"

        fun start(context: Context) {
            VisualDiaryPreferences.setEnabled(context, true)
            val intent = Intent(context, VisualDiaryService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            VisualDiaryPreferences.setEnabled(context, false)
            context.startService(Intent(context, VisualDiaryService::class.java).setAction(ACTION_STOP))
        }

        fun captureNow(context: Context) {
            val intent = Intent(context, VisualDiaryService::class.java).setAction(ACTION_CAPTURE_NOW)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
        }

        fun isRunning(): Boolean = RUNNING.get()
    }
}

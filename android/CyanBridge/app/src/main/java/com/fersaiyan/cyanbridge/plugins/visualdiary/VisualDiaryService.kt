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
import androidx.fragment.app.FragmentActivity
import com.fersaiyan.cyanbridge.R
import com.fersaiyan.cyanbridge.devices.DeviceCapabilityHelper
import com.fersaiyan.cyanbridge.devices.DeviceProfileStore
import com.fersaiyan.cyanbridge.devices.metarayban.MetaRaybanManager
import com.fersaiyan.cyanbridge.navigation.ADAppLaunchIntents
import com.fersaiyan.cyanbridge.shared.plugins.NativePluginIds
import com.fersaiyan.cyanbridge.ui.CommunityPluginPrefs
import com.fersaiyan.cyanbridge.ui.ensureNotificationPermission
import com.fersaiyan.cyanbridge.ui.hasNotificationPermission
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
 * Periodic Timeline host. HeyCyan uses its existing thumbnail path; Meta uses the shared
 * DAT one-shot camera path before entering the same private visual-note pipeline.
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
        if (!DeviceCapabilityHelper.hasCamera(this)) {
            Log.w(TAG, "Stopping Timeline: selected device profile has no camera")
            VisualDiaryPreferences.setEnabled(this, false)
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_START -> if (VisualDiaryPreferences.isEnabled(this)) startLoop() else stopSelf()
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
        if (RUNNING.getAndSet(true)) return
        if (!startForegroundSafely("Timeline is ready")) {
            RUNNING.set(false)
            disable(this, "Notification permission is required for Timeline")
            return
        }

        loopJob = scope.launch {
            if (DeviceProfileStore.isMetaSelected(this@VisualDiaryService)) {
                val manager = MetaRaybanManager.getInstance(this@VisualDiaryService)
                if (!manager.isInitialized.value) manager.initialize()
                if (!manager.awaitCameraReady()) {
                    val detail = manager.lastError.value
                        ?: "Register and connect a Meta camera before using Timeline"
                    Log.e(TAG, "Meta Timeline cannot start: $detail\n${manager.diagnosticsSnapshot()}")
                    VisualDiaryPreferences.setLastError(
                        this@VisualDiaryService,
                        "Meta camera unavailable: $detail",
                    )
                    stopLoop()
                    return@launch
                }
            }
            VisualDiaryPreferences.clearLastError(this@VisualDiaryService)
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
        if (!startForegroundSafely("Capturing a Timeline scene")) {
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
        updateNotification("Capturing Timeline note #$index")

        if (DeviceProfileStore.isMetaSelected(this)) {
            val manager = MetaRaybanManager.getInstance(this)
            if (!manager.isInitialized.value) manager.initialize()
            val file = runCatching {
                val photo = manager.capturePhotoOnce()
                manager.savePhotoForProcessing(photo, "META_VISUAL_NOTE_$index")
            }.onFailure {
                val detail = manager.lastError.value ?: it.message ?: "camera unavailable"
                Log.e(TAG, "Meta DAT photo capture failed: $detail\n${manager.diagnosticsSnapshot()}", it)
                VisualDiaryPreferences.setLastError(this, "Meta capture failed: $detail")
                updateNotification("Meta capture failed: ${detail.take(120)}")
            }.getOrNull()
            if (file == null) {
                if (manager.lastError.value.isNullOrBlank()) {
                    VisualDiaryPreferences.setLastError(this, "Meta camera unavailable")
                    updateNotification("Meta camera unavailable")
                }
                return
            }

            TimelineVisualNoteGenerator.enqueueCapturedPhoto(
                context = this,
                loopIndex = index,
                image = file,
                promptOverride = VisualDiaryPreferences.getCustomPrompt(this),
            )
            VisualDiaryPreferences.clearLastError(this)
            return
        }

        TimelineVisualNoteGenerator.enqueueStandalone(
            context = this,
            loopIndex = index,
            promptOverride = VisualDiaryPreferences.getCustomPrompt(this),
        )
        VisualDiaryPreferences.clearLastError(this)
    }

    private fun stopLoop(clearPreference: Boolean = true) {
        if (clearPreference) clearEnabledState(this)
        RUNNING.set(false)
        loopJob?.cancel()
        loopJob = null
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }

    private fun startForegroundSafely(content: String): Boolean {
        if (!hasNotificationPermission(this)) return false
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
                "Timeline",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Private automatic glasses scene notes"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            },
        )
    }

    private fun notification(content: String): Notification {
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            ADAppLaunchIntents.productHome(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_ad_glasses)
            .setContentTitle("Timeline")
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

        fun enable(context: Context): Boolean {
            if (!hasNotificationPermission(context)) {
                if (context is FragmentActivity) {
                    ensureNotificationPermission(
                        activity = context,
                        feature = "Timeline",
                        onDenied = { disable(context) },
                        onGranted = { enable(context) },
                    )
                }
                return false
            }
            setEnabledState(context, true)
            startIfEnabled(context)
            return true
        }

        fun startIfEnabled(context: Context): Boolean {
            if (!VisualDiaryPreferences.isEnabled(context) || !hasNotificationPermission(context)) {
                return false
            }
            val intent = Intent(context, VisualDiaryService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
            return true
        }

        fun disable(context: Context, error: String? = null) {
            clearEnabledState(context)
            if (error != null) VisualDiaryPreferences.setLastError(context, error)
            context.stopService(Intent(context, VisualDiaryService::class.java))
        }

        fun captureNow(context: Context) {
            if (!hasNotificationPermission(context)) {
                if (context is FragmentActivity) {
                    ensureNotificationPermission(context, "Timeline") {
                        captureNow(context)
                    }
                }
                return
            }
            val intent = Intent(context, VisualDiaryService::class.java).setAction(ACTION_CAPTURE_NOW)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
        }

        fun isRunning(): Boolean = RUNNING.get()

        private fun setEnabledState(context: Context, enabled: Boolean) {
            VisualDiaryPreferences.setEnabled(context, enabled)
            CommunityPluginPrefs.setNativePluginEnabled(context, NativePluginIds.VISUAL_DIARY, enabled)
        }

        private fun clearEnabledState(context: Context) {
            setEnabledState(context, false)
        }
    }
}

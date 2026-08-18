package com.fersaiyan.cyanbridge.plugins.autodiary

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
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.fersaiyan.cyanbridge.R
import com.fersaiyan.cyanbridge.agent.LocalAgentPrefs
import com.fersaiyan.cyanbridge.ai.orchestrator.AssistantCapabilityRuntimeEvents
import com.fersaiyan.cyanbridge.localagent.dailysummary.DailySummaryRegenerateWorker
import com.fersaiyan.cyanbridge.memoryvault.MemoryModeManager
import com.fersaiyan.cyanbridge.navigation.ADAppLaunchIntents
import com.fersaiyan.cyanbridge.shared.plugins.NativePluginIds
import com.fersaiyan.cyanbridge.ui.CommunityPluginPrefs
import com.fersaiyan.cyanbridge.ui.ensureNotificationPermission
import com.fersaiyan.cyanbridge.ui.hasAccessibilityServicePermission
import com.fersaiyan.cyanbridge.ui.hasNotificationPermission
import com.fersaiyan.cyanbridge.ui.requestAccessibilityServicePermission
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Native capability host for the existing screen-memory and daily-summary pipeline.
 * The accessibility service remains the screen observer; this service only owns
 * the capability lifecycle and summary commands.
 */
class AutoDiaryService : Service() {

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> if (isEnabled(this)) startDiary() else stopSelf()
            ACTION_STOP -> stopDiary()
            ACTION_SUMMARIZE -> {
                startForegroundSafely("Preparing today's DayNote summary")
                queueSummary(this)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            null -> if (isEnabled(this)) startDiary() else stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        RUNNING.set(false)
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        super.onDestroy()
    }

    private fun startDiary() {
        if (!hasAccessibilityServicePermission(this)) {
            disable(this)
            return
        }

        if (RUNNING.getAndSet(true)) return
        if (!startForegroundSafely("DayNote is collecting screen context")) {
            RUNNING.set(false)
            disable(this)
        }
    }

    private fun stopDiary() {
        clearEnabledState(this)
        RUNNING.set(false)
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

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "DayNote",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Private screen context and daily summaries"
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
            .setContentTitle("DayNote")
            .setContentText(content)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(openPendingIntent)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "auto_diary"
        private const val NOTIFICATION_ID = 55241
        private val RUNNING = AtomicBoolean(false)

        const val ACTION_START = "com.fersaiyan.cyanbridge.action.AUTO_DIARY_START"
        const val ACTION_STOP = "com.fersaiyan.cyanbridge.action.AUTO_DIARY_STOP"
        const val ACTION_SUMMARIZE = "com.fersaiyan.cyanbridge.action.AUTO_DIARY_SUMMARIZE"

        /** Enables DayNote only after its shared capture prerequisites are available. */
        fun enable(context: Context): Boolean {
            if (!hasAccessibilityServicePermission(context)) {
                if (context is FragmentActivity) {
                    requestAccessibilityServicePermission(context, "DayNote screen capture")
                }
                return false
            }
            if (!hasNotificationPermission(context)) {
                if (context is FragmentActivity) {
                    ensureNotificationPermission(
                        activity = context,
                        feature = "DayNote",
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

        /** Restores an already-enabled DayNote without prompting from a background context. */
        fun startIfEnabled(context: Context): Boolean {
            if (!isEnabled(context) ||
                !hasAccessibilityServicePermission(context) ||
                !hasNotificationPermission(context)
            ) {
                return false
            }
            val intent = Intent(context, AutoDiaryService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
            return true
        }

        fun disable(context: Context) {
            clearEnabledState(context)
            context.stopService(Intent(context, AutoDiaryService::class.java))
        }

        fun summarize(context: Context) {
            if (!hasNotificationPermission(context)) {
                if (context is FragmentActivity) {
                    ensureNotificationPermission(context, "DayNote") {
                        summarize(context)
                    }
                }
                return
            }
            val intent = Intent(context, AutoDiaryService::class.java).setAction(ACTION_SUMMARIZE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
        }

        fun isRunning(): Boolean = RUNNING.get()

        fun isEnabled(context: Context): Boolean =
            LocalAgentPrefs.isAutoCaptureEnabled(context) &&
                MemoryModeManager.isScreenOcrCaptureEnabled(context)

        private fun setEnabledState(context: Context, enabled: Boolean) {
            val changed = isEnabled(context) != enabled
            LocalAgentPrefs.setAutoCaptureEnabled(context, enabled)
            MemoryModeManager.setScreenOcrCaptureEnabled(context, enabled)
            CommunityPluginPrefs.setNativePluginEnabled(context, NativePluginIds.AUTO_DIARY, enabled)
            if (changed) AssistantCapabilityRuntimeEvents.notifyChanged()
        }

        private fun clearEnabledState(context: Context) {
            setEnabledState(context, false)
        }

        private fun queueSummary(context: Context) {
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(System.currentTimeMillis()))
            val request = OneTimeWorkRequestBuilder<DailySummaryRegenerateWorker>()
                .setInputData(workDataOf(DailySummaryRegenerateWorker.KEY_DATE to date))
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                DailySummaryRegenerateWorker.uniqueWorkName(date),
                ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }
}

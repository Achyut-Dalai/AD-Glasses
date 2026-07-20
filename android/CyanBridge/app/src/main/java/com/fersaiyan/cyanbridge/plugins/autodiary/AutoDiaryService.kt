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
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.fersaiyan.cyanbridge.MainActivity
import com.fersaiyan.cyanbridge.R
import com.fersaiyan.cyanbridge.agent.LocalAgentPrefs
import com.fersaiyan.cyanbridge.localagent.dailysummary.DailySummaryRegenerateWorker
import com.fersaiyan.cyanbridge.memoryvault.MemoryModeManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Native-plugin host for the existing screen-memory and daily-summary pipeline.
 * The accessibility service remains the screen observer; this service only owns
 * the plugin lifecycle and summary commands.
 */
class AutoDiaryService : Service() {

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startDiary()
            ACTION_STOP -> stopDiary()
            ACTION_SUMMARIZE -> {
                startForegroundSafely("Preparing today's diary summary")
                queueSummary(this)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            null -> if (LocalAgentPrefs.isAutoCaptureEnabled(this)) startDiary() else stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        RUNNING.set(false)
        super.onDestroy()
    }

    private fun startDiary() {
        LocalAgentPrefs.setAutoCaptureEnabled(this, true)
        MemoryModeManager.setScreenOcrCaptureEnabled(this, true)

        if (RUNNING.getAndSet(true)) return
        if (!startForegroundSafely("AutoDiary is collecting screen context")) {
            RUNNING.set(false)
            LocalAgentPrefs.setAutoCaptureEnabled(this, false)
            MemoryModeManager.setScreenOcrCaptureEnabled(this, false)
            stopSelf()
        }
    }

    private fun stopDiary() {
        LocalAgentPrefs.setAutoCaptureEnabled(this, false)
        MemoryModeManager.setScreenOcrCaptureEnabled(this, false)
        RUNNING.set(false)
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

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "AutoDiary",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Screen-memory and daily-summary automation"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            },
        )
    }

    private fun notification(content: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("AutoDiary")
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

        fun start(context: Context) {
            val intent = Intent(context, AutoDiaryService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            LocalAgentPrefs.setAutoCaptureEnabled(context, false)
            MemoryModeManager.setScreenOcrCaptureEnabled(context, false)
            context.startService(Intent(context, AutoDiaryService::class.java).setAction(ACTION_STOP))
        }

        fun summarize(context: Context) {
            val intent = Intent(context, AutoDiaryService::class.java).setAction(ACTION_SUMMARIZE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
        }

        fun isRunning(): Boolean = RUNNING.get()

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

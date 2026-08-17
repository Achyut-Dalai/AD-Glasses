package com.fersaiyan.cyanbridge.ai

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.fersaiyan.cyanbridge.MainActivity
import com.fersaiyan.cyanbridge.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Keeps an already-started AD glasses question/session alive while the visible Activity is stopped.
 * It supplies Android foreground-execution and wake-lock guarantees only; the selected assistant
 * runtime (Gemini Live, standard provider, or local AI) owns inference.
 */
class AiQuestionForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var idleStopJob: Job? = null
    private lateinit var wakeLock: PowerManager.WakeLock

    override fun onCreate() {
        super.onCreate()
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:ad-assistant")
            .apply { setReferenceCounted(false) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "AD Assistant", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopQuestionWork()
            return START_NOT_STICKY
        }

        if (intent == null) {
            stopQuestionWork()
            return START_NOT_STICKY
        }

        val status = intent.getStringExtra(EXTRA_STATUS).orEmpty().ifBlank { "AD is working through your glasses" }
        startForegroundSafely(status, isQueryActive = true)
        if (wakeLock.isHeld) wakeLock.release()
        wakeLock.acquire(MAX_WORK_DURATION_MS)
        idleStopJob?.cancel()
        idleStopJob = serviceScope.launch {
            delay(MAX_WORK_DURATION_MS)
            Log.w(TAG, "AD assistant wake lock expired")
            if (wakeLock.isHeld) wakeLock.release()
            stopQuestionWork()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        idleStopJob?.cancel()
        if (wakeLock.isHeld) wakeLock.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundSafely(status: String, isQueryActive: Boolean) {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_ad_glasses)
            .setContentTitle("AD Glasses")
            .setContentText(status)
            .setContentIntent(openApp)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .build()
        runCatching {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or
                        if (isQueryActive) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
                } else {
                    0
                },
            )
        }.onFailure { Log.e(TAG, "Unable to start AD assistant foreground service", it) }
    }

    private fun stopQuestionWork() {
        idleStopJob?.cancel()
        if (wakeLock.isHeld) wakeLock.release()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        private const val TAG = "ADAssistantForeground"
        private const val CHANNEL_ID = "ai_question_work"
        private const val NOTIFICATION_ID = 7043
        private const val MAX_WORK_DURATION_MS = 2L * 60L * 1000L
        private const val ACTION_START = "com.fersaiyan.cyanbridge.action.AI_QUESTION_START"
        private const val ACTION_STOP = "com.fersaiyan.cyanbridge.action.AI_QUESTION_STOP"
        private const val EXTRA_STATUS = "status"

        fun start(context: Context, status: String) {
            val intent = Intent(context, AiQuestionForegroundService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_STATUS, status)
            runCatching { ContextCompat.startForegroundService(context, intent) }
                .onFailure { Log.w(TAG, "Unable to request AD assistant foreground service", it) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AiQuestionForegroundService::class.java))
        }
    }
}

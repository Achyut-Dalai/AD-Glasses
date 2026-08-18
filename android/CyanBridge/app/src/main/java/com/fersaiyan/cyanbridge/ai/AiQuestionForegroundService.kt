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
import com.fersaiyan.cyanbridge.R
import com.fersaiyan.cyanbridge.glasses.runtime.ADGlassesCommandGateway
import com.fersaiyan.cyanbridge.glasses.runtime.ADPersistentGlassesRuntime
import com.fersaiyan.cyanbridge.ui.WelcomeActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Process-level foreground host for AD's Activity-independent glasses runtime and bounded AI work.
 *
 * The connected-device runtime may remain alive after every Activity is gone. A wake-word/query
 * temporarily adds wake-lock/session work, then returns to the lightweight glasses runtime instead
 * of tearing the whole transport down.
 */
class AiQuestionForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var idleStopJob: Job? = null
    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var persistentRuntime: ADPersistentGlassesRuntime
    private var runtimeRequested = false
    private var questionActive = false
    private var questionUsesPhoneMicrophone = false

    override fun onCreate() {
        super.onCreate()
        persistentRuntime = ADPersistentGlassesRuntime(applicationContext)
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:ad-assistant")
            .apply { setReferenceCounted(false) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "AD Glasses runtime", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RUNTIME_START -> {
                runtimeRequested = true
                ADGlassesCommandGateway.attachPersistent(persistentRuntime)
                startForegroundSafely("Glasses runtime ready", usesPhoneMicrophone = false)
                return START_STICKY
            }

            ACTION_RUNTIME_STOP -> {
                runtimeRequested = false
                ADGlassesCommandGateway.detachPersistent(persistentRuntime)
                if (!questionActive) stopRuntimeHost()
                return START_NOT_STICKY
            }

            ACTION_STOP -> {
                stopQuestionWork()
                return if (runtimeRequested) START_STICKY else START_NOT_STICKY
            }

            ACTION_START -> {
                // A direct question may start the service before the UI ever requested the
                // persistent runtime. Keep command ownership attached for the session either way.
                ADGlassesCommandGateway.attachPersistent(persistentRuntime)
                questionActive = true
                questionUsesPhoneMicrophone = intent.getBooleanExtra(EXTRA_PHONE_MICROPHONE, false)
                val status = intent.getStringExtra(EXTRA_STATUS).orEmpty().ifBlank { "AD is working through your glasses" }
                startForegroundSafely(status, usesPhoneMicrophone = questionUsesPhoneMicrophone)
                holdQuestionWakeLock()
                return if (runtimeRequested) START_STICKY else START_NOT_STICKY
            }

            null -> {
                if (runtimeRequested) {
                    ADGlassesCommandGateway.attachPersistent(persistentRuntime)
                    startForegroundSafely("Glasses runtime ready", usesPhoneMicrophone = false)
                    return START_STICKY
                }
                stopRuntimeHost()
                return START_NOT_STICKY
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        idleStopJob?.cancel()
        if (wakeLock.isHeld) wakeLock.release()
        ADGlassesCommandGateway.detachPersistent(persistentRuntime)
        serviceScope.coroutineContext[Job]?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun holdQuestionWakeLock() {
        if (wakeLock.isHeld) wakeLock.release()
        wakeLock.acquire(MAX_WORK_DURATION_MS)
        idleStopJob?.cancel()
        idleStopJob = serviceScope.launch {
            delay(MAX_WORK_DURATION_MS)
            Log.w(TAG, "AD assistant wake lock expired")
            stopQuestionWork()
        }
    }

    private fun startForegroundSafely(status: String, usesPhoneMicrophone: Boolean) {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, WelcomeActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
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
                        if (usesPhoneMicrophone) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
                } else {
                    0
                },
            )
        }.onFailure { Log.e(TAG, "Unable to start AD foreground runtime", it) }
    }

    private fun stopQuestionWork() {
        questionActive = false
        questionUsesPhoneMicrophone = false
        idleStopJob?.cancel()
        idleStopJob = null
        if (wakeLock.isHeld) wakeLock.release()
        if (runtimeRequested) {
            startForegroundSafely("Glasses runtime ready", usesPhoneMicrophone = false)
        } else {
            ADGlassesCommandGateway.detachPersistent(persistentRuntime)
            stopRuntimeHost()
        }
    }

    private fun stopRuntimeHost() {
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
        private const val ACTION_RUNTIME_START = "com.fersaiyan.cyanbridge.action.AD_RUNTIME_START"
        private const val ACTION_RUNTIME_STOP = "com.fersaiyan.cyanbridge.action.AD_RUNTIME_STOP"
        private const val EXTRA_STATUS = "status"
        private const val EXTRA_PHONE_MICROPHONE = "phone_microphone"

        fun startRuntime(context: Context) {
            val intent = Intent(context, AiQuestionForegroundService::class.java).setAction(ACTION_RUNTIME_START)
            runCatching { ContextCompat.startForegroundService(context, intent) }
                .onFailure { Log.w(TAG, "Unable to start persistent AD glasses runtime", it) }
        }

        fun stopRuntime(context: Context) {
            val intent = Intent(context, AiQuestionForegroundService::class.java).setAction(ACTION_RUNTIME_STOP)
            runCatching { context.startService(intent) }
                .onFailure { Log.w(TAG, "Unable to stop persistent AD glasses runtime", it) }
        }

        fun start(context: Context, status: String, usesPhoneMicrophone: Boolean = false) {
            val intent = Intent(context, AiQuestionForegroundService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_STATUS, status)
                .putExtra(EXTRA_PHONE_MICROPHONE, usesPhoneMicrophone)
            runCatching { ContextCompat.startForegroundService(context, intent) }
                .onFailure { Log.w(TAG, "Unable to request AD assistant foreground service", it) }
        }

        /** Ends only bounded question work; the connected glasses runtime may remain alive. */
        fun stop(context: Context) {
            val intent = Intent(context, AiQuestionForegroundService::class.java).setAction(ACTION_STOP)
            runCatching { context.startService(intent) }
                .onFailure { Log.w(TAG, "Unable to stop bounded AD question work", it) }
        }
    }
}
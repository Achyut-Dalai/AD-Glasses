package com.fersaiyan.cyanbridge.localmodels.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.fersaiyan.cyanbridge.agent.LocalModelsConfigureActivity
import com.fersaiyan.cyanbridge.localmodels.catalog.LocalModelCatalogRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class ModelDownloadForegroundService : Service() {

    companion object {
        const val TAG = "ModelDownloadSvc"
        const val CHANNEL_ID = "model_download_progress"
        const val NOTIFICATION_ID = 1001

        const val ACTION_DOWNLOAD = "com.fersaiyan.cyanbridge.action.DOWNLOAD_MODEL"
        const val ACTION_CANCEL = "com.fersaiyan.cyanbridge.action.CANCEL_DOWNLOAD"
        const val EXTRA_MODEL_ID = "model_id"
        const val EXTRA_HF_TOKEN = "hf_token"

        const val BROADCAST_DOWNLOAD_FINISHED = "com.fersaiyan.cyanbridge.DOWNLOAD_FINISHED"
        const val BROADCAST_PROGRESS = "com.fersaiyan.cyanbridge.DOWNLOAD_PROGRESS"
        const val EXTRA_SUCCESS = "success"
        const val EXTRA_ERROR = "error"
        const val EXTRA_PERCENT = "percent"
        const val EXTRA_DOWNLOADED_BYTES = "downloaded_bytes"
        const val EXTRA_TOTAL_BYTES = "total_bytes"
        const val EXTRA_PHASE = "phase"
        const val EXTRA_STATUS_MESSAGE = "status_message"

        private val downloadSlot = LocalModelDownloadSlot()

        @Volatile
        private var _lastResult: DownloadResult? = null
        val lastResult: DownloadResult? get() = _lastResult

        @Volatile
        private var _isDownloading: Boolean = false
        val isDownloading: Boolean get() = _isDownloading

        @Volatile
        private var _downloadingModelId: String? = null
        val downloadingModelId: String? get() = _downloadingModelId

        @Volatile
        private var _lastPercent: Int? = null
        val lastPercent: Int? get() = _lastPercent

        @Volatile
        private var _lastDownloadedBytes: Long? = null
        val lastDownloadedBytes: Long? get() = _lastDownloadedBytes

        @Volatile
        private var _lastTotalBytes: Long? = null
        val lastTotalBytes: Long? get() = _lastTotalBytes

        @Volatile
        private var _lastStatusMessage: String? = null
        val lastStatusMessage: String? get() = _lastStatusMessage

        @Volatile
        private var _lastPhase: LocalModelDownloadPhase? = null
        val lastPhase: LocalModelDownloadPhase? get() = _lastPhase

        data class DownloadResult(
            val success: Boolean,
            val modelId: String,
            val error: String?,
        )

        fun startDownload(context: Context, modelId: String, hfToken: String?) {
            val intent = Intent(context, ModelDownloadForegroundService::class.java).apply {
                action = ACTION_DOWNLOAD
                putExtra(EXTRA_MODEL_ID, modelId)
                putExtra(EXTRA_HF_TOKEN, hfToken)
            }
            context.startForegroundService(intent)
        }

        fun cancelDownload(context: Context) {
            val intent = Intent(context, ModelDownloadForegroundService::class.java).apply {
                action = ACTION_CANCEL
            }
            context.startService(intent)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val downloadManager = LocalModelDownloadManager()
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile
    private var activeRun: DownloadRun? = null
    private var lastProgressUpdateMs = 0L

    private class DownloadRun(
        val modelId: String,
        val cancelled: AtomicBoolean = AtomicBoolean(false),
        val terminalDelivered: AtomicBoolean = AtomicBoolean(false),
        @Volatile var job: Job? = null,
    )

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                requestCancellation()
            }
            ACTION_DOWNLOAD -> {
                val modelId = intent.getStringExtra(EXTRA_MODEL_ID) ?: run {
                    stopSelf()
                    return START_NOT_STICKY
                }
                if (!downloadSlot.tryAcquire(modelId)) {
                    Log.i(
                        TAG,
                        "Ignoring duplicate download start for $modelId; " +
                            "${downloadSlot.currentModelId()} is already active",
                    )
                    return START_NOT_STICKY
                }

                val run = DownloadRun(modelId)
                activeRun = run
                _isDownloading = true
                _downloadingModelId = modelId
                _lastPercent = null
                _lastDownloadedBytes = null
                _lastTotalBytes = null
                _lastStatusMessage = "Starting download..."
                _lastPhase = LocalModelDownloadPhase.DOWNLOADING
                _lastResult = null
                lastProgressUpdateMs = 0L
                Log.i(TAG, "Starting download for model: $modelId")

                try {
                    val notification = buildNotification("Starting download...", 0, true)
                    startForeground(NOTIFICATION_ID, notification)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start foreground service", e)
                }

                val job = scope.launch(start = CoroutineStart.LAZY) {
                    performDownload(run, intent.getStringExtra(EXTRA_HF_TOKEN))
                }
                run.job = job
                job.invokeOnCompletion { cause ->
                    if (cause is CancellationException) {
                        finishDownload(run, success = false, error = "cancelled")
                    }
                }
                job.start()
            }
            else -> {
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun performDownload(run: DownloadRun, hfToken: String?) {
        val modelId = run.modelId
        val entry = LocalModelCatalogRepository.findById(modelId) ?: run {
            Log.w(TAG, "Model $modelId not found in catalog")
            finishDownload(run, success = false, error = "Model not found in catalog")
            return
        }

        try {
            val model = downloadManager.downloadCatalogModel(
                context = this@ModelDownloadForegroundService,
                entry = entry,
                authToken = hfToken,
                cancelled = run.cancelled,
                onProgress = { progress ->
                    if (activeRun !== run || run.terminalDelivered.get()) return@downloadCatalogModel
                    val now = System.currentTimeMillis()
                    if (now - lastProgressUpdateMs >= 500L || progress.percent >= 100) {
                        lastProgressUpdateMs = now
                        try {
                            val notification = buildNotification(
                                text = "Downloading ${entry.displayName}: ${progress.percent}%",
                                progress = progress.percent,
                                ongoing = true,
                            )
                            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                            nm.notify(NOTIFICATION_ID, notification)
                        } catch (_: Exception) { }

                        sendProgressBroadcast(
                            modelId = entry.id,
                            percent = progress.percent,
                            downloadedBytes = progress.downloadedBytes,
                            totalBytes = progress.totalBytes,
                        )
                    }
                },
                onStatus = { status -> publishStatus(run, status) },
            )

            Log.i(TAG, "Download complete: ${model.displayName}")
            finishDownload(
                run = run,
                success = true,
                error = null,
                successMessage = "Download complete: ${model.displayName}",
            )
        } catch (err: Throwable) {
            if (err is CancellationException || run.cancelled.get()) {
                Log.i(TAG, "Download cancelled")
                finishDownload(run, success = false, error = "cancelled")
            } else {
                Log.e(TAG, "Download failed: ${err.message}", err)
                finishDownload(run, success = false, error = err.message ?: "unknown error")
            }
        }
    }

    private fun requestCancellation() {
        val run = activeRun
        if (run == null || run.terminalDelivered.get()) {
            Log.i(TAG, "Cancel requested with no active model download")
            if (!_isDownloading) stopSelf()
            return
        }

        Log.i(TAG, "Cancelling download for ${run.modelId}")
        run.cancelled.set(true)
        _lastStatusMessage = "Cancelling download…"
        runCatching {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(
                NOTIFICATION_ID,
                buildNotification(
                    text = "Cancelling download…",
                    progress = _lastPercent ?: 0,
                    ongoing = true,
                ),
            )
        }
        val job = run.job
        if (job == null) {
            finishDownload(run, success = false, error = "cancelled")
        } else {
            job.cancel(CancellationException("Download cancelled"))
        }
    }

    private fun publishStatus(run: DownloadRun, status: LocalModelDownloadStatus) {
        if (activeRun !== run || run.terminalDelivered.get()) return
        _lastPhase = status.phase
        _lastStatusMessage = status.message
        runCatching {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(
                NOTIFICATION_ID,
                buildNotification(
                    text = status.message,
                    progress = _lastPercent ?: 0,
                    ongoing = true,
                ),
            )
        }
        sendStatusBroadcast(status)
    }

    private fun finishDownload(
        run: DownloadRun,
        success: Boolean,
        error: String?,
        successMessage: String? = null,
    ) {
        if (!run.terminalDelivered.compareAndSet(false, true)) return

        val terminalError = if (success) null else error ?: "unknown error"
        _lastResult = DownloadResult(success = success, modelId = run.modelId, error = terminalError)
        _lastStatusMessage = when {
            success -> successMessage ?: "Download complete"
            terminalError == "cancelled" -> "Download cancelled"
            else -> "Download failed: $terminalError"
        }
        if (success) {
            _lastPercent = 100
            _lastPhase = LocalModelDownloadPhase.INSTALLING
        }

        if (activeRun === run) activeRun = null
        downloadSlot.release(run.modelId)
        _isDownloading = false
        _downloadingModelId = null

        if (terminalError != "cancelled") {
            runCatching {
                val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(
                    NOTIFICATION_ID,
                    buildNotification(
                        text = _lastStatusMessage.orEmpty(),
                        progress = if (success) 100 else 0,
                        ongoing = false,
                    ),
                )
            }
        }
        sendFinishedBroadcast(modelId = run.modelId, success = success, error = terminalError)
        scheduleServiceStop(removeImmediately = terminalError == "cancelled")
    }

    private fun scheduleServiceStop(removeImmediately: Boolean) {
        mainHandler.postDelayed(
            {
                // A new run may have started while the completion message was visible.
                if (activeRun == null && downloadSlot.currentModelId() == null) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            },
            if (removeImmediately) 0L else 3_000L,
        )
    }

    private fun buildNotification(text: String, progress: Int, ongoing: Boolean): android.app.Notification {
        val tapIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, LocalModelsConfigureActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val cancelIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, ModelDownloadForegroundService::class.java).apply {
                action = ACTION_CANCEL
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Model Download")
            .setContentText(text)
            .setOngoing(ongoing)
            .setProgress(100, progress, progress == 0 && ongoing)
            .setContentIntent(tapIntent)
            .apply {
                if (ongoing) {
                    addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelIntent)
                }
            }
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Model Downloads",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Shows model download progress"
                setShowBadge(false)
            }
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun sendProgressBroadcast(modelId: String, percent: Int, downloadedBytes: Long, totalBytes: Long) {
        _lastPercent = percent
        _lastDownloadedBytes = downloadedBytes
        _lastTotalBytes = totalBytes
        val entryName = LocalModelCatalogRepository.findById(modelId)?.displayName ?: modelId
        _lastStatusMessage = "Downloading $entryName: $percent%"

        val intent = Intent(BROADCAST_PROGRESS).apply {
            `package` = packageName
            putExtra(EXTRA_MODEL_ID, modelId)
            putExtra(EXTRA_PERCENT, percent)
            putExtra(EXTRA_DOWNLOADED_BYTES, downloadedBytes)
            putExtra(EXTRA_TOTAL_BYTES, totalBytes)
        }
        sendBroadcast(intent)
    }

    private fun sendStatusBroadcast(status: LocalModelDownloadStatus) {
        val intent = Intent(BROADCAST_PROGRESS).apply {
            `package` = packageName
            putExtra(EXTRA_MODEL_ID, status.modelId)
            putExtra(EXTRA_PERCENT, _lastPercent ?: 0)
            putExtra(EXTRA_DOWNLOADED_BYTES, _lastDownloadedBytes ?: 0L)
            putExtra(EXTRA_TOTAL_BYTES, _lastTotalBytes ?: 0L)
            putExtra(EXTRA_PHASE, status.phase.name)
            putExtra(EXTRA_STATUS_MESSAGE, status.message)
        }
        sendBroadcast(intent)
    }

    private fun sendFinishedBroadcast(modelId: String, success: Boolean, error: String?) {
        val intent = Intent(BROADCAST_DOWNLOAD_FINISHED).apply {
            `package` = packageName
            putExtra(EXTRA_SUCCESS, success)
            putExtra(EXTRA_MODEL_ID, modelId)
            error?.let { putExtra(EXTRA_ERROR, it) }
        }
        sendBroadcast(intent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        activeRun?.let { run ->
            run.cancelled.set(true)
            run.job?.cancel(CancellationException("Download service stopped"))
            if (run.terminalDelivered.compareAndSet(false, true)) {
                downloadSlot.release(run.modelId)
                _lastResult = DownloadResult(false, run.modelId, "cancelled")
                _lastStatusMessage = "Download cancelled"
                sendFinishedBroadcast(run.modelId, success = false, error = "cancelled")
            }
        }
        activeRun = null
        _isDownloading = false
        _downloadingModelId = null
        scope.cancel()
        super.onDestroy()
    }
}

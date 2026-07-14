package com.fersaiyan.cyanbridge.localagent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.fersaiyan.cyanbridge.MainActivity
import com.fersaiyan.cyanbridge.R
import com.fersaiyan.cyanbridge.agent.LocalAgentPrefs as AutomationPrefs
import com.fersaiyan.cyanbridge.localagent.memory.LocalAgentMemoryStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Foreground service that runs a simple observe -> plan -> act loop.
 *
 * - observe: best-effort screen text via AccessibilityService
 * - plan: via [LocalAgentBrain] (stubbed by default)
 * - act: execute JSON actions via Accessibility
 *
 * When an action requires user approval, the loop pauses and waits for
 * [LocalAgentIntents.ACTION_RESUME_AFTER_APPROVAL] to continue.
 */
class LocalAgentService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loopJob: Job? = null

    private var tts: TextToSpeech? = null
    private var ttsReady: CompletableDeferred<Boolean>? = null

    /** Signalled when the user approves a pending action; the loop awaits this. */
    private var approvalDeferred: CompletableDeferred<Boolean>? = null

    /** Stored task state so the loop can resume after approval. */
    private var pausedTaskState: LocalAgentTaskState? = null

    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        initTts()
        LocalAgentMemoryStore.ensureSeedFiles(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            // New contract (UI -> Service)
            LocalAgentIntents.ACTION_START -> startLoop(intent.getStringExtra(LocalAgentIntents.EXTRA_GOAL))
            LocalAgentIntents.ACTION_STOP -> stopLoop(reason = "user")
            LocalAgentIntents.ACTION_DEMO -> runDemo()
            LocalAgentIntents.ACTION_GET_STATUS -> emitStatus()
            LocalAgentIntents.ACTION_RESUME_AFTER_APPROVAL -> resumeAfterApproval()

            // Back-compat (older internal actions)
            ACTION_START_LEGACY -> startLoop(null)
            ACTION_STOP_LEGACY -> stopLoop(reason = "user")
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopLoop(reason = "service_destroy")
        approvalDeferred?.complete(false)
        approvalDeferred = null
        serviceScope.cancel()
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
        ttsReady = null
        super.onDestroy()
    }

    private fun startLoop(goalOverride: String?) {
        if (!AutomationPrefs.isLocalAgentAutomationEnabled(applicationContext)) {
            LocalAgentPrefs.setStatus(applicationContext, "Disabled")
            LocalAgentPrefs.setLastError(applicationContext, "local_agent_automation_disabled")
            emitStatus()
            stopSelf()
            return
        }

        if (isRunning.getAndSet(true)) {
            Log.i(TAG, "startLoop: already running")
            emitStatus()
            return
        }

        val goal = goalOverride?.trim()?.takeIf { it.isNotBlank() } ?: DEFAULT_GOAL
        val maxSteps = AutomationPrefs.getMaxSteps(applicationContext)

        lastError.set(null)
        LocalAgentPrefs.setStatus(applicationContext, "Running")
        LocalAgentPrefs.clearLastError(applicationContext)
        emitStatus()

        startForeground(NOTIFICATION_ID, buildNotification(content = "Running: ${goal.take(48)}"))

        val engine = LocalAgentStepEngine(
            context = applicationContext,
            executor = object : LocalAgentStepEngine.LocalAgentActionExecutor {
                override suspend fun execute(action: LocalAgentAction): Boolean {
                    return LocalAgentAccessibilityBridge.perform(action)
                }

                override fun ensureNotCancelled() {
                    // cooperative cancellation handled by coroutine.
                }
            }
        )

        loopJob = serviceScope.launch {
            Log.i(TAG, "Loop started")

            val brain = brainRef.get()
            var warnedA11yMissing = false
            // Resume from paused state if available, otherwise start fresh.
            var taskState = pausedTaskState ?: LocalAgentTaskState(
                goal = goal,
                maxSteps = maxSteps,
                startedAtMs = System.currentTimeMillis(),
            )
            pausedTaskState = null

            while (isActive && taskState.stepIndex <= taskState.maxSteps) {
                try {
                    if (!LocalAgentAccessibilityBridge.isConnected()) {
                        val err = "accessibility_not_connected"
                        lastError.set(err)
                        LocalAgentPrefs.setStatus(applicationContext, "Waiting for accessibility")
                        LocalAgentPrefs.setLastError(applicationContext, err)
                        emitStatus()

                        if (!warnedA11yMissing) {
                            warnedA11yMissing = true
                            Log.w(TAG, "Accessibility service not connected; enable it in Android Accessibility settings")
                        }
                        delay(1_000)
                        continue
                    }

                    LocalAgentPrefs.setStatus(
                        applicationContext,
                        "Running step ${taskState.stepIndex}/${taskState.maxSteps}"
                    )
                    emitStatus()

                    val obs = LocalAgentObserver.observe()
                    val out = brain.next(applicationContext, taskState, obs)
                    val actions = out.actions
                    if (actions.isNotEmpty()) {
                        Log.i(TAG, "Planned ${actions.size} actions. note=${out.note}")
                    }

                    val summary = engine.execute(actions)
                    val previousResult = buildString {
                        out.note?.takeIf { it.isNotBlank() }?.let {
                            append(it)
                        }
                        if (summary.actionResults.isNotEmpty()) {
                            if (isNotBlank()) append(" | ")
                            append(summary.actionResults.joinToString("; "))
                        }
                    }.ifBlank { out.note ?: "" }

                    if (out.isComplete || summary.finished) {
                        completeLoop(
                            taskId = taskState.startedAtMs,
                            status = "Completed",
                            notification = summary.actionResults.lastOrNull()
                                ?: out.note
                                ?: "Task completed",
                            userMessage = completionSpeech(actions),
                        )
                        return@launch
                    }

                    if (summary.haltedForApproval) {
                        // Pause the loop and wait for user approval instead of stopping.
                        Log.i(TAG, "Action requires approval; pausing loop.")
                        LocalAgentPrefs.setStatus(applicationContext, "Waiting for approval")
                        emitStatus()
                        runCatching {
                            notificationManager.notify(
                                NOTIFICATION_ID,
                                buildNotification(content = "Waiting for your approval"),
                            )
                        }

                        // Store task state so we can resume after approval.
                        pausedTaskState = taskState
                        val deferred = CompletableDeferred<Boolean>()
                        approvalDeferred = deferred

                        val approved = deferred.await()
                        approvalDeferred = null

                        if (!approved) {
                            // User rejected or service was destroyed.
                            completeLoop(
                                taskId = taskState.startedAtMs,
                                status = "Stopped",
                                notification = "Action rejected",
                                userMessage = "Action was not approved.",
                                error = "action_rejected",
                            )
                            return@launch
                        }

                        // User approved: the action was already executed by PendingActionsActivity.
                        // Record the result and advance to the next step.
                        Log.i(TAG, "Approval received; resuming loop.")
                        LocalAgentPrefs.setStatus(applicationContext, "Resuming after approval")
                        emitStatus()

                        taskState = taskState.nextStep(
                            previousActionResult = "Action approved and executed by user",
                            failed = false,
                        )
                        continue
                    }

                    val failed = summary.actionResults.any { it.endsWith("failed") }
                    taskState = taskState.nextStep(
                        previousActionResult = previousResult,
                        failed = failed,
                    )
                } catch (e: Exception) {
                    val msg = e.message ?: e.javaClass.simpleName
                    lastError.set(msg)
                    LocalAgentPrefs.setStatus(applicationContext, "Error")
                    LocalAgentPrefs.setLastError(applicationContext, msg)
                    emitStatus()
                    Log.e(TAG, "Loop error: $msg", e)
                    // Back off to avoid crash loops.
                    taskState = taskState.nextStep(previousActionResult = msg, failed = true)
                    delay(1_000)
                }

                // Throttle the main loop slightly so we don't spin at 100%.
                delay(250)
            }

            if (isActive) {
                completeLoop(
                    taskId = taskState.startedAtMs,
                    status = "Stopped",
                    notification = "Reached max steps (${taskState.maxSteps})",
                    userMessage = "I couldn't finish that task.",
                    error = "max_steps_reached",
                )
            }
        }
    }

    /**
     * Called when the user approves a pending action from [PendingActionsActivity].
     * Signals the paused loop to resume.
     */
    private fun resumeAfterApproval() {
        val deferred = approvalDeferred
        if (deferred != null && !deferred.isCompleted) {
            Log.i(TAG, "resumeAfterApproval: signalling deferred")
            deferred.complete(true)
        } else {
            // No loop waiting; might have been a stale approval. Try restarting.
            Log.w(TAG, "resumeAfterApproval: no deferred waiting, restarting loop")
            startLoop(null)
        }
    }

    private fun stopLoop(reason: String) {
        if (!isRunning.getAndSet(false)) {
            stopSelf()
            return
        }
        Log.i(TAG, "Stopping loop: reason=$reason")

        loopJob?.cancel()
        loopJob = null

        LocalAgentPrefs.setStatus(applicationContext, "Stopped")
        // keep lastError as-is
        emitStatus()

        runCatching {
            notificationManager.notify(NOTIFICATION_ID, buildNotification(content = "Stopped ($reason)"))
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun completeLoop(
        taskId: Long,
        status: String,
        notification: String,
        userMessage: String,
        error: String? = null,
    ) {
        isRunning.set(false)
        loopJob = null
        lastError.set(error)
        LocalAgentPrefs.setStatus(applicationContext, status)
        if (error.isNullOrBlank()) {
            LocalAgentPrefs.clearLastError(applicationContext)
        } else {
            LocalAgentPrefs.setLastError(applicationContext, error)
        }
        emitStatus(
            taskId = taskId,
            isTerminal = true,
            userMessage = userMessage,
        )
        speakAndWaitBestEffort(userMessage)
        runCatching {
            notificationManager.notify(NOTIFICATION_ID, buildNotification(content = notification))
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun completionSpeech(actions: List<LocalAgentAction>): String {
        val finishMessage = actions.filterIsInstance<LocalAgentAction.Finish>()
            .lastOrNull()
            ?.message
            .orEmpty()
            .lowercase()
        val failed = finishMessage.contains("fail") ||
            finishMessage.contains("couldn't") ||
            finishMessage.contains("cannot") ||
            finishMessage.contains("blocked") ||
            finishMessage.contains("stopped")
        return if (failed) "I couldn't finish that task." else "Done."
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Local agent",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows when the local agent loop is running"
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun emitStatus(
        taskId: Long? = null,
        isTerminal: Boolean = false,
        userMessage: String? = null,
    ) {
        val status = LocalAgentPrefs.getStatus(applicationContext)
        val err = LocalAgentPrefs.getLastError(applicationContext)

        val intent = Intent(LocalAgentIntents.ACTION_STATUS_CHANGED)
            .putExtra(LocalAgentIntents.EXTRA_STATUS, status)
            .putExtra(LocalAgentIntents.EXTRA_LAST_ERROR, err)
            .putExtra(LocalAgentIntents.EXTRA_IS_TERMINAL, isTerminal)
        taskId?.let { intent.putExtra(LocalAgentIntents.EXTRA_TASK_ID, it) }
        userMessage?.trim()?.takeIf { it.isNotBlank() }?.let {
            intent.putExtra(LocalAgentIntents.EXTRA_USER_MESSAGE, it)
        }

        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
    }

    private fun runDemo() {
        // Demo behavior:
        // - wait 5 seconds to let the user change screens
        // - snapshot screen text
        // - store it in Local Agent memory
        // - read it back via TTS (Bluetooth headset/glasses will receive audio if routed)

        if (!LocalAgentAccessibilityBridge.isConnected()) {
            val err = "accessibility_not_connected"
            LocalAgentPrefs.setStatus(applicationContext, "Demo: failed")
            LocalAgentPrefs.setLastError(applicationContext, err)
            emitStatus()
            return
        }

        LocalAgentPrefs.setStatus(applicationContext, "Demo: reading in 5s…")
        LocalAgentPrefs.clearLastError(applicationContext)
        emitStatus()

        serviceScope.launch {
            delay(5_000)

            val text = LocalAgentAccessibilityBridge.snapshotScreenText() ?: ""
            if (text.isBlank()) {
                LocalAgentPrefs.setStatus(applicationContext, "Demo: no text found")
                LocalAgentPrefs.setLastError(applicationContext, "empty_screen_text")
                emitStatus()
                speakBestEffort("I couldn't read any text on the screen.")
                return@launch
            }

            // Store snapshot (package name isn't directly available here; mark as unknown for demo).
            LocalAgentMemoryStore.appendScreenCapture(
                context = applicationContext,
                packageName = "(demo)",
                text = text,
            )

            val toSpeak = text
                .lineSequence()
                .filter { it.isNotBlank() }
                .take(14)
                .joinToString(". ")
                .take(550)

            speakBestEffort("Reading your screen. $toSpeak")

            LocalAgentPrefs.setStatus(applicationContext, "Demo: spoke (${toSpeak.length} chars)")
            LocalAgentPrefs.clearLastError(applicationContext)
            emitStatus()
        }
    }

    private fun initTts() {
        val ready = CompletableDeferred<Boolean>()
        ttsReady = ready

        tts = TextToSpeech(applicationContext) { status ->
            val ok = status == TextToSpeech.SUCCESS
            if (ok) {
                runCatching { tts?.language = Locale.US }
            }
            if (!ready.isCompleted) ready.complete(ok)
        }
    }

    private suspend fun speakBestEffort(text: String) {
        val ready = ttsReady
        val ok = if (ready != null) {
            withTimeoutOrNull(3_000) { ready.await() } ?: false
        } else false

        if (!ok) {
            Log.w(TAG, "TTS not ready; skipping speak")
            return
        }

        runCatching {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "local_agent_demo")
        }.onFailure {
            Log.w(TAG, "TTS speak failed: ${it.message}")
        }
    }

    private suspend fun speakAndWaitBestEffort(text: String) {
        val clean = text.trim().take(160)
        if (clean.isBlank()) return

        val ready = ttsReady
        val ok = if (ready != null) {
            withTimeoutOrNull(3_000) { ready.await() } ?: false
        } else {
            false
        }
        if (!ok) return

        val utteranceId = "local_agent_result_${System.currentTimeMillis()}"
        val completed = CompletableDeferred<Unit>()
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(doneId: String?) {
                if (doneId == utteranceId && !completed.isCompleted) completed.complete(Unit)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(errorId: String?) {
                if (errorId == utteranceId && !completed.isCompleted) completed.complete(Unit)
            }

            override fun onError(errorId: String?, errorCode: Int) {
                if (errorId == utteranceId && !completed.isCompleted) completed.complete(Unit)
            }
        })

        val queued = runCatching {
            tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, utteranceId) == TextToSpeech.SUCCESS
        }.getOrDefault(false)
        if (!queued) return

        withTimeoutOrNull(8_000) { completed.await() }
    }

    private fun buildNotification(content: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val openPi = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, LocalAgentService::class.java).apply { action = LocalAgentIntents.ACTION_STOP }
        val stopPi = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Local agent")
            .setContentText(content)
            .setContentIntent(openPi)
            .setOngoing(true)
            .addAction(
                NotificationCompat.Action.Builder(
                    0,
                    "Stop",
                    stopPi
                ).build()
            )
            .build()
    }

    companion object {
        private const val TAG = "LocalAgentService"

        // Legacy action names (kept to avoid breaking any old entrypoints).
        private const val ACTION_START_LEGACY = "com.fersaiyan.cyanbridge.action.LOCAL_AGENT_START"
        private const val ACTION_STOP_LEGACY = "com.fersaiyan.cyanbridge.action.LOCAL_AGENT_STOP"

        private const val NOTIFICATION_CHANNEL_ID = "local_agent"
        private const val NOTIFICATION_ID = 937
        private const val DEFAULT_GOAL = "Inspect the current screen and avoid risky actions. If there is no explicit task, finish quickly rather than guessing."

        private val isRunning = AtomicBoolean(false)
        private val lastError = AtomicReference<String?>(null)

        private val brainRef: AtomicReference<LocalAgentBrain> = AtomicReference(RemoteUiControlLocalAgentBrain())

        fun setBrain(brain: LocalAgentBrain) {
            brainRef.set(brain)
        }

        fun getLastError(): String? = lastError.get()

        fun start(context: Context) {
            start(context, goal = null)
        }

        fun start(context: Context, goal: String?) {
            val intent = Intent(context, LocalAgentService::class.java).apply { action = LocalAgentIntents.ACTION_START }
            goal?.trim()?.takeIf { it.isNotBlank() }?.let {
                intent.putExtra(LocalAgentIntents.EXTRA_GOAL, it)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, LocalAgentService::class.java).apply { action = LocalAgentIntents.ACTION_STOP }
            context.startService(intent)
        }
    }
}

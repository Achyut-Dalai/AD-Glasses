package com.fersaiyan.cyanbridge.localagent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.fersaiyan.cyanbridge.MainActivity
import com.fersaiyan.cyanbridge.R
import com.fersaiyan.cyanbridge.agent.LocalAgentPrefs as AutomationPrefs
import com.fersaiyan.cyanbridge.localagent.memory.LocalAgentMemoryStore
import com.fersaiyan.cyanbridge.ui.hasNotificationPermission
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
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
    private val screenReadInProgress = AtomicBoolean(false)

    /** Signalled when the user approves a pending action; the loop awaits this. */
    private var approvalDeferred: CompletableDeferred<Boolean>? = null

    /** Stored task state so the loop can resume after approval. */
    private var pausedTaskState: LocalAgentTaskState? = null

    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private val deviceStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                stopForUnavailableDevice()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
        initTts()
        LocalAgentMemoryStore.ensureSeedFiles(applicationContext)
        ContextCompat.registerReceiver(
            this,
            deviceStateReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            // New contract (UI -> Service)
            LocalAgentIntents.ACTION_START -> startLoop(intent.getStringExtra(LocalAgentIntents.EXTRA_GOAL))
            LocalAgentIntents.ACTION_STOP -> stopLoop(reason = "user")
            LocalAgentIntents.ACTION_DEMO -> runDemo()
            LocalAgentIntents.ACTION_READ_SCREEN_ALOUD -> readCurrentScreenAloud()
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
        runCatching { unregisterReceiver(deviceStateReceiver) }
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

        if (!hasNotificationPermission(this)) {
            LocalAgentPrefs.setStatus(applicationContext, "Waiting for notification permission")
            LocalAgentPrefs.setLastError(applicationContext, "missing_post_notifications")
            emitStatus()
            stopSelf()
            return
        }

        LocalAgentDeviceState.availability(applicationContext)
            .takeIf { it != LocalAgentDeviceState.Availability.READY }
            ?.let { availability ->
                LocalAgentPrefs.setStatus(applicationContext, "Unavailable: ${availability.statusText}")
                LocalAgentPrefs.setLastError(applicationContext, availability.errorCode)
                emitStatus()
                stopSelf()
                return
            }

        if (runningState.getAndSet(true)) {
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
                    return if (action == LocalAgentAction.ReadScreenAloud) {
                        readCurrentScreenAloud()
                    } else {
                        LocalAgentAccessibilityBridge.performWithOptionalShizukuFallback(
                            applicationContext,
                            action,
                        )
                    }
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
            var pendingSavedSkill = LocalAgentSkillStore.findExact(applicationContext, taskState.goal)
            var usedSavedSkill = false
            val executedActions = mutableListOf<LocalAgentAction>()

            while (isActive && taskState.stepIndex <= taskState.maxSteps) {
                var settleAction: LocalAgentAction? = null
                try {
                    LocalAgentDeviceState.availability(applicationContext)
                        .takeIf { it != LocalAgentDeviceState.Availability.READY }
                        ?.let { availability ->
                            stopTaskForUnavailableDevice(
                                taskState = taskState,
                                usedSavedSkill = usedSavedSkill,
                                executedActions = executedActions,
                                availability = availability,
                            )
                            return@launch
                        }

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
                    val replayingSavedSkill = pendingSavedSkill != null
                    val out = pendingSavedSkill?.let { skill ->
                        pendingSavedSkill = null
                        usedSavedSkill = true
                        LocalAgentBrainOutput(
                            actions = skill.actions,
                            note = "Replaying a saved low-risk navigation skill.",
                        )
                    } ?: brain.next(applicationContext, taskState, obs)

                    LocalAgentDeviceState.availability(applicationContext)
                        .takeIf { it != LocalAgentDeviceState.Availability.READY }
                        ?.let { availability ->
                            stopTaskForUnavailableDevice(
                                taskState = taskState,
                                usedSavedSkill = usedSavedSkill,
                                executedActions = executedActions,
                                availability = availability,
                            )
                            return@launch
                        }

                    val actions = out.actions
                    if (actions.isNotEmpty()) {
                        Log.i(TAG, "Planned ${actions.size} actions. note=${out.note}")
                    }

                    val plannedAction = actions.singleOrNull()
                    settleAction = plannedAction
                    if (plannedAction != null && taskState.hasReachedRepeatLimit(plannedAction)) {
                        val reason = LocalAgentRuntimePolicy.repeatLimitMessage(plannedAction)
                        Log.w(TAG, reason)
                        taskState = taskState.nextStep(
                            previousActionResult = reason,
                            failed = true,
                        )
                        delay(LocalAgentRuntimePolicy.settleDelayMs(plannedAction))
                        continue
                    }

                    val summary = engine.execute(actions)
                    if (summary.haltedForDeviceState) {
                        stopTaskForUnavailableDevice(
                            taskState = taskState,
                            usedSavedSkill = usedSavedSkill,
                            executedActions = executedActions,
                            availability = summary.deviceAvailability
                                ?: LocalAgentDeviceState.availability(applicationContext),
                        )
                        return@launch
                    }
                    val actionFailed = summary.actionResults.any { it.endsWith("failed") }
                    if (!summary.haltedForApproval && !actionFailed) {
                        executedActions += actions.filterNot { it is LocalAgentAction.Finish }
                    }
                    if (replayingSavedSkill && actionFailed) {
                        LocalAgentSkillStore.recordReplayFailure(applicationContext, taskState.goal)
                    }
                    val previousResult = buildString {
                        out.note?.takeIf { it.isNotBlank() }?.let {
                            append(it)
                        }
                        if (summary.actionResults.isNotEmpty()) {
                            if (isNotBlank()) append(" | ")
                            append(summary.actionResults.joinToString("; "))
                        }
                    }.ifBlank { out.note ?: "" }

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

                        var approved: Boolean? = null
                        var unavailableDevice: LocalAgentDeviceState.Availability? = null
                        while (isActive && approved == null) {
                            val availability = LocalAgentDeviceState.availability(applicationContext)
                            if (availability != LocalAgentDeviceState.Availability.READY) {
                                unavailableDevice = availability
                                break
                            }
                            approved = withTimeoutOrNull(500L) { deferred.await() }
                        }
                        approvalDeferred = null

                        unavailableDevice?.let { availability ->
                            deferred.complete(false)
                            stopTaskForUnavailableDevice(
                                taskState = taskState,
                                usedSavedSkill = usedSavedSkill,
                                executedActions = executedActions,
                                availability = availability,
                            )
                            return@launch
                        }

                        if (approved != true) {
                            // User rejected or service was destroyed.
                            recordTaskOutcome(
                                taskState = taskState,
                                status = "Stopped",
                                usedSavedSkill = usedSavedSkill,
                                executedActions = executedActions,
                            )
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

                        plannedAction?.takeUnless { it is LocalAgentAction.Finish }?.let(executedActions::add)

                        taskState = taskState.nextStep(
                            previousActionResult = "Action approved and executed by user",
                            failed = false,
                            action = plannedAction,
                        )
                        delay(LocalAgentRuntimePolicy.settleDelayMs(plannedAction))
                        continue
                    }

                    if (out.isComplete || summary.finished) {
                        recordTaskOutcome(
                            taskState = taskState,
                            status = "Completed",
                            usedSavedSkill = usedSavedSkill,
                            executedActions = executedActions,
                        )
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

                    taskState = taskState.nextStep(
                        previousActionResult = previousResult,
                        failed = actionFailed,
                        action = plannedAction,
                    )
                } catch (e: CancellationException) {
                    throw e
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

                // Let the destination app render before observing its next state.
                delay(LocalAgentRuntimePolicy.settleDelayMs(settleAction))
            }

            if (isActive) {
                recordTaskOutcome(
                    taskState = taskState,
                    status = "Stopped",
                    usedSavedSkill = usedSavedSkill,
                    executedActions = executedActions,
                )
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
        if (!LocalAgentDeviceState.isReady(applicationContext)) {
            approvalDeferred?.complete(false)
            stopForUnavailableDevice()
            return
        }
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

    private fun stopLoop(
        reason: String,
        status: String = "Stopped",
        error: String? = null,
    ) {
        if (!runningState.getAndSet(false)) {
            if (error != null) {
                lastError.set(error)
                LocalAgentPrefs.setStatus(applicationContext, status)
                LocalAgentPrefs.setLastError(applicationContext, error)
                emitStatus()
            }
            stopSelf()
            return
        }
        Log.i(TAG, "Stopping loop: reason=$reason")

        loopJob?.cancel()
        loopJob = null

        LocalAgentPrefs.setStatus(applicationContext, status)
        if (error != null) {
            lastError.set(error)
            LocalAgentPrefs.setLastError(applicationContext, error)
        }
        // Keep the previous error for ordinary user stops.
        emitStatus()

        runCatching {
            notificationManager.notify(NOTIFICATION_ID, buildNotification(content = status))
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun recordTaskOutcome(
        taskState: LocalAgentTaskState,
        status: String,
        usedSavedSkill: Boolean,
        executedActions: List<LocalAgentAction>,
    ) {
        LocalAgentTaskHistory.record(
            applicationContext,
            LocalAgentTaskHistory.Entry(
                goal = taskState.goal,
                status = status,
                stepCount = taskState.stepIndex,
                usedSavedSkill = usedSavedSkill,
            ),
        )
        if (status == "Completed") {
            LocalAgentSkillStore.recordSuccessful(
                context = applicationContext,
                goal = taskState.goal,
                actions = executedActions,
            )
        }
    }

    private suspend fun stopTaskForUnavailableDevice(
        taskState: LocalAgentTaskState,
        usedSavedSkill: Boolean,
        executedActions: List<LocalAgentAction>,
        availability: LocalAgentDeviceState.Availability,
    ) {
        val blocked = availability.takeIf { it != LocalAgentDeviceState.Availability.READY }
            ?: LocalAgentDeviceState.Availability.UNAVAILABLE
        pausedTaskState = null
        approvalDeferred?.complete(false)
        approvalDeferred = null
        recordTaskOutcome(
            taskState = taskState,
            status = "Stopped",
            usedSavedSkill = usedSavedSkill,
            executedActions = executedActions,
        )
        completeLoop(
            taskId = taskState.startedAtMs,
            status = "Stopped: ${blocked.statusText}",
            notification = "Stopped: ${blocked.statusText}",
            userMessage = "",
            error = blocked.errorCode,
            speakResult = false,
        )
    }

    private fun stopForUnavailableDevice() {
        val availability = LocalAgentDeviceState.availability(applicationContext)
        if (availability == LocalAgentDeviceState.Availability.READY) return
        if (!runningState.get() && !screenReadInProgress.get()) return

        pausedTaskState = null
        approvalDeferred?.complete(false)
        approvalDeferred = null
        screenReadInProgress.set(false)
        runCatching { tts?.stop() }
        stopLoop(
            reason = availability.errorCode,
            status = "Stopped: ${availability.statusText}",
            error = availability.errorCode,
        )
    }

    private suspend fun completeLoop(
        taskId: Long,
        status: String,
        notification: String,
        userMessage: String,
        error: String? = null,
        speakResult: Boolean = true,
    ) {
        runningState.set(false)
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
        if (speakResult) speakAndWaitBestEffort(userMessage)
        runCatching {
            notificationManager.notify(NOTIFICATION_ID, buildNotification(content = notification))
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun completionSpeech(actions: List<LocalAgentAction>): String {
        if (screenReadInProgress.get() || actions.any { it == LocalAgentAction.ReadScreenAloud }) return ""
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

        LocalAgentDeviceState.availability(applicationContext)
            .takeIf { it != LocalAgentDeviceState.Availability.READY }
            ?.let { availability ->
                LocalAgentPrefs.setStatus(applicationContext, "Demo: unavailable")
                LocalAgentPrefs.setLastError(applicationContext, availability.errorCode)
                emitStatus()
                return
            }

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

            LocalAgentDeviceState.availability(applicationContext)
                .takeIf { it != LocalAgentDeviceState.Availability.READY }
                ?.let { availability ->
                    LocalAgentPrefs.setStatus(applicationContext, "Demo: stopped")
                    LocalAgentPrefs.setLastError(applicationContext, availability.errorCode)
                    emitStatus()
                    return@launch
                }

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

    /**
     * Reads only currently visible accessibility text. This is deliberately an explicit,
     * approval-gated action rather than a background notification or message reader.
     */
    private fun readCurrentScreenAloud(): Boolean {
        LocalAgentDeviceState.availability(applicationContext)
            .takeIf { it != LocalAgentDeviceState.Availability.READY }
            ?.let { availability ->
                LocalAgentPrefs.setStatus(applicationContext, "Screen reading unavailable: ${availability.statusText}")
                LocalAgentPrefs.setLastError(applicationContext, availability.errorCode)
                emitStatus()
                if (!runningState.get()) stopSelf()
                return false
            }
        if (!hasNotificationPermission(this)) {
            LocalAgentPrefs.setLastError(applicationContext, "missing_post_notifications")
            emitStatus()
            return false
        }
        if (!LocalAgentAccessibilityBridge.isConnected()) {
            LocalAgentPrefs.setLastError(applicationContext, "accessibility_not_connected")
            emitStatus()
            return false
        }
        val activePackage = LocalAgentAccessibilityBridge.activeWindowPackageName()
        if (activePackage.isNullOrBlank()) {
            LocalAgentPrefs.setStatus(applicationContext, "Unable to verify current app")
            LocalAgentPrefs.setLastError(applicationContext, "screen_package_unknown")
            emitStatus()
            return false
        }
        LocalAgentSafetyPolicy.blockedReason(applicationContext, activePackage)?.let {
            LocalAgentPrefs.setStatus(applicationContext, "Screen reading blocked by privacy settings")
            LocalAgentPrefs.setLastError(applicationContext, "privacy_blacklisted_app")
            emitStatus()
            return false
        }

        val standaloneRequest = !runningState.get()
        if (standaloneRequest) {
            LocalAgentPrefs.setStatus(applicationContext, "Reading current screen")
            LocalAgentPrefs.clearLastError(applicationContext)
            startForeground(NOTIFICATION_ID, buildNotification(content = "Reading current screen"))
            emitStatus()
        }

        val visibleText = LocalAgentAccessibilityBridge.snapshotScreenText()
            ?.lineSequence()
            ?.map(String::trim)
            ?.filter(String::isNotBlank)
            ?.take(20)
            ?.joinToString(". ")
            ?.take(MAX_SCREEN_READ_ALOUD_CHARS)
            .orEmpty()
        if (visibleText.isBlank()) {
            LocalAgentPrefs.setLastError(applicationContext, "empty_screen_text")
            emitStatus()
            screenReadInProgress.set(true)
            serviceScope.launch {
                try {
                    speakBestEffort("I couldn't read any text on the screen.")
                } finally {
                    screenReadInProgress.set(false)
                    if (standaloneRequest) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
            return false
        }

        screenReadInProgress.set(true)
        serviceScope.launch {
            try {
                LocalAgentDeviceState.availability(applicationContext)
                    .takeIf { it != LocalAgentDeviceState.Availability.READY }
                    ?.let { availability ->
                        LocalAgentPrefs.setStatus(
                            applicationContext,
                            "Screen reading stopped: ${availability.statusText}",
                        )
                        LocalAgentPrefs.setLastError(applicationContext, availability.errorCode)
                        emitStatus()
                        return@launch
                    }
                speakAndWaitBestEffort("Reading the visible screen. $visibleText")
            } finally {
                screenReadInProgress.set(false)
                if (standaloneRequest) {
                    LocalAgentPrefs.setStatus(applicationContext, "Read current screen")
                    LocalAgentPrefs.clearLastError(applicationContext)
                    emitStatus()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
        return true
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
        if (!LocalAgentDeviceState.isReady(applicationContext)) return
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
        if (!LocalAgentDeviceState.isReady(applicationContext)) return

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

        repeat(32) {
            if (!LocalAgentDeviceState.isReady(applicationContext)) {
                runCatching { tts?.stop() }
                return
            }
            if (withTimeoutOrNull(250L) { completed.await() } != null) return
        }
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
        private const val MAX_SCREEN_READ_ALOUD_CHARS = 750
        private const val DEFAULT_GOAL = "Inspect the current screen and avoid risky actions. If there is no explicit task, finish quickly rather than guessing."

        private val runningState = AtomicBoolean(false)
        private val lastError = AtomicReference<String?>(null)

        private val brainRef: AtomicReference<LocalAgentBrain> = AtomicReference(RemoteUiControlLocalAgentBrain())

        fun setBrain(brain: LocalAgentBrain) {
            brainRef.set(brain)
        }

        fun getLastError(): String? = lastError.get()

        fun isRunning(): Boolean = runningState.get()

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

        fun readScreenAloud(context: Context) {
            val intent = Intent(context, LocalAgentService::class.java).apply {
                action = LocalAgentIntents.ACTION_READ_SCREEN_ALOUD
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}

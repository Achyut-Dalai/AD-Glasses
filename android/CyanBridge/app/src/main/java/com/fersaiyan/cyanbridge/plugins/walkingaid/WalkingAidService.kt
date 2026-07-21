package com.fersaiyan.cyanbridge.plugins.walkingaid

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.widget.Toast
import androidx.core.app.ServiceCompat
import com.fersaiyan.cyanbridge.ai.vision.VisionProfile
import com.fersaiyan.cyanbridge.ai.vision.VisionProfilePreferences
import com.fersaiyan.cyanbridge.ai.vision.VisionPromptBuilder
import com.fersaiyan.cyanbridge.glasses.GlassesSessionCoordinator
import com.fersaiyan.cyanbridge.media.autocapture.AutoAudioCapturePrefs
import com.fersaiyan.cyanbridge.media.autocapture.AutoAudioCaptureService
import com.fersaiyan.cyanbridge.audio.MeetingCapturePrefs
import com.fersaiyan.cyanbridge.localmodels.provider.LocalModelRequestPriority
import com.fersaiyan.cyanbridge.localmodels.provider.LocalModelsProvider
import com.fersaiyan.cyanbridge.ai.router.CliRelayClient
import com.fersaiyan.cyanbridge.devices.DeviceProfileStore
import com.fersaiyan.cyanbridge.devices.metarayban.MetaRaybanManager
import com.oudmon.ble.base.bluetooth.BleOperateManager
import com.oudmon.ble.base.communication.LargeDataHandler
import com.hjq.permissions.XXPermissions
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class WalkingAidService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loopJob: Job? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var safetyDisclaimerSpoken = false
    private var wasAutoAudioEnabled = false
    private val localModelsProvider = LocalModelsProvider()

    override fun onCreate() {
        super.onCreate()
        WalkingAidNotificationHelper.ensureChannel(this)
        WalkingAidImageStore.load(this)
        initTts()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_START -> startLoop()
            ACTION_STOP -> stopLoop(reason = "user")
            null -> {
                if (WalkingAidPreferences.isEnabled(this)) startLoop() else stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopLoop(reason = "destroy")
        tts?.stop()
        tts?.shutdown()
        tts = null
        scope.cancel()
        super.onDestroy()
    }

    private fun initTts() {
        tts = TextToSpeech(this) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
        }
    }

    private fun canPostNotifications(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            XXPermissions.isGranted(this, Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun startForegroundSafely(content: String): Boolean {
        if (!canPostNotifications()) {
            Log.w(TAG, "Missing POST_NOTIFICATIONS; cannot start walking aid foreground service")
            return false
        }
        return runCatching {
            val notif = WalkingAidNotificationHelper.buildNotification(
                this, content, WalkingAidPreferences.getCaptureIntervalSeconds(this)
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    WalkingAidNotificationHelper.NOTIFICATION_ID,
                    notif,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                )
            } else {
                startForeground(WalkingAidNotificationHelper.NOTIFICATION_ID, notif)
            }
        }.onFailure {
            Log.e(TAG, "startForeground failed: ${it.message}")
        }.isSuccess
    }

    private fun startLoop() {
        if (RUNNING.getAndSet(true)) {
            Log.i(TAG, "Already running")
            return
        }
        val isMetaRayban = isMetaRaybanSelected()

        // Check POST_NOTIFICATIONS permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !XXPermissions.isGranted(this, Manifest.permission.POST_NOTIFICATIONS)
        ) {
            Log.w(TAG, "Missing POST_NOTIFICATIONS permission")
            RUNNING.set(false)
            stopSelf()
            return
        }

        if (!startForegroundSafely("Walking Aid active — starting...")) {
            RUNNING.set(false)
            stopSelf()
            return
        }

        // HeyCyan uses the vendor BLE transport; Meta uses DAT instead.
        if (!isMetaRayban && !BleOperateManager.getInstance().isConnected) {
            Log.w(TAG, "Glasses not connected")
            showToast(this, getString(com.fersaiyan.cyanbridge.R.string.walking_aid_not_connected))
            RUNNING.set(false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        if (isMetaRayban) {
            val metaManager = MetaRaybanManager.getInstance(this)
            if (!metaManager.isInitialized.value) metaManager.initialize()
        }

        // Check meeting capture
        if (MeetingCapturePrefs.getState(this).isRecording) {
            Log.w(TAG, "Meeting capture is active")
            showToast(this, getString(com.fersaiyan.cyanbridge.R.string.walking_aid_meeting_active))
            RUNNING.set(false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        // Pause auto audio capture
        wasAutoAudioEnabled = AutoAudioCapturePrefs.isEnabled(this)
        if (wasAutoAudioEnabled) {
            AutoAudioCapturePrefs.setEnabled(this, false)
            val pauseIntent = Intent(this, AutoAudioCaptureService::class.java)
                .setAction(AutoAudioCaptureService.ACTION_STOP)
            startService(pauseIntent)
        }

        safetyDisclaimerSpoken = false

        loopJob = scope.launch {
            var captureIndex = 0
            if (isMetaRayban) {
                val metaManager = MetaRaybanManager.getInstance(this@WalkingAidService)
                if (!metaManager.awaitCameraReady()) {
                    val detail = metaManager.lastError.value
                        ?: "Register and connect a Meta camera before using Walking Aid"
                    Log.e(
                        TAG,
                        "Meta Walking Aid cannot start: $detail\n${metaManager.diagnosticsSnapshot()}",
                    )
                    WalkingAidNotificationHelper.updateNotification(
                        this@WalkingAidService,
                        "Meta camera unavailable: ${detail.take(120)}",
                        0,
                    )
                    WalkingAidPreferences.setEnabled(this@WalkingAidService, false)
                    stopLoop(reason = "meta_camera_unavailable")
                    return@launch
                }
            }
            while (isActive && WalkingAidPreferences.isEnabled(this@WalkingAidService)) {
                val intervalMs = WalkingAidPreferences.getCaptureIntervalSeconds(this@WalkingAidService) * 1000L

                if (!isMetaRayban && !BleOperateManager.getInstance().isConnected) {
                    Log.w(TAG, "Glasses disconnected during loop; waiting...")
                    WalkingAidNotificationHelper.updateNotification(
                        this@WalkingAidService,
                        "Waiting for glasses connection...",
                        (intervalMs / 1000).toInt(),
                    )
                    delay(5_000)
                    continue
                }

                // 1. Capture thumbnail
                val image = captureThumbnail(captureIndex)
                if (image == null) {
                    Log.w(TAG, "No thumbnail captured, retrying after interval")
                    WalkingAidNotificationHelper.updateNotification(
                        this@WalkingAidService,
                        "No image captured, retrying...",
                        (intervalMs / 1000).toInt(),
                    )
                    delay(intervalMs)
                    continue
                }

                // 2. Parallel: image description + depth estimation
                val descriptionDeferred = async {
                    describeImage(image)
                }
                val depthDeferred = async {
                    if (WalkingAidPreferences.isDepthEnabled(this@WalkingAidService)) {
                        estimateDepth(image)
                    } else null
                }

                val description = descriptionDeferred.await()
                val depth = depthDeferred.await()

                if (description.isNullOrBlank()) {
                    Log.w(TAG, "Empty description, skipping")
                    delay(intervalMs)
                    captureIndex++
                    continue
                }

                // 3. State model decision (text-only: uses descriptions, not raw images)
                val recentContext = WalkingAidImageStore.getRecentDescriptions(5)
                val stateDecision = runStateModelTextOnly(description, depth, recentContext)

                // 4. Store
                val record = SceneRecord(
                    timestampMs = System.currentTimeMillis(),
                    imagePath = image.absolutePath,
                    description = description,
                    depthDescription = depth,
                    stateDecision = stateDecision.decision,
                )
                val maxHistory = WalkingAidPreferences.getImageHistoryMaxCount(this@WalkingAidService)
                WalkingAidImageStore.addRecord(record, maxHistory)
                WalkingAidImageStore.persist(this@WalkingAidService, maxHistory)

                // 5. Output
                if (stateDecision.decision == StateDecision.WARN ||
                    stateDecision.decision == StateDecision.DESCRIBE
                ) {
                    val disclaimer = if (
                        WalkingAidPreferences.isSafetyDisclaimerEnabled(this@WalkingAidService) &&
                        !safetyDisclaimerSpoken
                    ) {
                        safetyDisclaimerSpoken = true
                        "Safety notice: this system does not guarantee path safety. Always use caution. "
                    } else ""

                    val fullMessage = disclaimer + stateDecision.message
                    if (WalkingAidPreferences.isTtsEnabled(this@WalkingAidService)) {
                        speakTts(fullMessage)
                    }
                }

                // 6. Update notification
                val truncatedDesc = description.take(50)
                WalkingAidNotificationHelper.updateNotification(
                    this@WalkingAidService,
                    "Last: $truncatedDesc...",
                    (intervalMs / 1000).toInt(),
                )

                // 7. Delay
                captureIndex++
                delay(intervalMs)
            }

            stopLoop(reason = "loop_end")
        }
    }

    private suspend fun captureThumbnail(index: Int): File? {
        if (isMetaRaybanSelected()) {
            val manager = MetaRaybanManager.getInstance(this)
            if (!manager.isInitialized.value) manager.initialize()
            return runCatching {
                val photo = manager.capturePhotoOnce()
                manager.savePhotoForProcessing(photo, "META_WALKING_AID_$index")
            }.onFailure {
                val detail = manager.lastError.value ?: it.message ?: "camera unavailable"
                Log.e(TAG, "Meta DAT photo capture failed: $detail\n${manager.diagnosticsSnapshot()}", it)
                WalkingAidNotificationHelper.updateNotification(
                    this,
                    "Meta capture failed: ${detail.take(120)}",
                    0,
                )
            }.getOrNull()
        }

        val permit = GlassesSessionCoordinator.tryAcquireBackgroundCommand()
        if (permit == null) {
            Log.i(TAG, "Skipping thumbnail capture: glasses SDK busy")
            return null
        }
        var thumbnailTransferStarted = false
        try {
            val outDir = getExternalFilesDir("DCIM") ?: filesDir
            val file = File(outDir, "WALKING_AID_THUMB_${index}_${System.currentTimeMillis()}.jpg")
            runCatching {
                file.parentFile?.mkdirs()
                if (file.exists()) file.delete()
            }

            val completed = AtomicBoolean(false)
            val done = CompletableDeferred<File?>()

            val thumbCallback: (Int, Boolean, ByteArray?) -> Unit = { _, isComplete, data ->
                if (data != null && data.isNotEmpty()) {
                    runCatching {
                        FileOutputStream(file, true).use { out -> out.write(data) }
                    }.onFailure {
                        Log.e(TAG, "Failed writing thumbnail chunk: ${it.message}", it)
                    }
                }
                if (isComplete && completed.compareAndSet(false, true)) {
                    if (!done.isCompleted) {
                        done.complete(if (file.exists() && file.length() >= 1024L) file else null)
                    }
                    GlassesSessionCoordinator.releaseBackgroundCommand(permit)
                }
            }

            runCatching {
                LargeDataHandler.getInstance().glassesControl(byteArrayOf(0x02, 0x01, 0x06, 0x02, 0x02)) { _, _ -> }
            }
            delay(250)
            runCatching {
                LargeDataHandler.getInstance().glassesControl(byteArrayOf(0x02, 0x01, 0x01)) { _, _ -> }
            }
            delay(2500)

            LargeDataHandler.getInstance().getPictureThumbnails(thumbCallback)
            thumbnailTransferStarted = true

            val result = withTimeoutOrNull(14_000) { done.await() }
            if (result == null) {
                Log.w(TAG, "Thumbnail transfer timed out")
            }
            return result
        } catch (e: Exception) {
            if (!thumbnailTransferStarted) {
                GlassesSessionCoordinator.releaseBackgroundCommand(permit)
            }
            throw e
        }
    }

    private suspend fun describeImage(image: File): String? {
        val source = WalkingAidPreferences.getImageDescriptionSource(this)
        val settings = VisionProfilePreferences.get(this)
        val basePrompt = VisionPromptBuilder.build(
            settings = settings.copy(profile = VisionProfile.WALKING),
            userQuestion = null,
        )
        val customPrompt = WalkingAidPreferences.getCustomPrompt(this)
        val prompt = if (customPrompt.isNotBlank()) {
            "$basePrompt\nAdditional walking aid instructions: $customPrompt"
        } else {
            basePrompt
        }

        return if (source == "cloud") {
            val modelOverride = WalkingAidPreferences.getImageDescriptionModelOverride(this)
            val result = CliRelayClient.imageQuery(
                context = this,
                imagePath = image.absolutePath,
                prompt = prompt,
                modelOverride = modelOverride,
            )
            result.getOrNull()?.trim()?.takeIf { it.isNotBlank() }
        } else {
            // Local
            try {
                val raw = localModelsProvider.streamChat(
                    context = this,
                    messages = listOf(
                        mapOf("role" to "User", "content" to prompt)
                    ),
                    imagePaths = listOf(image.absolutePath),
                    requestPriority = LocalModelRequestPriority.HIGH,
                ).trim()
                if (raw.isBlank()) null else raw
            } catch (e: Exception) {
                Log.e(TAG, "Local describe failed: ${e.message}", e)
                null
            }
        }
    }

    private fun isMetaRaybanSelected(): Boolean = DeviceProfileStore.isMetaSelected(this)

    private suspend fun estimateDepth(image: File): String? {
        val customPrompt = WalkingAidPreferences.getCustomPrompt(this)
        val depthPrompt = buildString {
            append("Analyze this image for depth and distance. List the nearest obstacles or objects with approximate distance bands: immediate (under 1m), near (1-3m), medium (3-10m), far (over 10m). Be concise. Focus on ground-level hazards, obstacles in the walking path, and changes in elevation.")
            if (customPrompt.isNotBlank()) {
                append("\nAdditional walking aid instructions: ")
                append(customPrompt)
            }
        }
        val source = WalkingAidPreferences.getDepthSource(this)

        return if (source == "cloud") {
            val modelOverride = WalkingAidPreferences.getDepthModelOverride(this)
            val result = CliRelayClient.imageQuery(
                context = this,
                imagePath = image.absolutePath,
                prompt = depthPrompt,
                modelOverride = modelOverride,
            )
            result.getOrNull()?.trim()?.takeIf { it.isNotBlank() }
        } else {
            try {
                val raw = localModelsProvider.streamChat(
                    context = this,
                    messages = listOf(
                        mapOf("role" to "User", "content" to depthPrompt)
                    ),
                    imagePaths = listOf(image.absolutePath),
                    requestPriority = LocalModelRequestPriority.HIGH,
                ).trim()
                if (raw.isBlank()) null else raw
            } catch (e: Exception) {
                Log.e(TAG, "Local depth estimation failed: ${e.message}", e)
                null
            }
        }
    }

    private suspend fun runStateModel(
        description: String,
        depth: String?,
        recentContext: List<SceneRecord>,
    ): StateModelOutput {
        val contextLines = recentContext.mapIndexed { i, r ->
            "${i + 1}. ${r.description}"
        }.joinToString("\n")

        val prompt = buildString {
            appendLine("You are a walking navigation assistant for a blind user. Based on the scene information below, decide what to do.")
            appendLine()
            if (contextLines.isNotBlank()) {
                appendLine("Recent scenes (oldest first):")
                appendLine(contextLines)
                appendLine()
            }
            appendLine("Current scene: $description")
            if (depth != null) {
                appendLine("Depth info: $depth")
            }
            appendLine()
            appendLine("Reply with EXACTLY one line:")
            appendLine("WARN:<brief warning> — for immediate hazards, obstacles, ground changes, moving objects")
            appendLine("DESCRIBE:<brief description> — for notable scene changes, landmarks, text")
            appendLine("SKIP — if nothing noteworthy changed from recent scenes")
            appendLine()
            appendLine("Be extremely concise. Never claim that a path is safe.")
            val customPromptWalkingAid = WalkingAidPreferences.getCustomPrompt(this@WalkingAidService)
            if (customPromptWalkingAid.isNotBlank()) {
                appendLine()
                append("Additional walking aid instructions: $customPromptWalkingAid")
            }
        }

        val source = WalkingAidPreferences.getStateModelSource(this)
        val reply = if (source == "cloud") {
            val modelOverride = WalkingAidPreferences.getImageDescriptionModelOverride(this)
            val result = CliRelayClient.imageQuery(
                context = this,
                imagePath = "", // state model may not need an image, but API requires one
                prompt = prompt,
                modelOverride = modelOverride,
            ).getOrDefault("SKIP")
            result.trim()
        } else {
            try {
                localModelsProvider.streamChat(
                    context = this,
                    messages = listOf(
                        mapOf("role" to "User", "content" to prompt)
                    ),
                    requestPriority = LocalModelRequestPriority.HIGH,
                ).trim()
            } catch (e: Exception) {
                Log.e(TAG, "Local state model failed: ${e.message}", e)
                "SKIP"
            }
        }

        return parseStateDecision(reply)
    }

    /**
     * The state model may not see the image for the state decision (it's expensive).
     * Use a text-only query instead.
     */
    private suspend fun runStateModelTextOnly(
        description: String,
        depth: String?,
        recentContext: List<SceneRecord>,
    ): StateModelOutput {
        val contextLines = recentContext.mapIndexed { i, r ->
            "${i + 1}. ${r.description}"
        }.joinToString("\n")

        val prompt = buildString {
            appendLine("You are a walking navigation assistant for a blind user. Based on the scene information below, decide what to do.")
            appendLine()
            if (contextLines.isNotBlank()) {
                appendLine("Recent scenes (oldest first):")
                appendLine(contextLines)
                appendLine()
            }
            appendLine("Current scene: $description")
            if (depth != null) {
                appendLine("Depth info: $depth")
            }
            appendLine()
            appendLine("Reply with EXACTLY one line:")
            appendLine("WARN:<brief warning> — for immediate hazards, obstacles, ground changes, moving objects")
            appendLine("DESCRIBE:<brief description> — for notable scene changes, landmarks, text")
            appendLine("SKIP — if nothing noteworthy changed from recent scenes")
            appendLine()
            appendLine("Be extremely concise. Never claim that a path is safe.")
            val customPromptWalkingAid = WalkingAidPreferences.getCustomPrompt(this@WalkingAidService)
            if (customPromptWalkingAid.isNotBlank()) {
                appendLine()
                append("Additional walking aid instructions: $customPromptWalkingAid")
            }
        }

        val source = WalkingAidPreferences.getStateModelSource(this)
        val reply = if (source == "cloud") {
            val modelOverride = WalkingAidPreferences.getImageDescriptionModelOverride(this)
            val result = CliRelayClient.voiceQuery(
                context = this,
                prompt = prompt,
                modelOverride = modelOverride,
            ).getOrDefault("SKIP")
            result.trim()
        } else {
            try {
                localModelsProvider.streamChat(
                    context = this,
                    messages = listOf(
                        mapOf("role" to "User", "content" to prompt)
                    ),
                    requestPriority = LocalModelRequestPriority.HIGH,
                ).trim()
            } catch (e: Exception) {
                Log.e(TAG, "Local text state model failed: ${e.message}", e)
                "SKIP"
            }
        }

        return parseStateDecision(reply)
    }

    private fun parseStateDecision(reply: String): StateModelOutput {
        val trimmed = reply.trim()
        if (trimmed.startsWith("WARN:", ignoreCase = true)) {
            val msg = trimmed.removePrefix("WARN:").removePrefix("warn:").trim()
            return StateModelOutput(StateDecision.WARN, msg)
        }
        if (trimmed.startsWith("DESCRIBE:", ignoreCase = true)) {
            val msg = trimmed.removePrefix("DESCRIBE:").removePrefix("describe:").trim()
            return StateModelOutput(StateDecision.DESCRIBE, msg)
        }
        if (trimmed.startsWith("SKIP", ignoreCase = true)) {
            return StateModelOutput(StateDecision.SKIP, "")
        }
        // Fallback: check for any line starting with WARN or DESCRIBE
        for (line in trimmed.lines()) {
            val clean = line.trim()
            if (clean.startsWith("WARN:", ignoreCase = true)) {
                val msg = clean.removePrefix("WARN:").removePrefix("warn:").trim()
                return StateModelOutput(StateDecision.WARN, msg)
            }
            if (clean.startsWith("DESCRIBE:", ignoreCase = true)) {
                val msg = clean.removePrefix("DESCRIBE:").removePrefix("describe:").trim()
                return StateModelOutput(StateDecision.DESCRIBE, msg)
            }
            if (clean.startsWith("SKIP", ignoreCase = true)) {
                return StateModelOutput(StateDecision.SKIP, "")
            }
        }
        // Default: skip
        return StateModelOutput(StateDecision.SKIP, "")
    }

    private fun speakTts(text: String) {
        if (!ttsReady || tts == null) {
            Log.w(TAG, "TTS not ready")
            return
        }
        val settings = VisionProfilePreferences.get(this)
        val locale = Locale.forLanguageTag(settings.responseLanguageTag)
        tts?.language = locale

        val utteranceId = "walking_aid_${System.currentTimeMillis()}"
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(uttId: String?) {}
            override fun onDone(uttId: String?) {}
            override fun onError(uttId: String?) {}
        })
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    private fun stopLoop(reason: String) {
        if (!RUNNING.getAndSet(false)) {
            stopSelf()
            return
        }
        Log.i(TAG, "Stopping: $reason")
        loopJob?.cancel()
        loopJob = null

        // Resume auto audio capture if it was running before
        if (wasAutoAudioEnabled) {
            AutoAudioCapturePrefs.setEnabled(this, true)
            val resumeIntent = Intent(this, AutoAudioCaptureService::class.java)
                .setAction(AutoAudioCaptureService.ACTION_START)
            startService(resumeIntent)
        }

        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }

    private fun showToast(context: Context, message: String) {
        Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val TAG = "WalkingAidService"
        const val ACTION_START = "com.fersaiyan.cyanbridge.action.WALKING_AID_START"
        const val ACTION_STOP = "com.fersaiyan.cyanbridge.action.WALKING_AID_STOP"

        private val RUNNING = AtomicBoolean(false)

        fun start(context: Context) {
            WalkingAidPreferences.setEnabled(context, true)
            val intent = Intent(context, WalkingAidService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            WalkingAidPreferences.setEnabled(context, false)
            val intent = Intent(context, WalkingAidService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }

        fun isRunning(): Boolean = RUNNING.get()
    }
}

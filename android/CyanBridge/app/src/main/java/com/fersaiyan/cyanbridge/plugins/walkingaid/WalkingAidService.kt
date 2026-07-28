package com.fersaiyan.cyanbridge.plugins.walkingaid

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.widget.Toast
import androidx.core.app.ServiceCompat
import androidx.fragment.app.FragmentActivity
import com.fersaiyan.cyanbridge.ai.vision.ImageQuestionPreferences
import com.fersaiyan.cyanbridge.devices.DeviceProfileStore
import com.fersaiyan.cyanbridge.devices.metarayban.MetaRaybanManager
import com.fersaiyan.cyanbridge.glasses.GlassesSessionCoordinator
import com.fersaiyan.cyanbridge.media.autocapture.AutoAudioCapturePrefs
import com.fersaiyan.cyanbridge.media.autocapture.AutoAudioCaptureService
import com.fersaiyan.cyanbridge.audio.MeetingCapturePrefs
import com.fersaiyan.cyanbridge.ai.router.CliRelayClient
import com.fersaiyan.cyanbridge.localmodels.provider.LocalModelRequestPriority
import com.fersaiyan.cyanbridge.localmodels.provider.LocalModelsProvider
import com.fersaiyan.cyanbridge.plugins.walkingaid.vision.DepthResult
import com.fersaiyan.cyanbridge.plugins.walkingaid.vision.DetectedObject
import com.fersaiyan.cyanbridge.plugins.walkingaid.vision.DetectionResult
import com.fersaiyan.cyanbridge.plugins.walkingaid.vision.LiteRtVisionBackend
import com.fersaiyan.cyanbridge.plugins.walkingaid.vision.VisionBackend
import com.fersaiyan.cyanbridge.plugins.walkingaid.vision.VisionFrame
import android.graphics.RectF
import com.fersaiyan.cyanbridge.ui.ensureNotificationPermission
import com.fersaiyan.cyanbridge.ui.hasNotificationPermission
import com.oudmon.ble.base.bluetooth.BleOperateManager
import com.oudmon.ble.base.communication.LargeDataHandler
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

import com.fersaiyan.cyanbridge.devices.DeviceCapabilityHelper

class WalkingAidService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var captureLoopJob: Job? = null
    private var visionWorkerJob: Job? = null

    private var visionBackend: VisionBackend? = null

    // "Latest frame wins" decoupled communication channel
    private val frameChannel = Channel<VisionFrame>(
        capacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var safetyDisclaimerSpoken = false
    private var wasAutoAudioEnabled = false

    override fun onCreate() {
        super.onCreate()
        WalkingAidNotificationHelper.ensureChannel(this)
        WalkingAidImageStore.load(this)
        WalkingAidWarningEngine.reset()
        initTts()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!DeviceCapabilityHelper.hasCamera(this)) {
            Log.w(TAG, "Stopping WalkingAidService: selected device profile has no camera")
            stopSelf()
            return START_NOT_STICKY
        }
        val readiness = WalkingAidReadinessChecker.checkReadiness(this)
        if (!readiness.isReady) {
            Log.w(TAG, "Stopping WalkingAidService: model readiness check failed: ${readiness.missingDetails}")
            stopSelf()
            return START_NOT_STICKY
        }
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
        super.onDestroy()
    }

    private fun initTts() {
        tts = TextToSpeech(this) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
        }
    }

    private fun canPostNotifications(): Boolean {
        return hasNotificationPermission(this)
    }

    private fun startForegroundSafely(content: String): Boolean {
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

        if (!startForegroundSafely("Walking Aid active — starting LiteRT Vision Engine...")) {
            RUNNING.set(false)
            stopSelf()
            return
        }

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

        if (MeetingCapturePrefs.getState(this).isRecording) {
            Log.w(TAG, "Meeting capture is active")
            showToast(this, getString(com.fersaiyan.cyanbridge.R.string.walking_aid_meeting_active))
            RUNNING.set(false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        wasAutoAudioEnabled = AutoAudioCapturePrefs.isEnabled(this)
        if (wasAutoAudioEnabled) {
            AutoAudioCapturePrefs.setEnabled(this, false)
            val pauseIntent = Intent(this, AutoAudioCaptureService::class.java)
                .setAction(AutoAudioCaptureService.ACTION_STOP)
            startService(pauseIntent)
        }

        safetyDisclaimerSpoken = false

        // Initialize LiteRT Vision Backend (NPU -> GPU -> CPU hierarchy)
        visionBackend = LiteRtVisionBackend(this)
        val accelInfo = visionBackend?.acceleratorInfo()
        Log.i(TAG, "Vision engine active: ${accelInfo?.details}")

        // 1. Launch dedicated Vision Worker (decoupled from camera acquisition rate)
        visionWorkerJob = scope.launch {
            var frameCount = 0
            for (frame in frameChannel) {
                if (!isActive) break
                val backend = visionBackend ?: continue

                val imageSource = WalkingAidPreferences.getImageDescriptionSource(this@WalkingAidService)
                val depthSource = WalkingAidPreferences.getDepthSource(this@WalkingAidService)
                val stateSource = WalkingAidPreferences.getStateModelSource(this@WalkingAidService)

                // 1. Detection (Cloud Vision or Local LiteRT)
                val detectionResult = if (imageSource == "cloud" && !frame.sourcePath.isNullOrBlank() && File(frame.sourcePath).exists()) {
                    val modelOverride = WalkingAidPreferences.getImageDescriptionModelOverride(this@WalkingAidService)
                    val prompt = "List all visible obstacles, vehicles, people, or hazards in this image for a walking aid assistant."
                    val cloudReply = CliRelayClient.imageQuery(this@WalkingAidService, frame.sourcePath, prompt, modelOverride = modelOverride).getOrDefault("")

                    val cloudObjects = mutableListOf<DetectedObject>()
                    if (cloudReply.isNotBlank()) {
                        val keywords = listOf("person", "car", "bicycle", "chair", "pole", "stairs", "step", "curb", "door", "table", "wall")
                        keywords.forEach { kw ->
                            if (cloudReply.contains(kw, ignoreCase = true)) {
                                cloudObjects.add(DetectedObject(label = kw, confidence = 0.90f, boundingBox = RectF(0.3f, 0.3f, 0.7f, 0.7f), position = "center"))
                            }
                        }
                    }
                    if (cloudObjects.isNotEmpty()) {
                        DetectionResult(objects = cloudObjects, acquisitionTimeMs = 0L, preprocessTimeMs = 0L, inferenceTimeMs = 500L, postprocessTimeMs = 0L)
                    } else {
                        backend.detect(frame)
                    }
                } else {
                    backend.detect(frame)
                }

                // 2. Depth Estimation (Cloud Depth or Local LiteRT)
                var depthResult: DepthResult? = null
                if (WalkingAidPreferences.isDepthEnabled(this@WalkingAidService)) {
                    if (depthSource == "cloud" && !frame.sourcePath.isNullOrBlank() && File(frame.sourcePath).exists()) {
                        val depthModelOverride = WalkingAidPreferences.getDepthModelOverride(this@WalkingAidService)
                        val depthPrompt = "Analyze relative depth, ground steps, curbs, and drop-offs in this image for a walking aid assistant."
                        val cloudDepthReply = CliRelayClient.imageQuery(this@WalkingAidService, frame.sourcePath, depthPrompt, modelOverride = depthModelOverride).getOrDefault("")
                        val hasDrop = cloudDepthReply.contains("step", ignoreCase = true) || cloudDepthReply.contains("curb", ignoreCase = true) || cloudDepthReply.contains("drop", ignoreCase = true)
                        depthResult = DepthResult(
                            relativeDepthSummary = cloudDepthReply.ifBlank { "Cloud relative depth analysis complete" },
                            groundDiscontinuityDetected = hasDrop,
                            closestRegion = "center",
                            inferenceTimeMs = 500L,
                        )
                    } else if (frameCount % 3 == 0 || detectionResult.objects.any { it.approaching }) {
                        depthResult = backend.estimateDepth(frame)
                    }
                }

                // 3. Deterministic Warning Evaluation
                val customPrompt = WalkingAidPreferences.getCustomPrompt(this@WalkingAidService)
                val warningDecision = WalkingAidWarningEngine.evaluate(detectionResult, depthResult, customPrompt)

                // 4. Scene LLM Reasoning Guidance (Cloud LLM or Local LLM)
                var spokenMessage = warningDecision.message
                if (warningDecision.shouldWarn && spokenMessage.isNotBlank()) {
                    val scenePrompt = "Summarize the hazard concisely in 1 short spoken sentence for a walking aid user: $spokenMessage"
                    if (stateSource == "cloud") {
                        val cloudLlmReply = CliRelayClient.voiceQuery(this@WalkingAidService, scenePrompt).getOrNull()
                        if (!cloudLlmReply.isNullOrBlank()) {
                            spokenMessage = cloudLlmReply.trim()
                        }
                    } else if (stateSource == "local") {
                        try {
                            val localLlmReply = LocalModelsProvider().streamChat(
                                context = this@WalkingAidService,
                                messages = listOf(mapOf("role" to "User", "content" to scenePrompt)),
                                requestPriority = LocalModelRequestPriority.HIGH,
                            )
                            if (localLlmReply.isNotBlank()) {
                                spokenMessage = localLlmReply.trim()
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Local Scene LLM streamChat error: ${e.message}")
                        }
                    }
                }

                // Store historical entry for GUI thumbnail playback and Q&A
                val descText = if (detectionResult.isError) {
                    "System Error: ${detectionResult.errorMessage ?: "Vision backend error"}"
                } else if (detectionResult.objects.isNotEmpty()) {
                    "Detected: " + detectionResult.objects.joinToString(", ") { "${it.label} (${it.position})" }
                } else {
                    "Clear walking trajectory"
                }

                if (detectionResult.isError) {
                    val errDetail = detectionResult.errorMessage ?: "Vision model error"
                    Log.e(TAG, "WalkingAid detection error: $errDetail")
                    val intervalSec = WalkingAidPreferences.getCaptureIntervalSeconds(this@WalkingAidService)
                    WalkingAidNotificationHelper.updateNotification(
                        this@WalkingAidService,
                        "Vision Error: $errDetail",
                        intervalSec,
                    )
                }

                val record = SceneRecord(
                    timestampMs = frame.timestampMs,
                    imagePath = frame.sourcePath ?: "",
                    description = descText,
                    depthDescription = depthResult?.relativeDepthSummary,
                    stateDecision = if (detectionResult.isError || warningDecision.shouldWarn) StateDecision.WARN else StateDecision.SKIP,
                )
                val maxHistory = WalkingAidPreferences.getImageHistoryMaxCount(this@WalkingAidService)
                WalkingAidImageStore.addRecord(record, maxHistory)
                WalkingAidImageStore.persist(this@WalkingAidService, maxHistory)

                // Trigger immediate audio warning via TTS
                if (warningDecision.shouldWarn && spokenMessage.isNotBlank()) {
                    val disclaimer = if (
                        WalkingAidPreferences.isSafetyDisclaimerEnabled(this@WalkingAidService) &&
                        !safetyDisclaimerSpoken
                    ) {
                        safetyDisclaimerSpoken = true
                        "Safety notice: check path carefully. "
                    } else ""

                    if (WalkingAidPreferences.isTtsEnabled(this@WalkingAidService)) {
                        speakTts(disclaimer + spokenMessage)
                    }
                }

                // Update real-time telemetry notification
                val accel = backend.acceleratorInfo()
                val intervalSec = WalkingAidPreferences.getCaptureIntervalSeconds(this@WalkingAidService)
                val statusMsg = "LiteRT (${accel.type.name.take(3)}): ${detectionResult.objects.size} objects in ${detectionResult.totalTimeMs}ms"
                WalkingAidNotificationHelper.updateNotification(this@WalkingAidService, statusMsg, intervalSec)

                frameCount++
            }
        }

        // 2. Launch Camera Capture Loop
        captureLoopJob = scope.launch {
            var captureIndex = 0
            if (isMetaRayban) {
                val metaManager = MetaRaybanManager.getInstance(this@WalkingAidService)
                if (!metaManager.awaitCameraReady()) {
                    val detail = metaManager.lastError.value ?: "Register and connect a Meta camera before using Walking Aid"
                    Log.e(TAG, "Meta Walking Aid cannot start: $detail\n${metaManager.diagnosticsSnapshot()}")
                    WalkingAidNotificationHelper.updateNotification(this@WalkingAidService, "Meta camera unavailable: ${detail.take(120)}", 0)
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

                val imageFile = captureThumbnail(captureIndex)
                if (imageFile != null && imageFile.exists()) {
                    val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
                    if (bitmap != null) {
                        val frame = VisionFrame(
                            bitmap = bitmap,
                            timestampMs = System.currentTimeMillis(),
                            captureIndex = captureIndex,
                            sourcePath = imageFile.absolutePath,
                        )
                        // Emit to vision worker ("latest frame wins"; drops oldest if NPU/GPU busy)
                        frameChannel.trySend(frame)
                    }
                } else {
                    Log.w(TAG, "No thumbnail captured, retrying after interval")
                }

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
        val completed = AtomicBoolean(false)
        var thumbnailTransferStarted = false
        try {
            val outDir = getExternalFilesDir("DCIM") ?: filesDir
            val file = File(outDir, "WALKING_AID_THUMB_${index}_${System.currentTimeMillis()}.jpg")
            runCatching {
                file.parentFile?.mkdirs()
                if (file.exists()) file.delete()
            }

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
                if (completed.compareAndSet(false, true)) {
                    GlassesSessionCoordinator.releaseBackgroundCommand(permit)
                }
                WalkingAidNotificationHelper.updateNotification(
                    this,
                    "Thumbnail capture timed out — retrying...",
                    WalkingAidPreferences.getCaptureIntervalSeconds(this),
                )
            }
            return result
        } catch (e: Exception) {
            if (completed.compareAndSet(false, true)) {
                GlassesSessionCoordinator.releaseBackgroundCommand(permit)
            }
            throw e
        }
    }

    private fun isMetaRaybanSelected(): Boolean = DeviceProfileStore.isMetaSelected(this)

    private fun speakTts(text: String) {
        if (!ttsReady || tts == null) {
            Log.w(TAG, "TTS not ready")
            return
        }
        val locale = Locale.forLanguageTag(ImageQuestionPreferences.get(this).appLanguageTag)
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
        captureLoopJob?.cancel()
        visionWorkerJob?.cancel()
        captureLoopJob = null
        visionWorkerJob = null

        visionBackend?.close()
        visionBackend = null

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
            if (!hasNotificationPermission(context) && context is FragmentActivity) {
                ensureNotificationPermission(context, "Walking Aid") { }
            }
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

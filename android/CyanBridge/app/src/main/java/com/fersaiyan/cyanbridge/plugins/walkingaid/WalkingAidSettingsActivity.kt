package com.fersaiyan.cyanbridge.plugins.walkingaid

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.CircularProgressIndicator
import com.fersaiyan.cyanbridge.agent.ProSubscriptionActivity
import com.fersaiyan.cyanbridge.plugins.walkingaid.vision.LiteRtVisionBackend
import com.fersaiyan.cyanbridge.plugins.walkingaid.vision.VisionFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fersaiyan.cyanbridge.R
import com.fersaiyan.cyanbridge.agent.ProSubscriptionRelayClient
import com.fersaiyan.cyanbridge.shared.plugins.NativePluginIds
import com.fersaiyan.cyanbridge.ui.CommunityPluginPrefs
import com.fersaiyan.cyanbridge.ui.installComposeHostWithLegacyAdapter
import com.fersaiyan.cyanbridge.ui.NativePluginShortcutPreference
import com.fersaiyan.cyanbridge.ui.setThemedComposeContent


import com.fersaiyan.cyanbridge.devices.DeviceCapabilityHelper
import androidx.compose.material.icons.filled.Warning

class WalkingAidSettingsActivity : AppCompatActivity() {

    private lateinit var composeView: ComposeView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        composeView = installComposeHostWithLegacyAdapter(R.layout.activity_walking_aid_settings)

        setThemedComposeContent(composeView) {
            WalkingAidSettingsScreen(
                onBack = ::finish,
                onStartService = { WalkingAidService.start(this) },
                onStopService = { WalkingAidService.stop(this) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalkingAidSettingsScreen(
    onBack: () -> Unit,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val hasCamera = remember { DeviceCapabilityHelper.hasCamera(context) }
    val cameraUnavailableReason = remember { DeviceCapabilityHelper.unavailableCameraReason(context) }

    // State
    var enabled by remember { mutableStateOf(WalkingAidPreferences.isEnabled(context)) }
    var captureInterval by remember { mutableIntStateOf(WalkingAidPreferences.getCaptureIntervalSeconds(context)) }
    var imageDescriptionSource by remember { mutableStateOf(WalkingAidPreferences.getImageDescriptionSource(context)) }
    var imageDescriptionCloudModelId by remember { mutableStateOf(WalkingAidPreferences.getImageDescriptionCloudModelId(context)) }
    var depthEnabled by remember { mutableStateOf(WalkingAidPreferences.isDepthEnabled(context)) }
    var depthSource by remember { mutableStateOf(WalkingAidPreferences.getDepthSource(context)) }
    var depthCloudModelId by remember { mutableStateOf(WalkingAidPreferences.getDepthCloudModelId(context)) }
    var ttsEnabled by remember { mutableStateOf(WalkingAidPreferences.isTtsEnabled(context)) }
    var safetyDisclaimerEnabled by remember { mutableStateOf(WalkingAidPreferences.isSafetyDisclaimerEnabled(context)) }
    var historyMaxCount by remember { mutableIntStateOf(WalkingAidPreferences.getImageHistoryMaxCount(context)) }
    var customPrompt by remember { mutableStateOf(WalkingAidPreferences.getCustomPrompt(context)) }

    // Model picker, latency test & readiness dialogs
    var yoloModelType by remember { mutableStateOf(WalkingAidPreferences.getYoloModelType(context)) }
    var focusDescriptionText by remember { mutableStateOf(WalkingAidPreferences.getFocusDescription(context)) }
    var isTestingLatency by remember { mutableStateOf(false) }
    var showLatencyReportDialog by remember { mutableStateOf(false) }
    var latencyReportText by remember { mutableStateOf("") }
    var isTestingAcquisition by remember { mutableStateOf(false) }
    var showAcquisitionReportDialog by remember { mutableStateOf(false) }
    var acquisitionReportText by remember { mutableStateOf("") }
    var thumbnailQuality by remember { mutableIntStateOf(WalkingAidPreferences.getThumbnailQualityLevel(context)) }
    var settleDelayMs by remember { mutableIntStateOf(WalkingAidPreferences.getPhotoSettleDelayMs(context).toInt()) }
    var showImageModelPicker by remember { mutableStateOf(false) }
    var showDepthModelPicker by remember { mutableStateOf(false) }
    var availableModels by remember { mutableStateOf<List<ProSubscriptionRelayClient.ModelOption>>(emptyList()) }
    var modelsLoading by remember { mutableStateOf(false) }
    var showReadinessResult by remember { mutableStateOf<WalkingAidReadinessResult?>(null) }
    var downloadingModelId by remember { mutableStateOf<String?>(null) }
    var modelDownloadStatus by remember { mutableStateOf("") }

    val intervalOptions = listOf(2, 3, 5, 10, 15, 30)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Walking Aid Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!hasCamera && cameraUnavailableReason != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Warning",
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Column {
                            Text(
                                text = "Camera Hardware Required",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Text(
                                text = "Walking Aid requires glasses with a point-of-view camera (such as HeyCyan or Meta Ray-Ban). $cameraUnavailableReason",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
            }

            // Section: General
            SectionTitle("General")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Enable switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Walking Aid enabled", modifier = Modifier.weight(1f))
                        Switch(
                            enabled = hasCamera,
                            checked = enabled && hasCamera,
                            onCheckedChange = { newValue ->
                                if (newValue) {
                                    val readiness = WalkingAidReadinessChecker.checkReadiness(context)
                                    if (!readiness.isReady) {
                                        showReadinessResult = readiness
                                        return@Switch
                                    }
                                }
                                enabled = newValue
                                WalkingAidPreferences.setEnabled(context, newValue)
                                CommunityPluginPrefs.setNativePluginEnabled(
                                    context,
                                    NativePluginIds.WALKING_AID,
                                    newValue,
                                )
                                if (newValue) {
                                    onStartService()
                                } else {
                                    onStopService()
                                }
                            },
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Capture interval
                    Text(
                        "Capture interval",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        intervalOptions.forEach { option ->
                            FilterChip(
                                selected = captureInterval == option,
                                onClick = {
                                    captureInterval = option
                                    WalkingAidPreferences.setCaptureIntervalSeconds(context, option)
                                },
                                label = { Text("${option}s") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                ),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Latency Test Button
                    Button(
                        onClick = {
                            isTestingLatency = true
                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    val captureStartedAt = SystemClock.elapsedRealtime()
                                    val capturedThumbnail = WalkingAidImageCapture(context)
                                        .captureFreshThumbnail("WALKING_AID_BENCHMARK")
                                    val captureMs = SystemClock.elapsedRealtime() - captureStartedAt
                                    val report = try {
                                        val testBitmap = checkNotNull(BitmapFactory.decodeFile(capturedThumbnail.file.absolutePath)) {
                                            "Fresh glasses thumbnail could not be decoded"
                                        }
                                        val inputDescription =
                                            "Fresh glasses thumbnail, quality ${WalkingAidPreferences.getThumbnailQualityLevel(context)} (${testBitmap.width}x${testBitmap.height})"
                                        val frame = VisionFrame(
                                            bitmap = testBitmap,
                                            timestampMs = capturedThumbnail.captureCommandAtMs,
                                            captureCommandAtMs = capturedThumbnail.captureCommandAtMs,
                                            estimatedExposureAtMs = capturedThumbnail.estimatedExposureAtMs,
                                            receivedAtMs = capturedThumbnail.receivedAtMs,
                                        )
                                        val backend = LiteRtVisionBackend(context)
                                        try {
                                            val t0 = SystemClock.elapsedRealtime()
                                            val detResult = backend.detect(frame)
                                            val t1 = SystemClock.elapsedRealtime()
                                            val depthResult = if (depthEnabled && depthSource == "local") {
                                                backend.estimateDepth(frame)
                                            } else {
                                                null
                                            }
                                            val t2 = SystemClock.elapsedRealtime()

                                            WalkingAidWarningEngine.reset()
                                            WalkingAidWarningEngine.evaluate(detResult, depthResult, focusDescriptionText)
                                            val t3 = SystemClock.elapsedRealtime()

                                            val accelInfo = backend.acceleratorInfo()
                                            val analysisMs = t3 - t0
                                            val detectorThroughput = if (!detResult.isError && analysisMs > 0) {
                                                String.format("%.1f FPS local detector throughput", 1000f / analysisMs)
                                            } else {
                                                "unavailable because detection failed"
                                            }
                                            val inferenceValue = if (detResult.isError) {
                                                "not completed"
                                            } else {
                                                "${detResult.inferenceTimeMs} ms"
                                            }
                                            val depthValue = when {
                                                !depthEnabled -> "skipped (disabled)"
                                                depthSource != "local" -> "skipped (cloud source selected)"
                                                else -> "${depthResult?.inferenceTimeMs ?: 0} ms"
                                            }
                                            val detectorStatus = if (detResult.isError) {
                                                "ERROR: ${detResult.errorMessage ?: "unknown detector error"}"
                                            } else {
                                                "OK"
                                            }
                                            val delegation = if (accelInfo.totalOperators > 0) {
                                                "${accelInfo.delegatedOperators}/${accelInfo.totalOperators}"
                                            } else {
                                                "not reported by LiteRT"
                                            }

                                            // Measured start-to-start cadence: run two captures back-to-back
                                            val secondCaptureStart = SystemClock.elapsedRealtime()
                                            val secondThumbnail = WalkingAidImageCapture(context)
                                                .captureFreshThumbnail("WALKING_AID_BENCHMARK_2")
                                            val secondCaptureMs = SystemClock.elapsedRealtime() - secondCaptureStart
                                            secondThumbnail.file.delete()
                                            val measuredCadenceMs = captureMs + secondCaptureMs
                                            val measuredFps = if (measuredCadenceMs > 0) 1000f / measuredCadenceMs else 0f

                                            """
                                                Pipeline Latency Benchmark

                                                Input: $inputDescription
                                                Detector status: $detectorStatus

                                                Capture breakdown:
                                                  BLE command → photo-ready: ${capturedThumbnail.estimatedExposureAtMs - capturedThumbnail.captureCommandAtMs} ms
                                                  Settle delay: ${WalkingAidPreferences.getPhotoSettleDelayMs(context)} ms
                                                  BLE transfer + decode: $captureMs ms total
                                                  Measured start-to-start cadence (2 captures): ${measuredCadenceMs} ms (${String.format("%.2f fps", measuredFps)})

                                                Analysis breakdown:
                                                  Preprocessing: ${detResult.preprocessTimeMs} ms
                                                  YOLO inference: $inferenceValue
                                                  Postprocessing (NMS): ${detResult.postprocessTimeMs} ms
                                                  Detector call total: ${t1 - t0} ms (${detResult.objects.size} objects)
                                                  Depth estimation: $depthValue
                                                  Rule evaluation: ${t3 - t2} ms

                                                Detector throughput: $analysisMs ms ($detectorThroughput)
                                                Capture-to-decision total: $captureMs + $analysisMs = ${captureMs + analysisMs} ms
                                                Effective rate at configured ${WalkingAidPreferences.getCaptureIntervalSeconds(context)}s interval: ${String.format("%.2f fps", 1000f / (captureMs + WalkingAidPreferences.getCaptureIntervalSeconds(context) * 1000f).coerceAtLeast(1f))}
                                                Hardware accelerator: ${accelInfo.type}
                                                Delegated operators: $delegation
                                                Details: ${accelInfo.details}
                                            """.trimIndent()
                                        } finally {
                                            backend.close()
                                            testBitmap.recycle()
                                        }
                                    } finally {
                                        capturedThumbnail.file.delete()
                                    }

                                    withContext(Dispatchers.Main) {
                                        latencyReportText = report
                                        showLatencyReportDialog = true
                                        isTestingLatency = false
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Latency test error: ${e.message}", Toast.LENGTH_LONG).show()
                                        isTestingLatency = false
                                    }
                                }
                            }
                        },
                        enabled = !isTestingLatency,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (isTestingLatency) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(20.dp).width(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Running Benchmark...")
                        } else {
                            Text("⚡ Test End-to-End Latency")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Acquisition Benchmark Button
                    Button(
                        onClick = {
                            isTestingAcquisition = true
                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    val sb = StringBuilder()
                                    sb.appendLine("Thumbnail Acquisition Benchmark (quality 0-5)")
                                    sb.appendLine("Each level: 2 consecutive captures")
                                    sb.appendLine("Settle delay: ${WalkingAidPreferences.getPhotoSettleDelayMs(context)} ms")
                                    sb.appendLine()
                                    for (quality in 0..5) {
                                        val times = mutableListOf<Long>()
                                        var sizeBytes = 0L
                                        var width = 0
                                        var height = 0
                                        repeat(2) {
                                            val start = SystemClock.elapsedRealtime()
                                            val thumbnail = WalkingAidImageCapture(context)
                                                .captureFreshThumbnailWithQuality("ACQ_BENCH_Q$quality", quality)
                                            val elapsed = SystemClock.elapsedRealtime() - start
                                            times += elapsed
                                            sizeBytes = thumbnail.file.length()
                                            val bmp = BitmapFactory.decodeFile(thumbnail.file.absolutePath)
                                            if (bmp != null) {
                                                width = bmp.width
                                                height = bmp.height
                                                bmp.recycle()
                                            }
                                            thumbnail.file.delete()
                                        }
                                        val avg = times.average().toLong()
                                        sb.appendLine("Quality $quality: ${avg}ms avg, ${width}x${height}, ${sizeBytes / 1024}KB")
                                    }
                                    withContext(Dispatchers.Main) {
                                        acquisitionReportText = sb.toString().trimEnd()
                                        showAcquisitionReportDialog = true
                                        isTestingAcquisition = false
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Acquisition benchmark error: ${e.message}", Toast.LENGTH_LONG).show()
                                        isTestingAcquisition = false
                                    }
                                }
                            }
                        },
                        enabled = !isTestingAcquisition && hasCamera,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (isTestingAcquisition) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(20.dp).width(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Running Acquisition Benchmark...")
                        } else {
                            Text("Benchmark All Quality Levels (0-5)")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        "Thumbnail quality level",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        "Lower = smaller images, faster transfer. Higher = better detection accuracy.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        (0..5).forEach { level ->
                            FilterChip(
                                selected = thumbnailQuality == level,
                                onClick = {
                                    thumbnailQuality = level
                                    WalkingAidPreferences.setThumbnailQualityLevel(context, level)
                                },
                                label = { Text("$level") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                ),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        "Photo settle delay: ${settleDelayMs} ms",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        "Time to wait after glasses confirm photo before reading data. Reduce for faster cadence, increase if transfers fail.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Slider(
                        value = settleDelayMs.toFloat(),
                        onValueChange = { newValue ->
                            settleDelayMs = newValue.toInt()
                            WalkingAidPreferences.setPhotoSettleDelayMs(context, newValue.toInt())
                        },
                        valueRange = 0f..500f,
                        steps = 9,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            SectionTitle("Glasses tab")
            NativePluginShortcutPreference(
                pluginId = NativePluginIds.WALKING_AID,
                pluginTitle = "Walking Aid",
            )

            // Section: Image Recognition
            SectionTitle("Image Recognition")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Safety detection and scene context",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = imageDescriptionSource == "local",
                            onClick = {
                                imageDescriptionSource = "local"
                                WalkingAidPreferences.setImageDescriptionSource(context, "local")
                            },
                            label = { Text("Local safety only") },
                        )
                        FilterChip(
                            selected = imageDescriptionSource == "cloud",
                            onClick = {
                                imageDescriptionSource = "cloud"
                                WalkingAidPreferences.setImageDescriptionSource(context, "cloud")
                                showImageModelPicker = true
                            },
                            label = { Text("Local + Cloud context (Pro)") },
                        )
                    }

                    Text(
                        "Local YOLO always makes immediate safety decisions. Cloud vision only enriches saved scene descriptions and never delays warnings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (imageDescriptionSource == "cloud") {
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = { showImageModelPicker = true }) {
                            Text(
                                text = if (imageDescriptionCloudModelId.isNotBlank())
                                    "Model: $imageDescriptionCloudModelId"
                                else
                                    "Select model...",
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Local detection model",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = yoloModelType == WalkingAidPreferences.MODEL_TYPE_YOLO11,
                            onClick = {
                                yoloModelType = WalkingAidPreferences.MODEL_TYPE_YOLO11
                                WalkingAidPreferences.setYoloModelType(context, WalkingAidPreferences.MODEL_TYPE_YOLO11)
                            },
                            label = { Text("YOLO11 (faster)") },
                        )
                        FilterChip(
                            selected = yoloModelType == WalkingAidPreferences.MODEL_TYPE_YOLO_WORLD,
                            onClick = {
                                yoloModelType = WalkingAidPreferences.MODEL_TYPE_YOLO_WORLD
                                WalkingAidPreferences.setYoloModelType(context, WalkingAidPreferences.MODEL_TYPE_YOLO_WORLD)
                            },
                            label = { Text("YOLO-World (larger)") },
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    WalkingAidModelDownloadCard(
                        entry = WalkingAidModelCatalog.detectorFor(yoloModelType),
                        installed = WalkingAidModelInstaller.isInstalled(
                            context,
                            WalkingAidModelCatalog.detectorFor(yoloModelType),
                        ),
                        isDownloading = downloadingModelId == WalkingAidModelCatalog.detectorFor(yoloModelType).id,
                        status = modelDownloadStatus,
                        onDownload = { entry ->
                            downloadingModelId = entry.id
                            modelDownloadStatus = "Starting ${entry.displayName}..."
                            CoroutineScope(Dispatchers.IO).launch {
                                runCatching {
                                    WalkingAidModelInstaller.install(context, entry) { progress ->
                                        CoroutineScope(Dispatchers.Main).launch {
                                            modelDownloadStatus = "Downloading ${progress.percent}%"
                                        }
                                    }
                                }.onSuccess {
                                    withContext(Dispatchers.Main) {
                                        modelDownloadStatus = "${entry.displayName} is ready for local use."
                                        downloadingModelId = null
                                    }
                                }.onFailure { error ->
                                    withContext(Dispatchers.Main) {
                                        modelDownloadStatus = "Download failed: ${error.message ?: "unknown error"}"
                                        downloadingModelId = null
                                    }
                                }
                            }
                        },
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "What should Walking Aid pay extra attention to?",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    TextField(
                        value = focusDescriptionText,
                        onValueChange = { newValue ->
                            focusDescriptionText = newValue
                            WalkingAidPreferences.setFocusDescription(context, newValue)
                        },
                        placeholder = { Text("For example: Please warn me about pets, traffic, and things I could trip over") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4,
                        supportingText = {
                            val understood = WalkingAidFocusMapper.resolve(focusDescriptionText)
                            Text(
                                if (understood.isEmpty()) {
                                    "Write naturally or use keyboard dictation. Built-in safety alerts remain active."
                                } else {
                                    "Walking Aid will pay extra attention to ${WalkingAidFocusMapper.friendlySummary(understood)}."
                                }
                            )
                        },
                    )
                }
            }

            // Section: Depth Estimation
            SectionTitle("Depth Estimation")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Depth enabled", modifier = Modifier.weight(1f))
                        Switch(
                            checked = depthEnabled,
                            onCheckedChange = { newValue ->
                                depthEnabled = newValue
                                WalkingAidPreferences.setDepthEnabled(context, newValue)
                            },
                        )
                    }

                    if (depthEnabled) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Source",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = depthSource == "local",
                                onClick = {
                                    depthSource = "local"
                                    WalkingAidPreferences.setDepthSource(context, "local")
                                },
                                label = { Text("Local (on-device)") },
                            )
                            FilterChip(
                                selected = depthSource == "cloud",
                                onClick = {
                                    depthSource = "cloud"
                                    WalkingAidPreferences.setDepthSource(context, "cloud")
                                    showDepthModelPicker = true
                                },
                                label = { Text("Cloud (Pro)") },
                            )
                        }

                        if (depthSource == "cloud") {
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(onClick = { showDepthModelPicker = true }) {
                                Text(
                                    text = if (depthCloudModelId.isNotBlank())
                                        "Model: $depthCloudModelId"
                                    else
                                        "Select model...",
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.height(12.dp))
                            WalkingAidModelDownloadCard(
                                entry = WalkingAidModelCatalog.depth,
                                installed = WalkingAidModelInstaller.isInstalled(context, WalkingAidModelCatalog.depth),
                                isDownloading = downloadingModelId == WalkingAidModelCatalog.depth.id,
                                status = modelDownloadStatus,
                                onDownload = { entry ->
                                    downloadingModelId = entry.id
                                    modelDownloadStatus = "Starting ${entry.displayName}..."
                                    CoroutineScope(Dispatchers.IO).launch {
                                        runCatching {
                                            WalkingAidModelInstaller.install(context, entry) { progress ->
                                                CoroutineScope(Dispatchers.Main).launch {
                                                    modelDownloadStatus = "Downloading ${progress.percent}%"
                                                }
                                            }
                                        }.onSuccess {
                                            withContext(Dispatchers.Main) {
                                                modelDownloadStatus = "${entry.displayName} is ready for local use."
                                                downloadingModelId = null
                                            }
                                        }.onFailure { error ->
                                            withContext(Dispatchers.Main) {
                                                modelDownloadStatus = "Download failed: ${error.message ?: "unknown error"}"
                                                downloadingModelId = null
                                            }
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }

            // Section: Output
            SectionTitle("Output")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Speak descriptions aloud", modifier = Modifier.weight(1f))
                        Switch(
                            checked = ttsEnabled,
                            onCheckedChange = { newValue ->
                                ttsEnabled = newValue
                                WalkingAidPreferences.setTtsEnabled(context, newValue)
                            },
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Safety disclaimer on first warning", modifier = Modifier.weight(1f))
                        Switch(
                            checked = safetyDisclaimerEnabled,
                            onCheckedChange = { newValue ->
                                safetyDisclaimerEnabled = newValue
                                WalkingAidPreferences.setSafetyDisclaimerEnabled(context, newValue)
                            },
                        )
                    }
                }
            }

            // Section: Custom Instructions
            SectionTitle("Custom Instructions")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Additional instructions appended to all Walking Aid prompts (image description, depth, and state model). These are specific to this plugin and do not affect the global vision profile in Settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = customPrompt,
                        onValueChange = { newValue ->
                            if (newValue.length <= 1500) {
                                customPrompt = newValue
                                WalkingAidPreferences.setCustomPrompt(context, newValue)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Walking Aid instructions") },
                        placeholder = { Text("e.g. Always mention people and their positions. Focus on sidewalk edges.") },
                        minLines = 3,
                        maxLines = 6,
                        supportingText = { Text("${customPrompt.length}/1500") },
                    )
                }
            }

            // Section: History
            SectionTitle("Image History")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Max stored descriptions: ${historyMaxCount}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Slider(
                        value = historyMaxCount.toFloat(),
                        onValueChange = { newValue ->
                            historyMaxCount = newValue.toInt()
                            WalkingAidPreferences.setImageHistoryMaxCount(context, newValue.toInt())
                        },
                        valueRange = 10f..100f,
                        steps = 8,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            WalkingAidImageStore.clear(context)
                            Toast.makeText(context, "History cleared", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Clear History")
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Model picker dialogs
    if (showImageModelPicker) {
        ModelPickerDialog(
            availableModels = availableModels,
            isLoading = modelsLoading,
            selectedModelId = imageDescriptionCloudModelId,
            onSelect = { modelId ->
                imageDescriptionCloudModelId = modelId
                WalkingAidPreferences.setImageDescriptionCloudModelId(context, modelId)
                showImageModelPicker = false
            },
            onDismiss = { showImageModelPicker = false },
            onRefresh = {
                modelsLoading = true
                CoroutineScope(Dispatchers.IO).launch {
                    val result = ProSubscriptionRelayClient.fetchAvailableModels(context)
                    result.onSuccess { models ->
                        availableModels = models
                    }.onFailure {
                        CoroutineScope(Dispatchers.Main).launch {
                            Toast.makeText(context, "Failed to load models: ${it.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                    modelsLoading = false
                }
            },
        )
    }

    if (showDepthModelPicker) {
        ModelPickerDialog(
            availableModels = availableModels,
            isLoading = modelsLoading,
            selectedModelId = depthCloudModelId,
            onSelect = { modelId ->
                depthCloudModelId = modelId
                WalkingAidPreferences.setDepthCloudModelId(context, modelId)
                showDepthModelPicker = false
            },
            onDismiss = { showDepthModelPicker = false },
            onRefresh = {
                modelsLoading = true
                CoroutineScope(Dispatchers.IO).launch {
                    val result = ProSubscriptionRelayClient.fetchAvailableModels(context)
                    result.onSuccess { models ->
                        availableModels = models
                    }.onFailure {
                        CoroutineScope(Dispatchers.Main).launch {
                            Toast.makeText(context, "Failed to load models: ${it.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                    modelsLoading = false
                }
            },
        )
    }

    if (showLatencyReportDialog) {
        AlertDialog(
            onDismissRequest = { showLatencyReportDialog = false },
            title = { Text("Walking Aid Latency Benchmark") },
            text = {
                Text(
                    text = latencyReportText,
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = {
                TextButton(onClick = { showLatencyReportDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showAcquisitionReportDialog) {
        AlertDialog(
            onDismissRequest = { showAcquisitionReportDialog = false },
            title = { Text("Acquisition Benchmark") },
            text = {
                Text(
                    text = acquisitionReportText,
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = {
                TextButton(onClick = { showAcquisitionReportDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    showReadinessResult?.let { readiness ->
        AlertDialog(
            onDismissRequest = { showReadinessResult = null },
            title = { Text("Walking Aid Setup Required") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Before starting Walking Aid, complete the missing setup items. YOLO and depth models are downloaded and selected in this screen.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    readiness.missingDetails.forEach { detail ->
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showReadinessResult = null
                    }
                ) {
                    Text("Review Walking Aid Settings")
                }
            },
            dismissButton = {
                if (readiness.requiresProForCloud) {
                    TextButton(
                        onClick = {
                            showReadinessResult = null
                            val intent = Intent(context, ProSubscriptionActivity::class.java)
                            context.startActivity(intent)
                        }
                    ) {
                        Text("Upgrade to Pro")
                    }
                } else {
                    TextButton(onClick = { showReadinessResult = null }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }
}

@Composable
private fun WalkingAidModelDownloadCard(
    entry: WalkingAidModelCatalogEntry,
    installed: Boolean,
    isDownloading: Boolean,
    status: String,
    onDownload: (WalkingAidModelCatalogEntry) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(entry.displayName, fontWeight = FontWeight.SemiBold)
            Text(entry.description, style = MaterialTheme.typography.bodySmall)
            Text(
                "${entry.sizeBytes / (1024 * 1024)} MB · ${entry.licenseNote}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (isDownloading) {
                Text(status, style = MaterialTheme.typography.bodySmall)
            }
            Button(
                onClick = { onDownload(entry) },
                enabled = !installed && !isDownloading,
            ) {
                Text(
                    when {
                        installed -> "Installed"
                        isDownloading -> "Downloading..."
                        else -> "Download from Hugging Face"
                    },
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp),
    )
}

@Composable
private fun ModelPickerDialog(
    availableModels: List<ProSubscriptionRelayClient.ModelOption>,
    isLoading: Boolean,
    selectedModelId: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
) {
    LaunchedEffect(Unit) {
        if (availableModels.isEmpty()) {
            onRefresh()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Model") },
        text = {
            Column {
                if (isLoading) {
                    Text("Loading models...")
                } else if (availableModels.isEmpty()) {
                    Text("No models available. Tap Refresh to load from server.")
                } else {
                    Text(
                        "Current: $selectedModelId",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    LazyColumn(
                        modifier = Modifier.height(300.dp),
                    ) {
                        items(availableModels) { option ->
                            TextButton(
                                onClick = {
                                    onSelect(option.id)
                                    onDismiss()
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column {
                                    Text(
                                        text = option.label.ifBlank { option.id },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (option.id == selectedModelId)
                                            androidx.compose.ui.text.font.FontWeight.Bold
                                        else
                                            androidx.compose.ui.text.font.FontWeight.Normal,
                                    )
                                    Text(
                                        text = option.id,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        },
        dismissButton = {
            if (!isLoading) {
                TextButton(onClick = onRefresh) {
                    Text("Refresh")
                }
            }
        },
    )
}

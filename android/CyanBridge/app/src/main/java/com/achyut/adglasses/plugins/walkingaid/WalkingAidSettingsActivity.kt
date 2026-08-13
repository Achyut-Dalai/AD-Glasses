package com.achyut.adglasses.plugins.walkingaid

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.CircularProgressIndicator
import com.achyut.adglasses.agent.LocalModelsConfigureActivity
import com.achyut.adglasses.agent.MainActivity
import com.achyut.adglasses.plugins.walkingaid.vision.LiteRtVisionBackend
import com.achyut.adglasses.plugins.walkingaid.vision.VisionFrame
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
import com.achyut.adglasses.R
import com.achyut.adglasses.agent.CliCloudClient
import com.achyut.adglasses.shared.plugins.NativePluginIds
import com.achyut.adglasses.ui.CommunityPluginPrefs
import com.achyut.adglasses.ui.installComposeHostWithLegacyAdapter
import com.achyut.adglasses.ui.NativePluginShortcutPreference
import com.achyut.adglasses.ui.setThemedComposeContent


import com.achyut.adglasses.devices.DeviceCapabilityHelper
import androidx.compose.material.icons.filled.Warning

class WalkingAidSettingsActivity : AppCompatActivity() {

    private lateinit var composeView: ComposeView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        composeView = installComposeHostWithLegacyAdapter(R.layout.activity_hands_free_translator_settings)

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
    var watchlistTermsText by remember { mutableStateOf(WalkingAidPreferences.getWatchlistTerms(context).joinToString(", ")) }
    var isTestingLatency by remember { mutableStateOf(false) }
    var showLatencyReportDialog by remember { mutableStateOf(false) }
    var latencyReportText by remember { mutableStateOf("") }
    var showImageModelPicker by remember { mutableStateOf(false) }
    var showDepthModelPicker by remember { mutableStateOf(false) }
    var availableModels by remember { mutableStateOf<List<CliCloudClient.ModelOption>>(emptyList()) }
    var modelsLoading by remember { mutableStateOf(false) }
    var showReadinessResult by remember { mutableStateOf<WalkingAidReadinessResult?>(null) }

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
                                    val backend = LiteRtVisionBackend(context)
                                    val testBitmap = Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888)
                                    val frame = VisionFrame(testBitmap, System.currentTimeMillis())

                                    val t0 = SystemClock.elapsedRealtime()
                                    val detResult = backend.detect(frame)
                                    val t1 = SystemClock.elapsedRealtime()
                                    val depthResult = backend.estimateDepth(frame)
                                    val t2 = SystemClock.elapsedRealtime()

                                    val engine = WalkingAidWarningEngine
                                    engine.reset()
                                    engine.evaluate(detResult, depthResult, watchlistTermsText)
                                    val t3 = SystemClock.elapsedRealtime()

                                    val accelInfo = backend.acceleratorInfo()
                                    backend.close()

                                    val totalMs = t3 - t0
                                    val fpsEquiv = if (totalMs > 0) String.format("%.1f", 1000f / totalMs) else "0"

                                    val report = """
                                        ⏱️ Pipeline Latency Benchmark Breakdown:

                                        📸 Preprocessing: ${detResult.preprocessTimeMs} ms
                                        🎯 YOLO Detection: ${detResult.inferenceTimeMs} ms (${detResult.objects.size} objects)
                                        ⚙️ Postprocessing (NMS): ${detResult.postprocessTimeMs} ms
                                        🌊 Depth Estimation: ${depthResult?.inferenceTimeMs ?: 0} ms
                                        ⚡ Rule Evaluation: ${t3 - t2} ms

                                        🚀 Total End-to-End Latency: $totalMs ms ($fpsEquiv FPS equiv)
                                        💻 Hardware Accelerator: ${accelInfo.type}
                                        📊 Delegated Operators: ${accelInfo.delegatedOperators}/${accelInfo.totalOperators}
                                        ℹ️ Details: ${accelInfo.details}
                                    """.trimIndent()

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
                        "Source",
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
                            label = { Text("Local (on-device)") },
                        )
                        FilterChip(
                            selected = imageDescriptionSource == "cloud",
                            onClick = {
                                imageDescriptionSource = "cloud"
                                WalkingAidPreferences.setImageDescriptionSource(context, "cloud")
                                showImageModelPicker = true
                            },
                            label = { Text("Cloud (Pro)") },
                        )
                    }

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
                    } else {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Local YOLO Model Architecture",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = yoloModelType == WalkingAidPreferences.MODEL_TYPE_YOLO11,
                                onClick = {
                                    yoloModelType = WalkingAidPreferences.MODEL_TYPE_YOLO11
                                    WalkingAidPreferences.setYoloModelType(context, WalkingAidPreferences.MODEL_TYPE_YOLO11)
                                },
                                label = { Text("YOLOv11 (COCO 80)") },
                            )
                            FilterChip(
                                selected = yoloModelType == WalkingAidPreferences.MODEL_TYPE_YOLO_WORLD,
                                onClick = {
                                    yoloModelType = WalkingAidPreferences.MODEL_TYPE_YOLO_WORLD
                                    WalkingAidPreferences.setYoloModelType(context, WalkingAidPreferences.MODEL_TYPE_YOLO_WORLD)
                                },
                                label = { Text("YOLO-World (Open Vocab)") },
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Target Watchlist Classes",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        TextField(
                            value = watchlistTermsText,
                            onValueChange = { newValue ->
                                watchlistTermsText = newValue
                                val terms = newValue.split(",").map { it.trim() }
                                WalkingAidPreferences.setWatchlistTerms(context, terms)
                            },
                            placeholder = { Text("e.g. person, bicycle, stairs, pothole, curb") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                    }
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
                    val result = CliCloudClient.fetchAvailableModels(context)
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
                    val result = CliCloudClient.fetchAvailableModels(context)
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

    showReadinessResult?.let { readiness ->
        AlertDialog(
            onDismissRequest = { showReadinessResult = null },
            title = { Text("Walking Aid Setup Required") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Before starting Walking Aid, please complete the following setup items:",
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
                        val intent = Intent(context, LocalModelsConfigureActivity::class.java)
                        context.startActivity(intent)
                    }
                ) {
                    Text("Open Local Models Settings")
                }
            },
            dismissButton = {
                if (readiness.requiresProForCloud) {
                    TextButton(
                        onClick = {
                            showReadinessResult = null
                            val intent = Intent(context, MainActivity::class.java)
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
    availableModels: List<CliCloudClient.ModelOption>,
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

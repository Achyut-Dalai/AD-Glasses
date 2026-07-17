package com.fersaiyan.cyanbridge.plugins.walkingaid

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
import com.fersaiyan.cyanbridge.ui.installComposeHostWithLegacyAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WalkingAidSettingsActivity : AppCompatActivity() {

    private lateinit var composeView: ComposeView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        composeView = installComposeHostWithLegacyAdapter(R.layout.activity_walking_aid_settings)

        composeView.setContent {
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

    // Model picker dialogs
    var showImageModelPicker by remember { mutableStateOf(false) }
    var showDepthModelPicker by remember { mutableStateOf(false) }
    var availableModels by remember { mutableStateOf<List<ProSubscriptionRelayClient.ModelOption>>(emptyList()) }
    var modelsLoading by remember { mutableStateOf(false) }

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
                            checked = enabled,
                            onCheckedChange = { newValue ->
                                enabled = newValue
                                WalkingAidPreferences.setEnabled(context, newValue)
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
                }
            }

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

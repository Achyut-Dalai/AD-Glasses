package com.fersaiyan.cyanbridge.shared.ui.localmodels

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fersaiyan.cyanbridge.shared.localmodels.LocalModelDownloadUiState
import com.fersaiyan.cyanbridge.shared.localmodels.LocalModelOptionField
import com.fersaiyan.cyanbridge.shared.localmodels.LocalModelTextField
import com.fersaiyan.cyanbridge.shared.localmodels.LocalModelToggleField
import com.fersaiyan.cyanbridge.shared.localmodels.LocalModelsAction
import com.fersaiyan.cyanbridge.shared.localmodels.LocalModelsConfigureUiState
import com.fersaiyan.cyanbridge.shared.localmodels.LocalModelsSection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalModelsConfigureScreen(
    state: LocalModelsConfigureUiState,
    onAction: (LocalModelsAction) -> Unit,
) {
    var showUnsavedChangesDialog by rememberSaveable { mutableStateOf(false) }
    val requestBack = {
        if (state.hasUnsavedChanges) {
            showUnsavedChangesDialog = true
        } else {
            onAction(LocalModelsAction.Back)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("Local models") },
                navigationIcon = {
                    IconButton(onClick = requestBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .testTag("local_models_configure"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ScreenCard("On-device runtime") {
                    Text(state.engineStatus, style = MaterialTheme.typography.bodyMedium)
                    if (state.deviceSummary.isNotBlank()) {
                        Text(
                            state.deviceSummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        state.selectedModelStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ActionRow(
                        primaryLabel = "Import model file",
                        onPrimary = { onAction(LocalModelsAction.ImportModel) },
                        secondaryLabel = "Refresh",
                        onSecondary = { onAction(LocalModelsAction.Refresh) },
                    )
                    if (state.emptyStateMessage.isNotBlank()) {
                        Text(
                            state.emptyStateMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        state.catalog.firstOrNull { it.id == "qwen2.5-0.5b-instruct-q4" }?.let { starter ->
                            FilledTonalButton(
                                onClick = { onAction(LocalModelsAction.DownloadCatalogModel(starter.id)) },
                                enabled = starter.canDownload,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Download starter model")
                            }
                        }
                    }
                }
            }
            if (state.download.isInFlight ||
                state.download.message.isNotBlank() ||
                state.download.progressPercent != null
            ) {
                item {
                    DownloadProgressCard(
                        state = state.download,
                        onAction = onAction,
                    )
                }
            }
            item {
                ScreenCard("Installed models") {
                    if (state.installedModels.isEmpty()) {
                        Text(
                            "No local model is installed. Import a GGUF/LiteRT file or download a catalog model.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        ChoiceField(
                            label = "Selected model",
                            value = state.installedModels.firstOrNull { it.id == state.selectedInstalledModelId }?.label
                                ?: "Select model",
                            options = state.installedModels.map { it.label },
                            onSelected = { index ->
                                state.installedModels.getOrNull(index)?.let {
                                    onAction(LocalModelsAction.SelectInstalledModel(it.id))
                                }
                            },
                        )
                    }
                    ActionRow(
                        primaryLabel = "Model info",
                        onPrimary = { onAction(LocalModelsAction.ShowSelectedModelInfo) },
                        secondaryLabel = "Unload",
                        onSecondary = { onAction(LocalModelsAction.UnloadSelectedModel) },
                        enabled = state.selectedInstalledModelId != null,
                    )
                    OutlinedButton(
                        onClick = { onAction(LocalModelsAction.RemoveSelectedModel) },
                        enabled = state.selectedInstalledModelId != null,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Remove selected model")
                    }
                }
            }
            item {
                ExpandableCard(
                    title = "Curated catalog",
                    expanded = state.catalogExpanded,
                    onToggle = { onAction(LocalModelsAction.ToggleSection(LocalModelsSection.CATALOG)) },
                ) {
                    Text(
                        "Some model families require accepted terms or a Hugging Face token.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    state.catalog.forEachIndexed { index, model ->
                        if (index > 0) HorizontalDivider()
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(model.title, style = MaterialTheme.typography.titleSmall)
                            Text(
                                model.details,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                model.status,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            ActionRow(
                                primaryLabel = model.downloadLabel,
                                onPrimary = { onAction(LocalModelsAction.DownloadCatalogModel(model.id)) },
                                secondaryLabel = "Info",
                                onSecondary = { onAction(LocalModelsAction.ShowCatalogModelInfo(model.id)) },
                                enabled = model.canDownload,
                                secondaryEnabled = true,
                            )
                        }
                    }
                }
            }
            item {
                ExpandableCard(
                    title = "Remote server",
                    expanded = state.remoteServerExpanded,
                    onToggle = { onAction(LocalModelsAction.ToggleSection(LocalModelsSection.REMOTE_SERVER)) },
                ) {
                    Text(
                        "Connect to an OpenAI-compatible server on your LAN or Tailnet. It must expose /v1/chat/completions; images use image_url data URLs and audio uses input_audio base64 (WAV/MP3).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ToggleRow(
                        label = "Use remote server instead of on-device model",
                        checked = state.remoteServer.enabled,
                        onCheckedChange = {
                            onAction(
                                LocalModelsAction.SetToggle(
                                    LocalModelToggleField.REMOTE_SERVER_ENABLED,
                                    it,
                                ),
                            )
                        },
                    )
                    ModelTextField(
                        label = "Base URL",
                        value = state.remoteServer.baseUrl,
                        onValueChange = {
                            onAction(LocalModelsAction.UpdateText(LocalModelTextField.REMOTE_BASE_URL, it))
                        },
                    )
                    ModelTextField(
                        label = "Model name",
                        value = state.remoteServer.modelName,
                        onValueChange = {
                            onAction(LocalModelsAction.UpdateText(LocalModelTextField.REMOTE_MODEL_NAME, it))
                        },
                    )
                    ModelTextField(
                        label = "API key (optional for local servers)",
                        value = state.remoteServer.apiKey,
                        password = true,
                        onValueChange = {
                            onAction(LocalModelsAction.UpdateText(LocalModelTextField.REMOTE_API_KEY, it))
                        },
                    )
                    ActionRow(
                        primaryLabel = "Test connection",
                        onPrimary = { onAction(LocalModelsAction.TestRemoteServer) },
                        secondaryLabel = "Save",
                        onSecondary = { onAction(LocalModelsAction.SaveRemoteServer) },
                    )
                    if (state.remoteServer.status.isNotBlank()) {
                        Text(
                            state.remoteServer.status,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                ExpandableCard(
                    title = "Studio Bridge",
                    expanded = state.studioBridgeExpanded,
                    onToggle = { onAction(LocalModelsAction.ToggleSection(LocalModelsSection.STUDIO_BRIDGE)) },
                ) {
                    Text(
                        "Receive CyanBridge Model Studio approval prompts over Tailscale and respond by voice.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ToggleRow(
                        label = "Enable Studio Bridge",
                        checked = state.studioBridge.enabled,
                        onCheckedChange = {
                            onAction(
                                LocalModelsAction.SetToggle(
                                    LocalModelToggleField.STUDIO_BRIDGE_ENABLED,
                                    it,
                                ),
                            )
                        },
                    )
                    ModelTextField(
                        label = "API key (same as Remote Server)",
                        value = state.studioBridge.apiKey,
                        password = true,
                        onValueChange = {
                            onAction(LocalModelsAction.UpdateText(LocalModelTextField.STUDIO_BRIDGE_API_KEY, it))
                        },
                    )
                    ActionRow(
                        primaryLabel = "Save and connect",
                        onPrimary = { onAction(LocalModelsAction.SaveStudioBridge) },
                        secondaryLabel = "API key help",
                        onSecondary = { onAction(LocalModelsAction.ShowStudioBridgeApiKeyHelp) },
                    )
                    if (state.studioBridge.status.isNotBlank()) {
                        Text(
                            state.studioBridge.status,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                ExpandableCard(
                    title = "Generation settings",
                    expanded = state.generationSettingsExpanded,
                    onToggle = { onAction(LocalModelsAction.ToggleSection(LocalModelsSection.GENERATION_SETTINGS)) },
                ) {
                    val generation = state.generation
                    ChoiceField(
                        label = "Performance profile",
                        value = generation.profileOptions.getOrNull(generation.profileIndex).orEmpty(),
                        options = generation.profileOptions,
                        onSelected = {
                            onAction(LocalModelsAction.SelectOption(LocalModelOptionField.PROFILE, it))
                        },
                    )
                    ChoiceField(
                        label = "Model runtime",
                        value = generation.runtimeOptions.getOrNull(generation.runtimeIndex).orEmpty(),
                        options = generation.runtimeOptions,
                        onSelected = {
                            onAction(LocalModelsAction.SelectOption(LocalModelOptionField.RUNTIME, it))
                        },
                    )
                    SupportingText(generation.runtimeNote)
                    ChoiceField(
                        label = "Compute backend",
                        value = generation.computeBackendOptions.getOrNull(generation.computeBackendIndex).orEmpty(),
                        options = generation.computeBackendOptions,
                        onSelected = {
                            onAction(LocalModelsAction.SelectOption(LocalModelOptionField.COMPUTE_BACKEND, it))
                        },
                    )
                    ModelTextField(
                        label = "CPU threads (1-16)",
                        value = generation.cpuThreads,
                        onValueChange = {
                            onAction(LocalModelsAction.UpdateText(LocalModelTextField.CPU_THREADS, it))
                        },
                    )
                    ModelTextField(
                        label = "GPU layers (-1 to 999)",
                        value = generation.gpuLayers,
                        enabled = generation.gpuLayersEnabled,
                        onValueChange = {
                            onAction(LocalModelsAction.UpdateText(LocalModelTextField.GPU_LAYERS, it))
                        },
                    )
                    SupportingText(generation.computeBackendNote)
                    ModelTextField(
                        label = "Temperature (0-2)",
                        value = generation.temperature,
                        onValueChange = {
                            onAction(LocalModelsAction.UpdateText(LocalModelTextField.TEMPERATURE, it))
                        },
                    )
                    ModelTextField(
                        label = "Top-p (0-1)",
                        value = generation.topP,
                        onValueChange = {
                            onAction(LocalModelsAction.UpdateText(LocalModelTextField.TOP_P, it))
                        },
                    )
                    ModelTextField(
                        label = "Top-k",
                        value = generation.topK,
                        onValueChange = {
                            onAction(LocalModelsAction.UpdateText(LocalModelTextField.TOP_K, it))
                        },
                    )
                    ModelTextField(
                        label = "Max output tokens (32-8192)",
                        value = generation.maxTokens,
                        onValueChange = {
                            onAction(LocalModelsAction.UpdateText(LocalModelTextField.MAX_TOKENS, it))
                        },
                    )
                    ModelTextField(
                        label = "Repetition penalty",
                        value = generation.repetitionPenalty,
                        onValueChange = {
                            onAction(LocalModelsAction.UpdateText(LocalModelTextField.REPETITION_PENALTY, it))
                        },
                    )
                    ModelTextField(
                        label = "Context size (1024-32768)",
                        value = generation.contextSize,
                        onValueChange = {
                            onAction(LocalModelsAction.UpdateText(LocalModelTextField.CONTEXT_SIZE, it))
                        },
                    )
                    ModelTextField(
                        label = "Seed (-1 is random)",
                        value = generation.seed,
                        onValueChange = {
                            onAction(LocalModelsAction.UpdateText(LocalModelTextField.SEED, it))
                        },
                    )
                    ChoiceField(
                        label = "Prompt template override",
                        value = generation.templateOptions.getOrNull(generation.templateIndex).orEmpty(),
                        options = generation.templateOptions,
                        onSelected = {
                            onAction(LocalModelsAction.SelectOption(LocalModelOptionField.TEMPLATE, it))
                        },
                    )
                    ToggleRow(
                        label = "Experimental structured JSON mode",
                        checked = generation.experimentalStructuredJson,
                        onCheckedChange = {
                            onAction(
                                LocalModelsAction.SetToggle(
                                    LocalModelToggleField.EXPERIMENTAL_STRUCTURED_JSON,
                                    it,
                                ),
                            )
                        },
                    )
                    ModelTextField(
                        label = "System prompt override (optional)",
                        value = generation.systemPrompt,
                        minLines = 3,
                        onValueChange = {
                            onAction(LocalModelsAction.UpdateText(LocalModelTextField.SYSTEM_PROMPT, it))
                        },
                    )
                    ModelTextField(
                        label = "Hugging Face token (for gated sources)",
                        value = generation.huggingFaceToken,
                        password = true,
                        onValueChange = {
                            onAction(LocalModelsAction.UpdateText(LocalModelTextField.HUGGING_FACE_TOKEN, it))
                        },
                    )
                }
            }
            item {
                ScreenCard("Diagnostics") {
                    OutlinedButton(
                        onClick = { onAction(LocalModelsAction.RunWarmup) },
                        enabled = state.selectedInstalledModelId != null && !state.download.isInFlight,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Run warm-up probe")
                    }
                    if (state.warmupResult.isNotBlank()) {
                        Text(
                            state.warmupResult,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(onClick = requestBack) { Text("Close") }
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(onClick = { onAction(LocalModelsAction.SaveGenerationSettings) }) {
                        Text("Save")
                    }
                }
            }
        }
    }

    if (showUnsavedChangesDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedChangesDialog = false },
            title = { Text("Unsaved changes") },
            text = { Text("Your local-model settings have changed. Leave without saving?") },
            dismissButton = {
                TextButton(onClick = { showUnsavedChangesDialog = false }) {
                    Text("Keep editing")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUnsavedChangesDialog = false
                        onAction(LocalModelsAction.DiscardChangesAndBack)
                    },
                ) {
                    Text("Discard")
                }
            },
        )
    }
}

@Composable
private fun DownloadProgressCard(
    state: LocalModelDownloadUiState,
    onAction: (LocalModelsAction) -> Unit,
) {
    ScreenCard("Download progress") {
        if (state.message.isNotBlank()) {
            Text(
                state.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("local_model_download_message"),
            )
        }
        if (state.isInFlight || state.progressPercent != null) {
            state.progressPercent?.let { percent ->
                LinearProgressIndicator(
                    progress = { percent.coerceIn(0, 100) / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("local_model_download_progress"),
                )
            } ?: LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("local_model_download_progress"),
            )
        }
        if (state.isInFlight) {
            OutlinedButton(
                onClick = { onAction(LocalModelsAction.CancelDownload) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Cancel download")
            }
        }
    }
}

@Composable
private fun ScreenCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                content()
            },
        )
    }
}

@Composable
private fun ExpandableCard(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandMore else Icons.Outlined.ChevronRight,
                    contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                )
            }
            if (expanded) content()
        }
    }
}

@Composable
private fun ActionRow(
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String,
    onSecondary: () -> Unit,
    enabled: Boolean = true,
    secondaryEnabled: Boolean = enabled,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilledTonalButton(
            onClick = onPrimary,
            enabled = enabled,
            modifier = Modifier.weight(1f),
        ) {
            Text(primaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        OutlinedButton(
            onClick = onSecondary,
            enabled = secondaryEnabled,
            modifier = Modifier.weight(1f),
        ) {
            Text(secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ModelTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean = true,
    password: Boolean = false,
    minLines: Int = 1,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        enabled = enabled,
        singleLine = minLines == 1,
        minLines = minLines,
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
    )
}

@Composable
private fun SupportingText(value: String) {
    if (value.isNotBlank()) {
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ChoiceField(
    label: String,
    value: String,
    options: List<String>,
    onSelected: (Int) -> Unit,
) {
    var showChoices by remember(label, options) { mutableStateOf(false) }
    OutlinedButton(
        onClick = { showChoices = true },
        enabled = options.isNotEmpty(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(value.ifBlank { "Select" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    if (showChoices) {
        AlertDialog(
            onDismissRequest = { showChoices = false },
            title = { Text(label) },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    itemsIndexed(options) { index, option ->
                        TextButton(
                            onClick = {
                                onSelected(index)
                                showChoices = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(option, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showChoices = false }) { Text("Close") }
            },
        )
    }
}

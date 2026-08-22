package com.ad_glasses.ui.adglasses

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.ad_glasses.shared.localmodels.InstalledModelUiItem
import com.ad_glasses.shared.localmodels.LocalModelCatalogUiItem
import com.ad_glasses.shared.localmodels.LocalModelGenerationUiState
import com.ad_glasses.shared.localmodels.LocalModelOptionField
import com.ad_glasses.shared.localmodels.LocalModelTextField
import com.ad_glasses.shared.localmodels.LocalModelToggleField
import com.ad_glasses.shared.localmodels.LocalModelsAction
import com.ad_glasses.shared.localmodels.LocalModelsConfigureUiState
import com.ad_glasses.shared.localmodels.LocalModelsSection

/** AD Glasses presentation for the Android-owned local-model controller. */
@Composable
internal fun ADLocalModelsConfigureScreen(
    state: LocalModelsConfigureUiState,
    onAction: (LocalModelsAction) -> Unit,
) {
    var showUnsavedDialog by rememberSaveable { mutableStateOf(false) }
    val requestBack = {
        if (state.hasUnsavedChanges) showUnsavedDialog = true else onAction(LocalModelsAction.Back)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ADColors.Background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("local_models_configure"),
    ) {
        // The hero below already communicates the page identity and readiness state.
        ADTopBar(showBack = true, onBack = requestBack)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            item { ADLocalRuntimeHero(state, onAction) }

            if (state.download.isInFlight || state.download.message.isNotBlank()) {
                item { ADLocalDownloadCard(state, onAction) }
            }

            item {
                ADSectionTitle("Installed")
                Spacer(Modifier.height(5.dp))
                ADCard {
                    if (state.installedModels.isEmpty()) {
                        Text("No model is stored on this phone", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "Download the recommended model below or import a compatible GGUF or LiteRT file.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ADColors.Muted,
                        )
                    } else {
                        state.installedModels.forEachIndexed { index, model ->
                            ADInstalledModelChoice(
                                model = model,
                                selected = model.id == state.selectedInstalledModelId,
                                onClick = { onAction(LocalModelsAction.SelectInstalledModel(model.id)) },
                            )
                            if (index != state.installedModels.lastIndex) {
                                HorizontalDivider(color = ADColors.Separator)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            OutlinedButton(
                                onClick = { onAction(LocalModelsAction.ShowSelectedModelInfo) },
                                modifier = Modifier.weight(1f),
                            ) { Text("Model info") }
                            OutlinedButton(
                                onClick = { onAction(LocalModelsAction.UnloadSelectedModel) },
                                modifier = Modifier.weight(1f),
                            ) { Text("Unload") }
                        }
                        TextButton(
                            onClick = { onAction(LocalModelsAction.RemoveSelectedModel) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Remove selected model", color = ADColors.Error) }
                    }
                }
            }

            item {
                ADExpandableModelCard(
                    title = "Recommended models",
                    subtitle = "Only models suitable for this phone can be downloaded",
                    expanded = state.catalogExpanded,
                    onToggle = { onAction(LocalModelsAction.ToggleSection(LocalModelsSection.CATALOG)) },
                ) {
                    val ordered = state.catalog.sortedByDescending { it.id == STARTER_MODEL_ID }
                    ordered.forEachIndexed { index, model ->
                        if (index > 0) HorizontalDivider(color = ADColors.Separator)
                        ADCatalogModel(model, model.id == STARTER_MODEL_ID, onAction)
                    }
                }
            }

            item {
                ADExpandableModelCard(
                    title = "Performance",
                    subtitle = "Runtime, speed and response controls",
                    expanded = state.generationSettingsExpanded,
                    onToggle = {
                        onAction(LocalModelsAction.ToggleSection(LocalModelsSection.GENERATION_SETTINGS))
                    },
                ) {
                    ADGenerationControls(state.generation, onAction)
                    Button(
                        onClick = { onAction(LocalModelsAction.SaveGenerationSettings) },
                        enabled = state.selectedInstalledModelId != null,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ADColors.Ink),
                    ) { Text("Save performance settings") }
                }
            }

            item {
                ADCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CheckCircle, null, tint = ADColors.Blue, modifier = Modifier.size(20.dp))
                        Column(Modifier.padding(start = 8.dp).weight(1f)) {
                            Text("Runtime check", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Loads the selected model and measures a short reply on this phone.",
                                style = MaterialTheme.typography.bodySmall,
                                color = ADColors.Muted,
                            )
                        }
                    }
                    Spacer(Modifier.height(9.dp))
                    OutlinedButton(
                        onClick = { onAction(LocalModelsAction.RunWarmup) },
                        enabled = state.selectedInstalledModelId != null && !state.download.isInFlight,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (state.warmupResult.startsWith("Running")) "Checking…" else "Run model check") }
                    if (state.warmupResult.isNotBlank()) {
                        Spacer(Modifier.height(5.dp))
                        Text(state.warmupResult, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
                    }
                }
            }

            item {
                OutlinedButton(onClick = requestBack, modifier = Modifier.fillMaxWidth()) {
                    Text("Done")
                }
            }
        }
    }

    if (showUnsavedDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedDialog = false },
            title = { Text("Unsaved performance settings") },
            text = { Text("Keep editing, or leave without applying these tuning changes?") },
            dismissButton = {
                TextButton(onClick = { showUnsavedDialog = false }) { Text("Keep editing") }
            },
            confirmButton = {
                TextButton(onClick = { onAction(LocalModelsAction.DiscardChangesAndBack) }) {
                    Text("Discard")
                }
            },
        )
    }
}

@Composable
private fun ADLocalRuntimeHero(
    state: LocalModelsConfigureUiState,
    onAction: (LocalModelsAction) -> Unit,
) {
    val ready = state.selectedInstalledModelId != null && state.selectedModelStatus.contains("ready", true)
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = ADColors.Ink,
        contentColor = ADColors.Surface,
        shadowElevation = 2.dp,
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = ADColors.Surface.copy(alpha = 0.14f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Memory, null, modifier = Modifier.size(22.dp))
                    }
                }
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Text(
                        if (ready) "Local AI ready" else "Private AI on this phone",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        if (ready) state.selectedModelStatus.substringAfter('|', state.selectedModelStatus)
                        else "No account, relay or API key required",
                        style = MaterialTheme.typography.bodySmall,
                        color = ADColors.Surface.copy(alpha = 0.68f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(11.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ADHeroChip("On-device", Modifier.weight(1f))
                ADHeroChip("AD voice", Modifier.weight(1f))
                ADHeroChip("Offline", Modifier.weight(1f))
            }
            if (state.deviceSummary.isNotBlank()) {
                Spacer(Modifier.height(9.dp))
                Text(
                    state.deviceSummary,
                    style = MaterialTheme.typography.labelSmall,
                    color = ADColors.Surface.copy(alpha = 0.62f),
                )
            }
            Spacer(Modifier.height(11.dp))
            if (state.installedModels.isEmpty()) {
                val starter = state.catalog.firstOrNull { it.id == STARTER_MODEL_ID }
                Button(
                    onClick = {
                        starter?.let { onAction(LocalModelsAction.DownloadCatalogModel(it.id)) }
                    },
                    enabled = starter?.canDownload == true && !state.download.isInFlight,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ADColors.Surface,
                        contentColor = ADColors.Ink,
                        disabledContainerColor = ADColors.Surface.copy(alpha = 0.18f),
                        disabledContentColor = ADColors.Surface.copy(alpha = 0.46f),
                    ),
                ) {
                    Icon(Icons.Outlined.Download, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(7.dp))
                    Text("Download Qwen starter · about 400 MB")
                }
            } else {
                OutlinedButton(
                    onClick = { onAction(LocalModelsAction.ImportModel) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                    border = BorderStroke(1.dp, ADColors.Surface.copy(alpha = 0.32f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ADColors.Surface),
                ) { Text("Import another model") }
            }
        }
    }
}

@Composable
private fun ADHeroChip(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = ADColors.Surface.copy(alpha = 0.12f),
        contentColor = ADColors.Surface,
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun ADLocalDownloadCard(
    state: LocalModelsConfigureUiState,
    onAction: (LocalModelsAction) -> Unit,
) {
    ADCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Download, null, tint = ADColors.Blue, modifier = Modifier.size(20.dp))
            Column(Modifier.padding(start = 8.dp).weight(1f)) {
                Text(if (state.download.isInFlight) "Downloading model" else "Model status", style = MaterialTheme.typography.titleMedium)
                Text(state.download.message, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
            }
            state.download.progressPercent?.let {
                ADStatusChip("$it%", if (it >= 100) ADStatusTone.SUCCESS else ADStatusTone.INFO)
            }
        }
        state.download.progressPercent?.let {
            Spacer(Modifier.height(9.dp))
            LinearProgressIndicator(
                progress = { it / 100f },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = ADColors.Ink,
                trackColor = ADColors.SurfaceSubtle,
            )
        }
        if (state.download.isInFlight) {
            TextButton(
                onClick = { onAction(LocalModelsAction.CancelDownload) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Cancel download", color = ADColors.Error) }
        }
    }
}

@Composable
private fun ADInstalledModelChoice(
    model: InstalledModelUiItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(34.dp).background(
                if (selected) ADColors.Ink else ADColors.SurfaceSubtle,
                RoundedCornerShape(10.dp),
            ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (selected) Icons.Outlined.CheckCircle else Icons.Outlined.Storage,
                null,
                tint = if (selected) ADColors.Surface else ADColors.Ink,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            model.label,
            modifier = Modifier.padding(start = 9.dp).weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        )
        if (selected) ADStatusChip("Selected", ADStatusTone.SUCCESS, showCheck = true)
    }
}

@Composable
private fun ADExpandableModelCard(
    title: String,
    subtitle: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    ADCard {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
            }
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                tint = ADColors.Muted,
                modifier = Modifier.size(21.dp),
            )
        }
        if (expanded) {
            Spacer(Modifier.height(9.dp))
            HorizontalDivider(color = ADColors.Separator)
            Spacer(Modifier.height(7.dp))
            Column(verticalArrangement = Arrangement.spacedBy(9.dp), content = content)
        }
    }
}

@Composable
private fun ADCatalogModel(
    model: LocalModelCatalogUiItem,
    recommended: Boolean,
    onAction: (LocalModelsAction) -> Unit,
) {
    Column(Modifier.padding(vertical = 7.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(model.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (recommended) ADStatusChip("Best for this phone", ADStatusTone.INFO)
        }
        Text(model.details, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
        Text(model.status, style = MaterialTheme.typography.labelSmall, color = ADColors.Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Button(
                onClick = { onAction(LocalModelsAction.DownloadCatalogModel(model.id)) },
                enabled = model.canDownload,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = ADColors.Ink),
            ) { Text(model.downloadLabel) }
            OutlinedButton(
                onClick = { onAction(LocalModelsAction.ShowCatalogModelInfo(model.id)) },
                modifier = Modifier.weight(0.62f),
            ) { Text("Details") }
        }
    }
}

@Composable
private fun ADGenerationControls(
    generation: LocalModelGenerationUiState,
    onAction: (LocalModelsAction) -> Unit,
) {
    ADOptionField("Profile", generation.profileOptions, generation.profileIndex) {
        onAction(LocalModelsAction.SelectOption(LocalModelOptionField.PROFILE, it))
    }
    ADOptionField("Runtime", generation.runtimeOptions, generation.runtimeIndex) {
        onAction(LocalModelsAction.SelectOption(LocalModelOptionField.RUNTIME, it))
    }
    if (generation.runtimeNote.isNotBlank()) {
        Text(generation.runtimeNote, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
    }
    ADOptionField("Compute", generation.computeBackendOptions, generation.computeBackendIndex) {
        onAction(LocalModelsAction.SelectOption(LocalModelOptionField.COMPUTE_BACKEND, it))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        ADModelTextField(
            label = "CPU threads",
            value = generation.cpuThreads,
            modifier = Modifier.weight(1f),
        ) { onAction(LocalModelsAction.UpdateText(LocalModelTextField.CPU_THREADS, it)) }
        ADModelTextField(
            label = "GPU layers",
            value = generation.gpuLayers,
            enabled = generation.gpuLayersEnabled,
            modifier = Modifier.weight(1f),
        ) { onAction(LocalModelsAction.UpdateText(LocalModelTextField.GPU_LAYERS, it)) }
    }
    if (generation.computeBackendNote.isNotBlank()) {
        Text(generation.computeBackendNote, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        ADModelTextField("Temperature", generation.temperature, modifier = Modifier.weight(1f)) {
            onAction(LocalModelsAction.UpdateText(LocalModelTextField.TEMPERATURE, it))
        }
        ADModelTextField("Top P", generation.topP, modifier = Modifier.weight(1f)) {
            onAction(LocalModelsAction.UpdateText(LocalModelTextField.TOP_P, it))
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        ADModelTextField("Max answer tokens", generation.maxTokens, modifier = Modifier.weight(1f)) {
            onAction(LocalModelsAction.UpdateText(LocalModelTextField.MAX_TOKENS, it))
        }
        ADModelTextField("Context tokens", generation.contextSize, modifier = Modifier.weight(1f)) {
            onAction(LocalModelsAction.UpdateText(LocalModelTextField.CONTEXT_SIZE, it))
        }
    }
    ADOptionField("Prompt template", generation.templateOptions, generation.templateIndex) {
        onAction(LocalModelsAction.SelectOption(LocalModelOptionField.TEMPLATE, it))
    }
    ADToggleLine("Structured JSON (experimental)", generation.experimentalStructuredJson) {
        onAction(LocalModelsAction.SetToggle(LocalModelToggleField.EXPERIMENTAL_STRUCTURED_JSON, it))
    }
    ADModelTextField(
        label = "System instructions (optional)",
        value = generation.systemPrompt,
        placeholder = "Leave blank to use AD defaults",
        minLines = 3,
    ) { onAction(LocalModelsAction.UpdateText(LocalModelTextField.SYSTEM_PROMPT, it)) }
}

@Composable
private fun ADOptionField(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
) {
    var choosing by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = ADColors.Muted)
        Surface(
            onClick = { if (options.isNotEmpty()) choosing = true },
            shape = RoundedCornerShape(12.dp),
            color = ADColors.SurfaceSubtle,
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(options.getOrNull(selectedIndex).orEmpty(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = ADColors.Muted, modifier = Modifier.size(19.dp))
            }
        }
    }
    if (choosing) {
        AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text(label) },
            text = {
                Column {
                    options.forEachIndexed { index, option ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                onSelected(index)
                                choosing = false
                            }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(option, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            if (index == selectedIndex) {
                                Icon(Icons.Outlined.CheckCircle, null, tint = ADColors.Success, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosing = false }) { Text("Close") } },
        )
    }
}

@Composable
private fun ADModelTextField(
    label: String,
    value: String,
    placeholder: String = "",
    password: Boolean = false,
    enabled: Boolean = true,
    minLines: Int = 1,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { if (placeholder.isNotBlank()) Text(placeholder) },
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        enabled = enabled,
        minLines = minLines,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        textStyle = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun ADToggleLine(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private const val STARTER_MODEL_ID = "qwen2.5-0.5b-instruct-q4"

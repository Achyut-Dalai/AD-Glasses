package com.fersaiyan.cyanbridge.shared.ui.glasses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fersaiyan.cyanbridge.shared.glasses.GlassesAssistantMode
import com.fersaiyan.cyanbridge.shared.glasses.GlassesDashboardAction
import com.fersaiyan.cyanbridge.shared.glasses.GlassesDashboardUiState
import com.fersaiyan.cyanbridge.shared.glasses.FirmwarePatchRequestUiState
import com.fersaiyan.cyanbridge.shared.plugins.NativePluginShortcutAction
import com.fersaiyan.cyanbridge.shared.plugins.NativePluginShortcutUiState
import com.fersaiyan.cyanbridge.shared.glasses.MetaRaybanUiState
import com.fersaiyan.cyanbridge.shared.glasses.OtaFirmwareSource
import com.fersaiyan.cyanbridge.shared.glasses.OtaSectionUiState
import com.fersaiyan.cyanbridge.shared.glasses.OtaTargetSelection
import com.fersaiyan.cyanbridge.shared.glasses.LivePreviewUiState
import com.fersaiyan.cyanbridge.shared.glasses.WifiAdbDebugUiState
import com.fersaiyan.cyanbridge.shared.navigation.AppDestination

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassesDashboardScreen(
    state: GlassesDashboardUiState,
    onAction: (GlassesDashboardAction) -> Unit,
) {
    var showWifiAdbConfirmation by remember { mutableStateOf(false) }
    var wifiAdbRiskAcknowledged by remember { mutableStateOf(false) }
    var showOtaFirmwareSourcePicker by remember { mutableStateOf(false) }
    var otaFirmwareRiskAcknowledged by remember { mutableStateOf(false) }

    if (showWifiAdbConfirmation) {
        AlertDialog(
            onDismissRequest = {
                showWifiAdbConfirmation = false
                wifiAdbRiskAcknowledged = false
            },
            title = { Text("Privileged ADB risk") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Privileged ADB can brick or erase the glasses. Use only an isolated, " +
                            "recoverable lab device and network. Force-stop the official HeyCyan app first. " +
                            "Stop media sync, OTA, live preview, recording, and automatic capture before continuing. " +
                            "This session is not persistent and stops when this screen leaves the foreground."
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = wifiAdbRiskAcknowledged,
                            onCheckedChange = { wifiAdbRiskAcknowledged = it },
                            modifier = Modifier.testTag("wifi_adb_risk_acknowledgement"),
                        )
                        Text("I explicitly accept the destructive risk and lab-only restrictions.")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = wifiAdbRiskAcknowledged,
                    modifier = Modifier.testTag("wifi_adb_confirm_start"),
                    onClick = {
                        showWifiAdbConfirmation = false
                        wifiAdbRiskAcknowledged = false
                        onAction(GlassesDashboardAction.RequestStartWifiAdbDebug)
                    },
                ) { Text("Start privileged ADB") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showWifiAdbConfirmation = false
                        wifiAdbRiskAcknowledged = false
                    },
                ) { Text("Cancel") }
            },
        )
    }

    if (showOtaFirmwareSourcePicker) {
        OtaFirmwareSourcePickerDialog(
            target = state.ota.selectedTarget,
            riskAcknowledged = otaFirmwareRiskAcknowledged,
            onRiskAcknowledgedChange = { otaFirmwareRiskAcknowledged = it },
            onDismissRequest = {
                showOtaFirmwareSourcePicker = false
                otaFirmwareRiskAcknowledged = false
            },
            onSourceSelected = { source ->
                showOtaFirmwareSourcePicker = false
                otaFirmwareRiskAcknowledged = false
                onAction(GlassesDashboardAction.RequestOtaFirmware(source))
            },
        )
    }

    state.firmwarePatchRequest?.let { request ->
        FirmwarePatchRequestDialog(
            request = request,
            onDismissRequest = {
                if (!request.isSubmitting) {
                    onAction(GlassesDashboardAction.DismissFirmwarePatchRequest)
                }
            },
            onSubmit = { contactEmail ->
                onAction(GlassesDashboardAction.SubmitFirmwarePatchRequest(contactEmail))
            },
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = { TopAppBar(title = { Text("Glasses") }) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .testTag("glasses_dashboard"),
            contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.meeting.isRecording) {
                item {
                    MeetingBanner(
                        label = state.meeting.bannerLabel.ifBlank { "Recording active" },
                        onStop = { onAction(GlassesDashboardAction.StopMeetingCapture) },
                    )
                }
            }
            item { StatusCard(state) }
            if (state.transfer.isVisible) {
                item {
                    TransferCard(
                        state = state,
                        onStop = { onAction(GlassesDashboardAction.StopSync) },
                    )
                }
            }
            item {
                SectionTitle("Connection")
                ActionRow(
                    primaryLabel = "Scan",
                    onPrimary = { onAction(GlassesDashboardAction.Scan) },
                    secondaryLabel = "Reconnect",
                    onSecondary = { onAction(GlassesDashboardAction.Reconnect) },
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onAction(GlassesDashboardAction.Disconnect) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Disconnect", color = MaterialTheme.colorScheme.error)
                }
            }
            item {
                NativePluginShortcutSection(
                    shortcut = state.nativePluginShortcut,
                    onAction = onAction,
                )
            }
            if (state.showHeyCyanControls) {
                item { CoreGlassesControls(state, onAction) }
            }
            if (state.showMetaRaybanControls) {
                item { MetaRaybanControls(state.metaRayban, onAction) }
                item { GlassesAssistantControls(state, onAction) }
            }
            if (state.wifiAdbDebug.isAvailable) {
                item {
                    WifiAdbDebugSection(
                        state = state.wifiAdbDebug,
                        onRequestStart = { showWifiAdbConfirmation = true },
                        onStop = { onAction(GlassesDashboardAction.StopWifiAdbDebug) },
                    )
                }
            }
            if (state.showHeyCyanControls) {
                item {
                    TextButton(
                        onClick = { onAction(GlassesDashboardAction.ToggleAdvanced) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (state.advancedExpanded) "Hide advanced controls" else "Show advanced controls")
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = if (state.advancedExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = null,
                        )
                    }
                }
                if (state.advancedExpanded) {
                    item {
                        AdvancedControls(
                            state = state,
                            onAction = onAction,
                            onRequestOtaFirmware = { showOtaFirmwareSourcePicker = true },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WifiAdbDebugSection(
    state: WifiAdbDebugUiState,
    onRequestStart: () -> Unit,
    onStop: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("wifi_adb_debug_section"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionTitle("Developer tools", accented = true)
            Text("ADB over glasses Wi-Fi Direct", style = MaterialTheme.typography.titleMedium)
            Text("Status: ${state.stateLabel}", style = MaterialTheme.typography.bodyMedium)
            if (state.detail.isNotBlank()) {
                Text(
                    state.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.glassesIp?.let { Text("Glasses IP: $it", style = MaterialTheme.typography.bodySmall) }
            if (state.relayEndpoints.isNotEmpty()) {
                Text(
                    "Relay endpoints: ${state.relayEndpoints.joinToString()}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (state.preferredCommand.isNotBlank()) {
                Text(state.preferredCommand, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "USB tethering alone is unreliable. Prefer phone USB debugging with adb forward.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            ActionRow(
                primaryLabel = "Start ADB relay",
                onPrimary = onRequestStart,
                primaryEnabled = state.canStart,
                secondaryLabel = "Stop",
                onSecondary = onStop,
                secondaryEnabled = state.canStop,
            )
        }
    }
}

@Composable
private fun NativePluginShortcutSection(
    shortcut: NativePluginShortcutUiState?,
    onAction: (GlassesDashboardAction) -> Unit,
) {
    if (shortcut == null) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("native_plugin_shortcut_empty"),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Glasses tab shortcut",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Choose a native plugin in its settings to place its actions here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = { onAction(GlassesDashboardAction.Navigate(AppDestination.PLUGINS)) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Choose a plugin")
                }
            }
        }
        return
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("native_plugin_shortcut_${shortcut.id}"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${shortcut.title} shortcuts",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = shortcut.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Surface(
                    color = if (shortcut.isEnabled) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    contentColor = if (shortcut.isEnabled) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        text = if (shortcut.isEnabled) "Enabled" else "Stopped",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            shortcut.buttons.chunked(2).forEach { rowButtons ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowButtons.forEach { button ->
                        val buttonModifier = Modifier.weight(1f)
                        if (button.action == NativePluginShortcutAction.START) {
                            FilledTonalButton(
                                onClick = {
                                    onAction(GlassesDashboardAction.RunNativePluginShortcut(button.action))
                                },
                                modifier = buttonModifier,
                            ) {
                                Text(button.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    onAction(GlassesDashboardAction.RunNativePluginShortcut(button.action))
                                },
                                modifier = buttonModifier,
                            ) {
                                Text(button.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    if (rowButtons.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun MeetingBanner(label: String, onStop: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 10.dp, end = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.error,
            )
            TextButton(onClick = onStop) { Text("Stop") }
        }
    }
}

@Composable
private fun StatusCard(state: GlassesDashboardUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "Glasses status",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(state.connectionLabel, style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "Class: ${state.deviceClassLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.showBattery || state.showStorage) {
                    Column(horizontalAlignment = Alignment.End) {
                        if (state.showBattery) {
                            Text(
                                text = state.batteryPercent?.let { "Battery: $it%" } ?: "Battery: --%",
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                        if (state.showStorage) {
                            Text(
                                text = "Storage: ${state.storageLabel}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransferCard(
    state: GlassesDashboardUiState,
    onStop: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Sync progress", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(
                text = "Flow: ${state.transfer.flowLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(state.transfer.countsLabel, style = MaterialTheme.typography.bodySmall)
            state.transfer.progress?.let { progress ->
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            } ?: LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = state.transfer.detail,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                TextButton(onClick = onStop) { Text("Stop sync") }
            }
        }
    }
}

@Composable
private fun CoreGlassesControls(
    state: GlassesDashboardUiState,
    onAction: (GlassesDashboardAction) -> Unit,
) {
    Column(
        modifier = Modifier.testTag("glasses_core_controls"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GlassesAssistantControls(state, onAction)
        Spacer(Modifier.height(8.dp))
        SectionTitle("Media controls")
        ActionRow(
            primaryLabel = "Photo",
            onPrimary = { onAction(GlassesDashboardAction.CapturePhoto) },
            secondaryLabel = "Video",
            onSecondary = { onAction(GlassesDashboardAction.ToggleVideo) },
        )
        ActionRow(
            primaryLabel = "Audio",
            onPrimary = { onAction(GlassesDashboardAction.StartAudioRecording) },
            secondaryLabel = "Count",
            onSecondary = { onAction(GlassesDashboardAction.RequestMediaCount) },
        )
        FilledTonalButton(
            onClick = { onAction(GlassesDashboardAction.StartSync) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Sync data (P2P)") }
        if (state.livePreview.isAvailable) {
            Spacer(Modifier.height(8.dp))
            SectionTitle("Passive RTSP lab probe")
            Text(
                text = "Sends no BLE mode command. Activate mode 8 separately using the approved hardware procedure.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.livePreview.stateLabel != "Idle") {
                Text(
                    text = "Status: ${state.livePreview.stateLabel}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (state.livePreview.detail.isNotBlank()) {
                    Text(
                        text = state.livePreview.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            ActionRow(
                primaryLabel = if (state.livePreview.isScanning) "Scanning..." else "Arm passive probe",
                onPrimary = { onAction(GlassesDashboardAction.StartLivePreview) },
                primaryEnabled = state.livePreview.canStart && !state.livePreview.isScanning,
                secondaryLabel = "Stop",
                onSecondary = { onAction(GlassesDashboardAction.StopLivePreview) },
                secondaryEnabled = state.livePreview.canStop,
            )
        }
    }
}

@Composable
private fun GlassesAssistantControls(
    state: GlassesDashboardUiState,
    onAction: (GlassesDashboardAction) -> Unit,
) {
    Column(
        modifier = Modifier.testTag("glasses_assistant_controls"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionTitle("AI assistant", accented = true)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AssistantModeChip(
                label = "Gemini",
                mode = GlassesAssistantMode.GEMINI,
                selectedMode = state.assistantMode,
                onAction = onAction,
                modifier = Modifier.weight(1f),
            )
            AssistantModeChip(
                label = "ChatGPT",
                mode = GlassesAssistantMode.CHAT_GPT,
                selectedMode = state.assistantMode,
                onAction = onAction,
                modifier = Modifier.weight(1f),
            )
            AssistantModeChip(
                label = "Custom Provider",
                mode = GlassesAssistantMode.CHOSEN_PROVIDER,
                selectedMode = state.assistantMode,
                onAction = onAction,
                modifier = Modifier.weight(1f),
            )
        }
        ActionRow(
            primaryLabel = "Test voice",
            onPrimary = { onAction(GlassesDashboardAction.TestVoiceQuestion) },
            secondaryLabel = state.imageQueryLabel,
            onSecondary = { onAction(GlassesDashboardAction.TestImageQuestion) },
            secondaryEnabled = state.imageQueryEnabled,
        )
    }
}

@Composable
private fun AssistantModeChip(
    label: String,
    mode: GlassesAssistantMode,
    selectedMode: GlassesAssistantMode,
    onAction: (GlassesDashboardAction) -> Unit,
    modifier: Modifier,
) {
    FilterChip(
        selected = selectedMode == mode,
        onClick = { onAction(GlassesDashboardAction.SelectAssistantMode(mode)) },
        label = { Text(label) },
        modifier = modifier,
    )
}

@Composable
private fun MetaRaybanControls(
    state: MetaRaybanUiState,
    onAction: (GlassesDashboardAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Meta Ray-Ban", accented = true)
        MetaControlRow(
            status = "Registration: ${state.registrationLabel}",
            startLabel = "Register",
            onStart = { onAction(GlassesDashboardAction.MetaRegister) },
            startEnabled = state.canRegister,
            stopLabel = "Unregister",
            onStop = { onAction(GlassesDashboardAction.MetaUnregister) },
            stopEnabled = state.canUnregister,
        )
        MetaControlRow(
            status = "Session: ${state.sessionLabel}",
            startLabel = "Start session",
            onStart = { onAction(GlassesDashboardAction.MetaStartSession) },
            startEnabled = state.canStartSession,
            stopLabel = "Stop session",
            onStop = { onAction(GlassesDashboardAction.MetaStopSession) },
            stopEnabled = state.canStopSession,
        )
        MetaControlRow(
            status = "Stream: ${state.streamLabel}",
            startLabel = "Start stream",
            onStart = { onAction(GlassesDashboardAction.MetaStartStream) },
            startEnabled = state.canStartStream,
            stopLabel = "Stop stream",
            onStop = { onAction(GlassesDashboardAction.MetaStopStream) },
            stopEnabled = state.canStopStream,
        )
        ActionRow(
            primaryLabel = "Capture photo",
            onPrimary = { onAction(GlassesDashboardAction.MetaCapturePhoto) },
            primaryEnabled = state.canCapturePhoto,
            secondaryLabel = "View last photo",
            onSecondary = { onAction(GlassesDashboardAction.MetaViewPhoto) },
            secondaryEnabled = state.hasCapturedPhoto,
        )
        if (state.displayCapable) {
            MetaControlRow(
                status = "Display: ${if (state.displayActive) "Active" else "Inactive"}",
                startLabel = "Start display",
                onStart = { onAction(GlassesDashboardAction.MetaStartDisplay) },
                startEnabled = !state.displayActive,
                stopLabel = "Stop display",
                onStop = { onAction(GlassesDashboardAction.MetaStopDisplay) },
                stopEnabled = state.displayActive,
            )
        }
    }
}

@Composable
private fun MetaControlRow(
    status: String,
    startLabel: String,
    onStart: () -> Unit,
    startEnabled: Boolean,
    stopLabel: String,
    onStop: () -> Unit,
    stopEnabled: Boolean,
) {
    Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    ActionRow(
        primaryLabel = startLabel,
        onPrimary = onStart,
        primaryEnabled = startEnabled,
        secondaryLabel = stopLabel,
        onSecondary = onStop,
        secondaryEnabled = stopEnabled,
    )
}

@Composable
private fun AdvancedControls(
    state: GlassesDashboardUiState,
    onAction: (GlassesDashboardAction) -> Unit,
    onRequestOtaFirmware: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Local agent")
        Text("Status: ${state.agentStatus}", style = MaterialTheme.typography.bodyMedium)
        Text(
            text = "Last error: ${state.agentLastError}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ThreeActionRow(
            firstLabel = "Start",
            onFirst = { onAction(GlassesDashboardAction.StartAgent) },
            secondLabel = "Stop",
            onSecond = { onAction(GlassesDashboardAction.StopAgent) },
            thirdLabel = "Demo",
            onThird = { onAction(GlassesDashboardAction.RunAgentDemo) },
        )
        HorizontalDivider()
        SectionTitle("Device info")
        ActionRow(
            primaryLabel = "Battery",
            onPrimary = { onAction(GlassesDashboardAction.RequestBattery) },
            secondaryLabel = "Version",
            onSecondary = { onAction(GlassesDashboardAction.RequestVersion) },
        )
        ActionRow(
            primaryLabel = "Sync time",
            onPrimary = { onAction(GlassesDashboardAction.SyncTime) },
            secondaryLabel = "Volume",
            onSecondary = { onAction(GlassesDashboardAction.RequestVolume) },
        )
        HorizontalDivider()
        SectionTitle("Developer tools")
        TextButton(
            onClick = { onAction(GlassesDashboardAction.AddDeviceListener) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Register device listener") }
        TextButton(
            onClick = { onAction(GlassesDashboardAction.StartClassicBluetoothScan) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Classic Bluetooth scan") }
        TextButton(
            onClick = { onAction(GlassesDashboardAction.DumpOtaInfo) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Dump OTA information") }
        TextButton(
            onClick = { onAction(GlassesDashboardAction.TestPullOta) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Test pull-mode OTA") }
        HorizontalDivider()
        SectionTitle("OTA firmware update")
        Text(
            text = "Choose a personal file or an approved server artifact for one chip at a time.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OtaTargetSelector(
            selected = state.ota.selectedTarget,
            onSelect = { onAction(GlassesDashboardAction.SelectOtaTarget(it)) },
            enabled = state.ota.canStart,
        )
        OtaProgressSection(state.ota)
        ActionRow(
            primaryLabel = when (state.ota.selectedTarget) {
                OtaTargetSelection.V821_WIFI -> "Choose Wi-Fi SWU"
                OtaTargetSelection.JIELI_BLE -> "Choose BLE .bin"
            },
            onPrimary = onRequestOtaFirmware,
            secondaryLabel = "Cancel",
            onSecondary = { onAction(GlassesDashboardAction.CancelOta) },
            primaryEnabled = state.ota.canStart,
            secondaryEnabled = state.ota.canCancel,
        )
    }
}

@Composable
private fun OtaFirmwareSourcePickerDialog(
    target: OtaTargetSelection,
    riskAcknowledged: Boolean,
    onRiskAcknowledgedChange: (Boolean) -> Unit,
    onDismissRequest: () -> Unit,
    onSourceSelected: (OtaFirmwareSource) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.testTag("ota_firmware_source_picker"),
        title = { Text("Choose ${target.label}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Firmware is model- and chip-specific. The official update normally applies " +
                        "a Wi-Fi SWU before its matching BLE BIN. CyanBridge deliberately flashes " +
                        "one selected image at a time and never guesses a companion image. A successful " +
                        "Wi-Fi update does not automatically start BLE DFU in CyanBridge."
                )
                Text(
                    "Server copies are available only when the relay has a hash-verified patch built " +
                        "from this chip's exact installed firmware version."
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = riskAcknowledged,
                        onCheckedChange = onRiskAcknowledgedChange,
                        modifier = Modifier.testTag("ota_firmware_risk_acknowledgement"),
                    )
                    Text("I verified this image matches this glasses model and chip target.")
                }
                OtaFirmwareSource.entries.forEach { source ->
                    OutlinedButton(
                        enabled = riskAcknowledged,
                        onClick = { onSourceSelected(source) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ota_firmware_source_${source.name.lowercase()}"),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(source.label, style = MaterialTheme.typography.labelLarge)
                            Text(
                                source.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismissRequest) { Text("Cancel") }
        },
    )
}

@Composable
private fun FirmwarePatchRequestDialog(
    request: FirmwarePatchRequestUiState,
    onDismissRequest: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var contactEmail by remember(
        request.source,
        request.target,
        request.targetHardwareVersion,
        request.targetFirmwareVersion,
        request.suggestedContactEmail,
    ) { mutableStateOf(request.suggestedContactEmail) }
    val validEmail = isValidContactEmail(contactEmail)

    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.testTag("firmware_patch_request_dialog"),
        title = { Text("Request firmware patch") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "CyanBridge only downloads patched firmware built from the exact version " +
                        "reported by each target chip. The developer cannot verify which Wi-Fi " +
                        "and Bluetooth chip versions are compatible, so mixed or unverified " +
                        "versions are never offered."
                )
                Text(
                    "Some old or very recent versions may not be available yet. Send a request " +
                        "for this exact version and the developer will contact you at the email " +
                        "below to assist, probably within 48 hours."
                )
                Text(
                    "Requested: ${request.target.label}\n" +
                        "Hardware: ${request.targetHardwareVersion}\n" +
                        "Firmware: ${request.targetFirmwareVersion}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = contactEmail,
                    onValueChange = { contactEmail = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("firmware_patch_request_email"),
                    label = { Text("Contact email") },
                    singleLine = true,
                    enabled = !request.isSubmitting,
                    isError = contactEmail.isNotBlank() && !validEmail,
                    supportingText = {
                        Text(
                            if (contactEmail.isNotBlank() && !validEmail) {
                                "Enter a valid email address"
                            } else {
                                "Version details and OTA diagnostics will be sent to CyanBridge."
                            },
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
                request.submissionError?.let { error ->
                    Text(
                        error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = validEmail && !request.isSubmitting,
                onClick = { onSubmit(contactEmail.trim()) },
                modifier = Modifier.testTag("firmware_patch_request_send"),
            ) { Text(if (request.isSubmitting) "Sending..." else "Send request") }
        },
        dismissButton = {
            TextButton(
                enabled = !request.isSubmitting,
                onClick = onDismissRequest,
                modifier = Modifier.testTag("firmware_patch_request_cancel"),
            ) { Text("Cancel") }
        },
    )
}

private fun isValidContactEmail(value: String): Boolean =
    value.trim().matches(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))

@Composable
private fun ActionRow(
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String,
    onSecondary: () -> Unit,
    primaryEnabled: Boolean = true,
    secondaryEnabled: Boolean = true,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(
            onClick = onPrimary,
            enabled = primaryEnabled,
            modifier = Modifier.weight(1f),
        ) { Text(primaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        OutlinedButton(
            onClick = onSecondary,
            enabled = secondaryEnabled,
            modifier = Modifier.weight(1f),
        ) { Text(secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
private fun ThreeActionRow(
    firstLabel: String,
    onFirst: () -> Unit,
    secondLabel: String,
    onSecond: () -> Unit,
    thirdLabel: String,
    onThird: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = onFirst, modifier = Modifier.weight(1f)) { Text(firstLabel) }
        OutlinedButton(onClick = onSecond, modifier = Modifier.weight(1f)) { Text(secondLabel) }
        OutlinedButton(onClick = onThird, modifier = Modifier.weight(1f)) { Text(thirdLabel) }
    }
}

@Composable
private fun SectionTitle(text: String, accented: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = if (accented) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun OtaTargetSelector(
    selected: OtaTargetSelection,
    onSelect: (OtaTargetSelection) -> Unit,
    enabled: Boolean,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        OtaTargetSelection.entries.forEach { target ->
            FilterChip(
                selected = selected == target,
                onClick = { onSelect(target) },
                label = {
                    Text(
                        text = target.label,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                enabled = enabled,
                modifier = Modifier.weight(1f),
            )
        }
    }
    Text(
        text = selected.description,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun OtaProgressSection(ota: OtaSectionUiState) {
    if (ota.stateLabel == "Idle") return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Status: ${ota.stateLabel}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            if (ota.stateLabel == "Complete") {
                Text(
                    text = "Done",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        ota.progress?.let { progress ->
            LinearProgressIndicator(
                progress = { progress.coerceIn(0, 100) / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
        } ?: LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        if (ota.detail.isNotBlank()) {
            Text(
                text = ota.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

package com.fersaiyan.cyanbridge.shared.ui.glasses

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.Switch
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
import com.fersaiyan.cyanbridge.shared.glasses.MeizuMyvuUiState
import com.fersaiyan.cyanbridge.shared.glasses.OtaFirmwareSource
import com.fersaiyan.cyanbridge.shared.glasses.OtaSectionUiState
import com.fersaiyan.cyanbridge.shared.glasses.LivePreviewUiState
import com.fersaiyan.cyanbridge.shared.glasses.WifiAdbDebugUiState
import com.fersaiyan.cyanbridge.shared.navigation.AppDestination
import com.fersaiyan.cyanbridge.shared.generated.resources.*
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class, ExperimentalResourceApi::class)
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
                ActionButton(
                    label = "Disconnect",
                    onClick = { onAction(GlassesDashboardAction.Disconnect) },
                    style = ActionButtonStyle.Destructive,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                NativePluginShortcutSection(
                    shortcut = state.nativePluginShortcut,
                    onAction = onAction,
                )
            }
            if (state.showHeyCyanControls || state.showEyevueControls) {
                item { CoreGlassesControls(state, onAction) }
            }
            if (state.showMetaRaybanControls) {
                item { MetaRaybanControls(state.metaRayban, onAction) }
                item { GlassesAssistantControls(state, onAction) }
            }
            if (state.showMeizuMyvuControls) {
                item { MeizuMyvuControls(state.meizuMyvu, onAction) }
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
            if (state.showHeyCyanControls || state.showEyevueControls) {
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
                primaryStyle = ActionButtonStyle.Primary,
                secondaryLabel = "Stop",
                onSecondary = onStop,
                secondaryEnabled = state.canStop,
                secondaryStyle = ActionButtonStyle.Destructive,
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
                        ActionButton(
                            label = button.label,
                            onClick = {
                                onAction(GlassesDashboardAction.RunNativePluginShortcut(button.action))
                            },
                            style = when (button.action) {
                                NativePluginShortcutAction.START -> ActionButtonStyle.Primary
                                NativePluginShortcutAction.STOP -> ActionButtonStyle.Destructive
                                else -> ActionButtonStyle.Neutral
                            },
                            modifier = buttonModifier,
                        )
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
            TextButton(onClick = onStop) { Text("Stop", color = MaterialTheme.colorScheme.error) }
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
                TextButton(onClick = onStop) { Text("Stop sync", color = MaterialTheme.colorScheme.error) }
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
        RecordingSettingsControls(state, onAction)
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
        ActionButton(
            label = "Sync data over Wi-Fi",
            onClick = { onAction(GlassesDashboardAction.StartSync) },
            style = ActionButtonStyle.Primary,
            modifier = Modifier.fillMaxWidth(),
            )
            if (state.livePreview.isAvailable) {
                Spacer(Modifier.height(8.dp))
            SectionTitle(if (state.showEyevueControls) "Eyevue live preview" else "Passive RTSP lab probe")
            Text(
                text = if (state.showEyevueControls) {
                    "Starts the vendor live mode, joins the returned Eyevue Wi-Fi network, and plays the camera stream."
                } else {
                    "Sends no BLE mode command. Activate mode 8 separately using the approved hardware procedure."
                },
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
                primaryLabel = if (state.livePreview.isScanning) {
                    "Connecting..."
                } else if (state.showEyevueControls) {
                    "Start live preview"
                } else {
                    "Arm passive probe"
                },
                onPrimary = { onAction(GlassesDashboardAction.StartLivePreview) },
                primaryEnabled = state.livePreview.canStart && !state.livePreview.isScanning,
                primaryStyle = ActionButtonStyle.Primary,
                secondaryLabel = "Stop",
                onSecondary = { onAction(GlassesDashboardAction.StopLivePreview) },
                secondaryEnabled = state.livePreview.canStop,
                secondaryStyle = ActionButtonStyle.Destructive,
            )
        }
    }
}

@Composable
private fun RecordingSettingsControls(
    state: GlassesDashboardUiState,
    onAction: (GlassesDashboardAction) -> Unit,
) {
    Column(
        modifier = Modifier.testTag("glasses_recording_settings"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionTitle("Glasses capture settings")
        Text(
            text = "Read from the connected glasses before changing these device-stored settings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Wearing detection", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = when (state.wearingDetectionEnabled) {
                        true -> "Enabled on the glasses"
                        false -> "Disabled on the glasses"
                        null -> "Load settings to read its current state"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = state.wearingDetectionEnabled == true,
                enabled = state.wearingDetectionEnabled != null,
                onCheckedChange = { enabled ->
                    onAction(GlassesDashboardAction.SetWearingDetection(enabled))
                },
                modifier = Modifier.testTag("wearing_detection_switch"),
            )
        }
        RecordingDurationSelector(
            title = "Maximum video length",
            currentSeconds = state.videoRecordingDurationSeconds,
            optionsSeconds = state.videoRecordingDurationOptionsSeconds,
            onSelect = { onAction(GlassesDashboardAction.SetVideoRecordingDuration(it)) },
            testTagPrefix = "video_recording_duration",
        )
        RecordingDurationSelector(
            title = "Maximum audio length",
            currentSeconds = state.audioRecordingDurationSeconds,
            optionsSeconds = state.audioRecordingDurationOptionsSeconds,
            onSelect = { onAction(GlassesDashboardAction.SetAudioRecordingDuration(it)) },
            testTagPrefix = "audio_recording_duration",
        )
        OutlinedButton(
            onClick = { onAction(GlassesDashboardAction.RefreshRecordingSettings) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("refresh_recording_settings"),
        ) {
            Text("Load settings from glasses")
        }
    }
}

@Composable
private fun RecordingDurationSelector(
    title: String,
    currentSeconds: Int?,
    optionsSeconds: List<Int>,
    onSelect: (Int) -> Unit,
    testTagPrefix: String,
) {
    Text(title, style = MaterialTheme.typography.titleSmall)
    if (optionsSeconds.isEmpty()) {
        Text(
            text = "Load settings to see the limits supported by these glasses.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    optionsSeconds.chunked(3).forEach { rowOptions ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            rowOptions.forEach { seconds ->
                FilterChip(
                    selected = currentSeconds == seconds,
                    onClick = { onSelect(seconds) },
                    label = { Text(formatRecordingDuration(seconds)) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("${testTagPrefix}_$seconds"),
                )
            }
            repeat(3 - rowOptions.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

private fun formatRecordingDuration(seconds: Int): String = when {
    seconds < 60 -> "${seconds}s"
    seconds % 3600 == 0 -> "${seconds / 3600}h"
    seconds % 60 == 0 -> "${seconds / 60}m"
    else -> "${seconds}s"
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
                label = "Phone Assistant",
                mode = GlassesAssistantMode.PHONE_ASSISTANT,
                selectedMode = state.assistantMode,
                onAction = onAction,
                modifier = Modifier.weight(1f),
            )
            AssistantModeChip(
                label = "Local / Pro / Tasker",
                mode = GlassesAssistantMode.CUSTOM_AI_PROVIDER,
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
        OutlinedButton(
            onClick = { onAction(GlassesDashboardAction.OpenExternalImageAutomationDiagnostics) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Gemini / ChatGPT automation setup")
        }
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
        SectionTitle(stringResource(Res.string.meta_rayban_title), accented = true)
        Text(
            text = stringResource(
                Res.string.meta_rayban_device_summary,
                state.selectedDeviceName ?: stringResource(Res.string.meta_rayban_no_device),
                state.availableDeviceCount,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.lastError?.takeIf { it.isNotBlank() }?.let { error ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("meta_rayban_last_error"),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(Res.string.meta_rayban_last_error),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = { onAction(GlassesDashboardAction.MetaSendDiagnostics) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(Res.string.meta_rayban_send_diagnostics))
                    }
                }
            }
        }
        if (state.lastError.isNullOrBlank()) {
            OutlinedButton(
                onClick = { onAction(GlassesDashboardAction.MetaSendDiagnostics) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.meta_rayban_send_diagnostics))
            }
        }
        MetaControlRow(
            status = stringResource(Res.string.meta_rayban_registration_status, state.registrationLabel),
            startLabel = stringResource(Res.string.meta_rayban_register),
            onStart = { onAction(GlassesDashboardAction.MetaRegister) },
            startEnabled = state.canRegister,
            stopLabel = stringResource(Res.string.meta_rayban_unregister),
            onStop = { onAction(GlassesDashboardAction.MetaUnregister) },
            stopEnabled = state.canUnregister,
        )
        MetaControlRow(
            status = stringResource(Res.string.meta_rayban_session_status, state.sessionLabel),
            startLabel = stringResource(Res.string.meta_rayban_start_session),
            onStart = { onAction(GlassesDashboardAction.MetaStartSession) },
            startEnabled = state.canStartSession,
            stopLabel = stringResource(Res.string.meta_rayban_stop_session),
            onStop = { onAction(GlassesDashboardAction.MetaStopSession) },
            stopEnabled = state.canStopSession,
        )
        MetaControlRow(
            status = stringResource(Res.string.meta_rayban_stream_status, state.streamLabel),
            startLabel = stringResource(Res.string.meta_rayban_start_stream),
            onStart = { onAction(GlassesDashboardAction.MetaStartStream) },
            startEnabled = state.canStartStream,
            stopLabel = stringResource(Res.string.meta_rayban_stop_stream),
            onStop = { onAction(GlassesDashboardAction.MetaStopStream) },
            stopEnabled = state.canStopStream,
        )
        ActionRow(
            primaryLabel = stringResource(Res.string.meta_rayban_capture_photo),
            onPrimary = { onAction(GlassesDashboardAction.MetaCapturePhoto) },
            primaryEnabled = state.canCapturePhoto,
            primaryStyle = ActionButtonStyle.Primary,
            secondaryLabel = stringResource(Res.string.meta_rayban_view_last_photo),
            onSecondary = { onAction(GlassesDashboardAction.MetaViewPhoto) },
            secondaryEnabled = state.hasCapturedPhoto,
        )
        if (state.displayCapable) {
            MetaControlRow(
                status = stringResource(
                    Res.string.meta_rayban_display_status,
                    stringResource(
                        if (state.displayActive) {
                            Res.string.meta_rayban_active
                        } else {
                            Res.string.meta_rayban_inactive
                        },
                    ),
                ),
                startLabel = stringResource(Res.string.meta_rayban_start_display),
                onStart = { onAction(GlassesDashboardAction.MetaStartDisplay) },
                startEnabled = !state.displayActive,
                stopLabel = stringResource(Res.string.meta_rayban_stop_display),
                onStop = { onAction(GlassesDashboardAction.MetaStopDisplay) },
                stopEnabled = state.displayActive,
            )
        }
    }
}

@Composable
private fun MeizuMyvuControls(
    state: MeizuMyvuUiState,
    onAction: (GlassesDashboardAction) -> Unit,
) {
    Column(
        modifier = Modifier.testTag("meizu_myvu_controls"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionTitle("Meizu MYVU / Star Air", accented = true)
        Text(
            text = state.connectionLabel,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = "Protocol: ${state.protocolState}" + state.deviceName?.let { "  Device: $it" }.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.batteryPercent?.let { battery ->
            Text("Battery: $battery%", style = MaterialTheme.typography.bodySmall)
        }
        state.lastError?.takeIf { it.isNotBlank() }?.let { error ->
            Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        Text(
            "MYVU uses its own BLE key exchange and RFCOMM relay. Keep the official MYVU app disconnected while connecting.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ActionRow(
            primaryLabel = "Connect",
            onPrimary = { onAction(GlassesDashboardAction.MeizuConnect) },
            primaryEnabled = state.canConnect,
            primaryStyle = ActionButtonStyle.Primary,
            secondaryLabel = "Disconnect",
            onSecondary = { onAction(GlassesDashboardAction.MeizuDisconnect) },
            secondaryEnabled = state.canDisconnect,
            secondaryStyle = ActionButtonStyle.Destructive,
        )
        ActionRow(
            primaryLabel = "Test notification",
            onPrimary = { onAction(GlassesDashboardAction.MeizuSendTestNotification) },
            primaryEnabled = state.canSend,
            secondaryLabel = "Show text",
            onSecondary = { onAction(GlassesDashboardAction.MeizuShowTestTeleprompter) },
            secondaryEnabled = state.canSend,
        )
        ActionRow(
            primaryLabel = "Sync clock",
            onPrimary = { onAction(GlassesDashboardAction.MeizuSyncClock) },
            primaryEnabled = state.canSend,
            secondaryLabel = "Comfort brightness",
            onSecondary = { onAction(GlassesDashboardAction.MeizuSetComfortBrightness) },
            secondaryEnabled = state.canSend,
        )
        Text(
            "Voice plugins use the MYVU HFP microphone route after connection. Camera capture, onboard-media sync, Visual Diary, and Walking Aid are not supported because MYVU has no camera or media store.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
        primaryStyle = ActionButtonStyle.Primary,
        secondaryLabel = stopLabel,
        onSecondary = onStop,
        secondaryEnabled = stopEnabled,
        secondaryStyle = ActionButtonStyle.Destructive,
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
            firstStyle = ActionButtonStyle.Primary,
            secondLabel = "Stop",
            onSecond = { onAction(GlassesDashboardAction.StopAgent) },
            secondStyle = ActionButtonStyle.Destructive,
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
        SectionTitle("AI image quality")
        Text(
            text = "BLE thumbnail for AI image questions: ${state.imageThumbnailQualityLabel}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        listOf("Instant", "Quick", "Smooth", "Fine", "Clearer", "Detailed")
            .chunked(3)
            .forEachIndexed { rowIndex, labels ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    labels.forEachIndexed { columnIndex, label ->
                        val sdkValue = rowIndex * 3 + columnIndex
                        FilterChip(
                            selected = state.imageThumbnailQualitySdkValue == sdkValue,
                            onClick = {
                                onAction(GlassesDashboardAction.SelectImageThumbnailQuality(sdkValue))
                            },
                            label = { Text(label) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("ai_image_thumbnail_quality_$sdkValue"),
                        )
                    }
                }
            }
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
            text = "Run one controlled update for both chips: Wi-Fi .swu first, then BLE .bin after a fresh readiness check.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OtaProgressSection(state.ota)
        ActionRow(
            primaryLabel = "Choose combined OTA files",
            onPrimary = onRequestOtaFirmware,
            primaryStyle = ActionButtonStyle.Primary,
            secondaryLabel = "Cancel",
            onSecondary = { onAction(GlassesDashboardAction.CancelOta) },
            secondaryStyle = ActionButtonStyle.Destructive,
            primaryEnabled = state.ota.canStart,
            secondaryEnabled = state.ota.canCancel,
        )
    }
}

@Composable
private fun OtaFirmwareSourcePickerDialog(
    riskAcknowledged: Boolean,
    onRiskAcknowledgedChange: (Boolean) -> Unit,
    onDismissRequest: () -> Unit,
    onSourceSelected: (OtaFirmwareSource) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.testTag("ota_firmware_source_picker"),
        title = { Text("Choose source for both firmware components") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Firmware is model- and chip-specific. CyanBridge stages both components before " +
                        "flashing anything, then follows the official order: Wi-Fi SWU first and BLE " +
                        "BIN second. A Wi-Fi failure never starts BLE DFU."
                )
                Text(
                    "Server copies are selected only when both files exactly match the Wi-Fi and Bluetooth " +
                        "versions currently reported by these glasses. Personal files require two selections: a .swu followed by a .bin."
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

private enum class ActionButtonStyle {
    Neutral,
    Primary,
    Destructive,
}

@Composable
private fun ActionButton(
    label: String,
    onClick: () -> Unit,
    style: ActionButtonStyle = ActionButtonStyle.Neutral,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    when (style) {
        ActionButtonStyle.Primary -> FilledTonalButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
        ) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        ActionButtonStyle.Neutral -> OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
        ) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        ActionButtonStyle.Destructive -> OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
                disabledContentColor = MaterialTheme.colorScheme.error.copy(alpha = 0.38f),
            ),
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.error.copy(alpha = if (enabled) 1f else 0.38f),
            ),
        ) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ActionRow(
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String,
    onSecondary: () -> Unit,
    primaryEnabled: Boolean = true,
    secondaryEnabled: Boolean = true,
    primaryStyle: ActionButtonStyle = ActionButtonStyle.Neutral,
    secondaryStyle: ActionButtonStyle = ActionButtonStyle.Neutral,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ActionButton(
            label = primaryLabel,
            onClick = onPrimary,
            enabled = primaryEnabled,
            modifier = Modifier.weight(1f),
            style = primaryStyle,
        )
        ActionButton(
            label = secondaryLabel,
            onClick = onSecondary,
            enabled = secondaryEnabled,
            modifier = Modifier.weight(1f),
            style = secondaryStyle,
        )
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
    firstStyle: ActionButtonStyle = ActionButtonStyle.Neutral,
    secondStyle: ActionButtonStyle = ActionButtonStyle.Neutral,
    thirdStyle: ActionButtonStyle = ActionButtonStyle.Neutral,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ActionButton(
            label = firstLabel,
            onClick = onFirst,
            modifier = Modifier.weight(1f),
            style = firstStyle,
        )
        ActionButton(
            label = secondLabel,
            onClick = onSecond,
            modifier = Modifier.weight(1f),
            style = secondStyle,
        )
        ActionButton(
            label = thirdLabel,
            onClick = onThird,
            modifier = Modifier.weight(1f),
            style = thirdStyle,
        )
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

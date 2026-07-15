package com.fersaiyan.cyanbridge.ui.glasses

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fersaiyan.cyanbridge.shared.glasses.GlassesAssistantMode
import com.fersaiyan.cyanbridge.shared.glasses.GlassesDashboardAction
import com.fersaiyan.cyanbridge.shared.glasses.GlassesDashboardUiState
import com.fersaiyan.cyanbridge.shared.glasses.MetaRaybanUiState
import com.fersaiyan.cyanbridge.shared.glasses.OtaSectionUiState
import com.fersaiyan.cyanbridge.shared.navigation.AppDestination
import com.fersaiyan.cyanbridge.ui.navigation.CyanBridgeNavigationBar

private val meetingTimerLabels = listOf("No timer", "15 min", "1 hour", "3 hours")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassesDashboardScreen(
    state: GlassesDashboardUiState,
    onAction: (GlassesDashboardAction) -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = { TopAppBar(title = { Text("Glasses") }) },
        bottomBar = {
            CyanBridgeNavigationBar(
                selectedDestination = AppDestination.GLASSES,
                onDestinationSelected = { onAction(GlassesDashboardAction.Navigate(it)) },
            )
        },
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
                SectionTitle("Meeting capture")
                ActionRow(
                    primaryLabel = "Start",
                    onPrimary = { onAction(GlassesDashboardAction.StartMeetingCapture) },
                    primaryEnabled = !state.meeting.isRecording,
                    secondaryLabel = "Stop",
                    onSecondary = { onAction(GlassesDashboardAction.StopMeetingCapture) },
                    secondaryEnabled = state.meeting.isRecording,
                )
                Spacer(Modifier.height(10.dp))
                Text("Timer", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                TimerOptions(
                    selectedIndex = state.meeting.timerIndex,
                    onSelected = { onAction(GlassesDashboardAction.SelectMeetingTimer(it)) },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Source: ${state.meeting.sourceLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.showHeyCyanControls) {
                item { HeyCyanControls(state, onAction) }
            }
            if (state.showMetaRaybanControls) {
                item { MetaRaybanControls(state.metaRayban, onAction) }
            }
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
                item { AdvancedControls(state, onAction) }
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
private fun TimerOptions(selectedIndex: Int, onSelected: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        meetingTimerLabels.forEachIndexed { index, label ->
            FilterChip(
                selected = selectedIndex == index,
                onClick = { onSelected(index) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun HeyCyanControls(
    state: GlassesDashboardUiState,
    onAction: (GlassesDashboardAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                label = "Provider",
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
            text = "Flash debug SWU to enable ADB over Wi-Fi",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OtaProgressSection(state.ota)
        ActionRow(
            primaryLabel = "Flash debug SWU",
            onPrimary = { onAction(GlassesDashboardAction.StartOta) },
            secondaryLabel = "Cancel",
            onSecondary = { onAction(GlassesDashboardAction.CancelOta) },
            primaryEnabled = state.ota.canStart,
            secondaryEnabled = state.ota.canCancel,
        )
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

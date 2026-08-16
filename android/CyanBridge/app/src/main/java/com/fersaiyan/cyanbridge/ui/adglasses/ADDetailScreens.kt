package com.fersaiyan.cyanbridge.ui.adglasses

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DeveloperMode
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PermDeviceInformation
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fersaiyan.cyanbridge.R
import com.fersaiyan.cyanbridge.shared.glasses.GlassesDashboardUiState

@Composable
internal fun ADDeviceCenterScreen(
    state: GlassesDashboardUiState,
    host: ADHostActions,
    onBack: () -> Unit,
    onSync: () -> Unit,
    onFirmware: () -> Unit,
    onAdvanced: () -> Unit,
) {
    val connected = state.connectionLabel.contains("connected", true) &&
        !state.connectionLabel.contains("disconnected", true)
    val hasKnownProfile = state.deviceClassLabel != "Unknown"
    ADDetailLayout("Device Center", onBack) {
        ADCard {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(132.dp)
                    .background(
                        Brush.radialGradient(listOf(Color.White, ADColors.BlueSoft.copy(alpha = 0.72f))),
                        RoundedCornerShape(18.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ad_glasses_hero_v4),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 4.dp),
                    contentScale = ContentScale.Fit,
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (hasKnownProfile) state.deviceClassLabel else "No active glasses",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(state.connectionLabel, style = MaterialTheme.typography.bodyMedium, color = ADColors.Muted)
                }
                ADStatusChip(
                    if (connected) "CONNECTED" else "OFFLINE",
                    if (connected) ADStatusTone.SUCCESS else ADStatusTone.NEUTRAL,
                    showCheck = connected,
                )
            }
            Spacer(Modifier.height(16.dp))
            if (!hasKnownProfile && !connected) {
                Button(
                    onClick = host.onOpenDeviceSetup,
                    colors = ButtonDefaults.buttonColors(containerColor = ADColors.Blue),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Connect glasses") }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = if (connected) host.onDisconnect else host.onReconnect,
                        colors = ButtonDefaults.buttonColors(containerColor = ADColors.Ink),
                        modifier = Modifier.weight(1f),
                    ) { Text(if (connected) "Disconnect" else "Reconnect") }
                    OutlinedButton(onClick = host.onOpenDeviceSetup, modifier = Modifier.weight(1f)) {
                        Text("Change device")
                    }
                }
            }
        }

        ADSectionTitle("Status")
        ADCard {
            ADMetricRow(Icons.Outlined.Bluetooth, "Transport", if (connected) "Bluetooth connected" else "Not connected")
            if (state.showBattery && state.batteryPercent != null) {
                HorizontalDivider(Modifier.padding(start = 32.dp), color = ADColors.Separator)
                ADMetricRow(Icons.Outlined.BatteryFull, "Battery", "${state.batteryPercent}%")
            }
            if (state.showStorage && state.storageLabel != "--") {
                HorizontalDivider(Modifier.padding(start = 32.dp), color = ADColors.Separator)
                ADMetricRow(Icons.Outlined.Storage, "Storage", state.storageLabel)
            }
            ADMetricRow(
                Icons.Outlined.PermDeviceInformation,
                "Support profile",
                when {
                    state.showHeyCyanControls -> "HeyCyan · Primary"
                    state.showEyevueControls -> "Eyevue · Experimental"
                    state.showMetaRaybanControls -> "Meta · Experimental"
                    state.showMeizuMyvuControls -> "Meizu MYVU · Experimental"
                    else -> "Generic · Limited until detected"
                },
            )
        }

        ADSectionTitle("Available actions")
        ADCard {
            ADSettingsRow(
                icon = Icons.Outlined.Sync,
                title = "Sync media",
                subtitle = "Local Wi-Fi transfer when supported",
                onClick = onSync,
                iconTint = Color.White,
                iconBackground = ADColors.Blue,
            )
            HorizontalDivider(Modifier.padding(start = 48.dp), color = ADColors.Separator)
            ADSettingsRow(
                icon = Icons.Outlined.SystemUpdateAlt,
                title = "Firmware Lab",
                subtitle = "HeyCyan only · careful preflight required",
                onClick = onFirmware,
                iconTint = Color.White,
                iconBackground = Color(0xFFFF9500),
            )
            HorizontalDivider(Modifier.padding(start = 48.dp), color = ADColors.Separator)
            ADSettingsRow(
                icon = Icons.Outlined.DeveloperMode,
                title = "Advanced and diagnostics",
                subtitle = "Logs and experimental runtimes",
                onClick = onAdvanced,
                iconTint = Color.White,
                iconBackground = Color(0xFF8E8E93),
            )
        }
    }
}

@Composable
internal fun ADSyncScreen(state: GlassesDashboardUiState, host: ADHostActions, onBack: () -> Unit) {
    val transfer = state.transfer
    val transferProgress = transfer.progress
    val connected = state.connectionLabel.contains("connected", ignoreCase = true) &&
        !state.connectionLabel.contains("disconnected", ignoreCase = true)
    val knownCounts = transfer.countsLabel
        .takeUnless { it.isBlank() || it == "Photos: --  Videos: --  Audio: --" }
    val flow = transfer.flowLabel.takeUnless { it.isBlank() || it == "--" } ?: "Local Wi-Fi"
    ADDetailLayout("Sync Center", onBack) {
        ADCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(48.dp).background(ADColors.BlueSoft, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.Sync, null, tint = ADColors.Blue) }
                Column(Modifier.padding(start = 13.dp).weight(1f)) {
                    Text(
                        when {
                            transfer.isVisible -> "Transfer in progress"
                            connected -> "Sync media"
                            else -> "Glasses offline"
                        },
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        when {
                            transfer.isVisible -> transfer.detail
                            connected -> "Ready"
                            else -> "Connect glasses to begin"
                        },
                        color = ADColors.Muted,
                    )
                }
            }
            if (transfer.isVisible) {
                Spacer(Modifier.height(18.dp))
                if (transferProgress != null) {
                    LinearProgressIndicator(
                        progress = { transferProgress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(7.dp),
                        color = ADColors.Blue,
                        trackColor = ADColors.SurfaceSubtle,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("${(transferProgress * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
                } else {
                    CircularProgressIndicator(color = ADColors.Blue, modifier = Modifier.size(28.dp))
                }
            }
        }

        ADSectionTitle("Details")
        ADCard {
            ADMetricRow(Icons.Outlined.Bluetooth, "Connection", if (connected) "Connected" else "Not connected")
            HorizontalDivider(Modifier.padding(start = 32.dp), color = ADColors.Separator)
            ADMetricRow(Icons.Outlined.Wifi, "Transfer", flow)
            HorizontalDivider(Modifier.padding(start = 32.dp), color = ADColors.Separator)
            ADMetricRow(Icons.Outlined.Storage, "Media", knownCounts ?: "Scan when sync starts")
        }
        Button(
            onClick = when {
                transfer.isVisible -> host.onStopSync
                !connected -> host.onOpenDeviceSetup
                else -> host.onStartSync
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (transfer.isVisible) ADColors.Error else ADColors.Blue,
            ),
        ) {
            Icon(
                when {
                    transfer.isVisible -> Icons.Outlined.StopCircle
                    !connected -> Icons.Outlined.Bluetooth
                    else -> Icons.Outlined.Sync
                },
                null,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    transfer.isVisible -> "Cancel transfer"
                    !connected -> "Connect glasses"
                    else -> "Start sync"
                },
            )
        }
    }
}

@Composable
internal fun ADSettingsScreen(
    state: GlassesDashboardUiState,
    onBack: () -> Unit,
    onDevice: () -> Unit,
    onAi: () -> Unit,
    onPrivacy: () -> Unit,
    onAdvanced: () -> Unit,
    onLegacySettings: () -> Unit,
) {
    ADDetailLayout("Settings", onBack) {
        ADCard(onClick = onDevice) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ADGlassesMark(Modifier.size(width = 38.dp, height = 24.dp))
                Column(Modifier.padding(start = 14.dp).weight(1f)) {
                    Text(
                        if (state.deviceClassLabel == "Unknown") "No active glasses" else state.deviceClassLabel,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(state.connectionLabel, style = MaterialTheme.typography.bodyMedium, color = ADColors.Muted)
                }
                Icon(Icons.Rounded.KeyboardArrowRight, null, tint = ADColors.Muted)
            }
        }
        ADSettingsGroup("Intelligence") {
            ADSettingsRow(
                icon = Icons.Outlined.AutoAwesome,
                title = "AI services",
                subtitle = "On device, your cloud and web grounding",
                onClick = onAi,
                iconTint = ADColors.Blue,
                iconBackground = ADColors.BlueSoft,
            )
            HorizontalDivider(Modifier.padding(start = 48.dp), color = ADColors.Separator)
            ADSettingsRow(
                icon = Icons.Outlined.Tune,
                title = "Routing defaults",
                subtitle = "Choose automatic or feature-specific routes",
                onClick = onLegacySettings,
                iconTint = ADColors.Blue,
                iconBackground = ADColors.BlueSoft,
            )
        }
        ADSettingsGroup("Privacy and data") {
            ADSettingsRow(
                icon = Icons.Outlined.PrivacyTip,
                title = "Privacy and Data",
                subtitle = "Memory, retention, export and deletion",
                onClick = onPrivacy,
                iconTint = ADColors.Blue,
                iconBackground = ADColors.BlueSoft,
            )
            HorizontalDivider(Modifier.padding(start = 48.dp), color = ADColors.Separator)
            ADSettingsRow(
                icon = Icons.Outlined.Storage,
                title = "Storage",
                subtitle = "Review local app and media usage",
                onClick = onLegacySettings,
                iconTint = ADColors.Blue,
                iconBackground = ADColors.BlueSoft,
            )
        }
        ADSettingsGroup("General") {
            ADSettingsRow(
                icon = Icons.Outlined.Language,
                title = "Language",
                subtitle = "App and transcription preferences",
                onClick = onLegacySettings,
                iconTint = ADColors.Blue,
                iconBackground = ADColors.BlueSoft,
            )
            HorizontalDivider(Modifier.padding(start = 48.dp), color = ADColors.Separator)
            ADSettingsRow(
                icon = Icons.Outlined.Notifications,
                title = "Permissions",
                subtitle = "Notifications, microphone and nearby devices",
                onClick = onLegacySettings,
                iconTint = ADColors.Blue,
                iconBackground = ADColors.BlueSoft,
            )
        }
        ADSettingsGroup("Support") {
            ADSettingsRow(
                icon = Icons.Outlined.DeveloperMode,
                title = "Advanced",
                subtitle = "Diagnostics and experimental runtimes",
                onClick = onAdvanced,
                iconTint = ADColors.Blue,
                iconBackground = ADColors.BlueSoft,
            )
            HorizontalDivider(Modifier.padding(start = 48.dp), color = ADColors.Separator)
            ADSettingsRow(
                icon = Icons.Outlined.Info,
                title = "About AD Glasses",
                subtitle = "Version, licenses and project status",
                onClick = onLegacySettings,
                iconTint = ADColors.Blue,
                iconBackground = ADColors.BlueSoft,
            )
        }
    }
}

@Composable
private fun ADSettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = ADColors.Muted)
        ADCard(content = content)
    }
}

@Composable
internal fun ADAiServicesScreen(onBack: () -> Unit, host: ADHostActions) {
    ADDetailLayout("AI Services", onBack) {
        ADCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).background(ADColors.BlueSoft, RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.AutoAwesome, null, tint = ADColors.Blue)
                }
                Column(Modifier.padding(start = 13.dp).weight(1f)) {
                    Text("Automatic routing", style = MaterialTheme.typography.titleLarge)
                    Text("Uses an available owner-configured service", color = ADColors.Muted)
                }
                ADStatusChip("DEFAULT", ADStatusTone.INFO)
            }
            Spacer(Modifier.height(14.dp))
            OutlinedButton(onClick = host.onOpenLegacySettings, modifier = Modifier.fillMaxWidth()) {
                Text("Configure services")
            }
        }
        ADSectionTitle("Processing options")
        ADServiceCard(Icons.Outlined.Memory, "On device", "Private local models for supported tasks", "Needs model")
        ADServiceCard(Icons.Outlined.Cloud, "Your cloud", "Your endpoint and API credentials", "Needs setup")
        ADServiceCard(Icons.Outlined.Link, "Web grounding", "Current information with source links", "Optional")
    }
}

@Composable
private fun ADServiceCard(icon: ImageVector, title: String, subtitle: String, status: String) {
    ADCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = ADColors.Blue)
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = ADColors.Muted)
            }
            ADStatusChip(status)
        }
    }
}

@Composable
internal fun ADPrivacyScreen(onBack: () -> Unit, host: ADHostActions) {
    ADDetailLayout("Privacy and Data", onBack) {
        ADSettingsGroup("Memory") {
            ADSettingsRow(Icons.Outlined.Memory, "Memory mode", "Off, local-only, or owner cloud", host.onOpenLegacySettings)
            HorizontalDivider(Modifier.padding(start = 48.dp), color = ADColors.Separator)
            ADSettingsRow(Icons.Outlined.Description, "Transcripts", "Retention and processing defaults", host.onOpenLegacySettings)
        }
        ADSettingsGroup("Your data") {
            ADSettingsRow(Icons.Outlined.Download, "Export", "Create a portable copy", host.onOpenLegacySettings)
            HorizontalDivider(Modifier.padding(start = 48.dp), color = ADColors.Separator)
            ADSettingsRow(Icons.Outlined.Upload, "Import", "Restore supported local data", host.onOpenLegacySettings)
            HorizontalDivider(Modifier.padding(start = 48.dp), color = ADColors.Separator)
            ADSettingsRow(Icons.Outlined.DeleteOutline, "Clear local data", "Review before permanent deletion", host.onOpenLegacySettings)
        }
    }
}

@Composable
internal fun ADAdvancedScreen(onBack: () -> Unit, host: ADHostActions) {
    ADDetailLayout("Advanced", onBack) {
        ADSettingsGroup("Diagnostics") {
            ADSettingsRow(Icons.Outlined.Description, "Connection logs", "BLE, Wi-Fi Direct and device notifications", host.onOpenLegacySettings)
            HorizontalDivider(Modifier.padding(start = 48.dp), color = ADColors.Separator)
            ADSettingsRow(Icons.Outlined.Wifi, "Wi-Fi ADB debug", "Debug builds only", host.onOpenLegacySettings)
        }
        ADSettingsGroup("Prototype runtimes") {
            ADSettingsRow(Icons.Outlined.DeveloperMode, "EvenHub and Mentra", "Compatibility runtimes under active research", host.onOpenLegacySettings)
            HorizontalDivider(Modifier.padding(start = 48.dp), color = ADColors.Separator)
            ADSettingsRow(Icons.Outlined.Bluetooth, "MemoMind", "Research adapter and protocol tools", host.onOpenLegacySettings)
        }
        ADStatusChip("EXPERIMENTAL", ADStatusTone.WARNING)
    }
}

@Composable
internal fun ADFirmwareScreen(
    state: GlassesDashboardUiState,
    host: ADHostActions,
    onBack: () -> Unit,
) {
    val ota = state.ota
    val otaProgress = ota.progress
    val bluetoothReady = state.showHeyCyanControls &&
        state.connectionLabel.startsWith("Connected", ignoreCase = true)
    var riskAcknowledged by remember { mutableStateOf(false) }
    ADDetailLayout("HeyCyan Firmware Lab", onBack) {
        ADCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).background(ADColors.WarningSoft, RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.SystemUpdateAlt, null, tint = ADColors.Warning)
                }
                Column(Modifier.padding(start = 13.dp).weight(1f)) {
                    Text(ota.stateLabel, style = MaterialTheme.typography.titleLarge)
                    Text(
                        ota.detail.ifBlank { "No firmware session is active" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = ADColors.Muted,
                    )
                }
            }
            if (otaProgress != null) {
                Spacer(Modifier.height(14.dp))
                LinearProgressIndicator(
                    progress = { (otaProgress / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(7.dp),
                    color = ADColors.Warning,
                    trackColor = ADColors.WarningSoft,
                )
            }
        }
        ADSectionTitle("Preflight")
        ADCard {
            ADFirmwareCheck("HeyCyan profile selected", state.showHeyCyanControls)
            HorizontalDivider(Modifier.padding(start = 34.dp), color = ADColors.Separator)
            ADFirmwareCheck("Bluetooth connected", bluetoothReady)
        }
        if (ota.canStart) {
            ADCard(onClick = { riskAcknowledged = !riskAcknowledged }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (riskAcknowledged) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                        null,
                        tint = if (riskAcknowledged) ADColors.Success else ADColors.Warning,
                    )
                    Text(
                        "I understand firmware update risk",
                        modifier = Modifier.padding(start = 10.dp).weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    ADStatusChip(
                        if (riskAcknowledged) "READY" else "REQUIRED",
                        if (riskAcknowledged) ADStatusTone.SUCCESS else ADStatusTone.WARNING,
                    )
                }
            }
            Button(
                onClick = host.onChooseFirmwareFiles,
                enabled = state.showHeyCyanControls && bluetoothReady && riskAcknowledged,
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ADColors.Warning),
            ) {
                Icon(Icons.Outlined.SystemUpdateAlt, null)
                Spacer(Modifier.width(8.dp))
                Text("Choose firmware files")
            }
        }
        if (ota.canCancel) {
            OutlinedButton(
                onClick = host.onCancelFirmware,
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ADColors.Error),
            ) {
                Icon(Icons.Outlined.StopCircle, null)
                Spacer(Modifier.width(8.dp))
                Text("Cancel update")
            }
        }
    }
}

@Composable
private fun ADFirmwareCheck(title: String, ready: Boolean) {
    Row(Modifier.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (ready) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
            null,
            tint = if (ready) ADColors.Success else ADColors.Muted,
        )
        Text(title, Modifier.padding(start = 10.dp).weight(1f), style = MaterialTheme.typography.bodyLarge)
        ADStatusChip(if (ready) "READY" else "PENDING", if (ready) ADStatusTone.SUCCESS else ADStatusTone.NEUTRAL)
    }
}

@Composable
internal fun ADAutomationDetailScreen(
    automation: ADAutomation,
    isActive: Boolean,
    onBack: () -> Unit,
    onConfigure: () -> Unit,
) {
    var reviewed by remember(automation) { mutableStateOf(false) }
    ADDetailLayout(automation.title, onBack) {
        ADCard {
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.size(48.dp).background(ADColors.BlueSoft, RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.AutoAwesome, null, tint = ADColors.Blue)
                }
                Column(Modifier.padding(start = 13.dp).weight(1f)) {
                    Text(automation.outcome, style = MaterialTheme.typography.labelLarge, color = ADColors.Blue)
                    Text(automation.summary, style = MaterialTheme.typography.bodyLarge)
                }
                ADStatusChip(if (isActive) "ON" else "OFF", if (isActive) ADStatusTone.SUCCESS else ADStatusTone.NEUTRAL)
            }
        }
        ADSectionTitle("Configuration")
        ADCard {
            ADMetricRow(Icons.Outlined.DataUsage, "Processing boundary", automation.boundary)
            HorizontalDivider(Modifier.padding(start = 32.dp), color = ADColors.Separator)
            ADMetricRow(Icons.Outlined.Lock, "Control", "Review before enable; stop at any time")
            HorizontalDivider(Modifier.padding(start = 32.dp), color = ADColors.Separator)
            ADMetricRow(Icons.Outlined.FolderOpen, "Output", automationOutput(automation))
        }
        ADCard(onClick = { reviewed = !reviewed }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (reviewed) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                    null,
                    tint = if (reviewed) ADColors.Success else ADColors.Muted,
                )
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text("Review permissions", style = MaterialTheme.typography.titleMedium)
                    Text("Required before enabling", color = ADColors.Muted)
                }
                Switch(checked = reviewed, onCheckedChange = { reviewed = it })
            }
        }
        Button(
            onClick = onConfigure,
            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ADColors.Ink),
            enabled = reviewed || isActive,
        ) {
            Icon(Icons.Outlined.Settings, null)
            Spacer(Modifier.width(8.dp))
            Text(if (isActive) "Manage automation" else "Review and configure")
        }
    }
}

private fun automationInput(automation: ADAutomation): String = when (automation) {
    ADAutomation.LOCAL_AGENT -> "A request plus explicitly permitted phone context"
    ADAutomation.MEETING_NOTES -> "Phone, Bluetooth or supported onboard audio"
    ADAutomation.LIVE_CAPTIONS -> "Live microphone audio and selected language"
    ADAutomation.TRANSLATOR -> "Source speech and chosen target language"
    ADAutomation.ERRAND_BRAIN -> "Spoken errands after you start listening"
    ADAutomation.AUTO_DIARY -> "Only allowed phone context and apps"
    ADAutomation.AUTO_AUDIO -> "Scheduled HeyCyan onboard recordings"
    ADAutomation.VISUAL_DIARY -> "Explicit captures from a supported camera"
}

private fun automationProcess(automation: ADAutomation): String = when (automation.boundary) {
    "On device" -> "Process locally with the configured on-device model"
    "Your cloud" -> "Send only required input to your configured endpoint"
    else -> "Use automatic routing from your AI service settings"
}

private fun automationOutput(automation: ADAutomation): String = when (automation) {
    ADAutomation.LOCAL_AGENT -> "Approved Android action and honest result"
    ADAutomation.MEETING_NOTES -> "Transcript, summary, decisions and action items"
    ADAutomation.LIVE_CAPTIONS -> "Readable live captions and optional transcript"
    ADAutomation.TRANSLATOR -> "Translated text and optional spoken output"
    ADAutomation.ERRAND_BRAIN -> "Reviewable tasks and confirmed reminders"
    ADAutomation.AUTO_DIARY -> "Private daily facts and summary"
    ADAutomation.AUTO_AUDIO -> "Synced audio, transcript and retention result"
    ADAutomation.VISUAL_DIARY -> "Searchable visual diary entry"
}

@Composable
private fun ADNumberedStep(number: Int, text: String) {
    Row(Modifier.padding(vertical = 9.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(28.dp).background(ADColors.Ink, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text(number.toString(), color = Color.White, style = MaterialTheme.typography.labelMedium) }
        Text(text, Modifier.padding(start = 11.dp, top = 3.dp), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ADMetricRow(icon: ImageVector, label: String, value: String) {
    Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = ADColors.Muted, modifier = Modifier.size(21.dp))
        Text(
            label,
            Modifier.padding(start = 11.dp).weight(0.9f),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            value,
            modifier = Modifier.weight(1.25f),
            style = MaterialTheme.typography.bodyMedium,
            color = ADColors.Muted,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ADDetailLayout(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ADTopBar(title = title, showBack = true, onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            content = content,
        )
    }
}

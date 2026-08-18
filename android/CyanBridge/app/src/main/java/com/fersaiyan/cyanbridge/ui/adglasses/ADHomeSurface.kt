package com.fersaiyan.cyanbridge.ui.adglasses

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fersaiyan.cyanbridge.R
import com.fersaiyan.cyanbridge.ai.orchestrator.AndroidCapabilityCommandExecutor
import com.fersaiyan.cyanbridge.devices.ADDeviceSupportPolicy
import com.fersaiyan.cyanbridge.devices.DeviceProfileStore
import com.fersaiyan.cyanbridge.shared.glasses.GlassesDashboardUiState

/** Quiet control surface for the glasses. */
@Composable
internal fun ADHomeSurface(
    state: GlassesDashboardUiState,
    host: ADHostActions,
    onOpenDevice: () -> Unit,
    onOpenSync: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTranslator: () -> Unit,
    onOpenWebSearch: () -> Unit,
    onOpenPhoneControl: () -> Unit,
    onOpenTasks: () -> Unit,
) {
    val context = LocalContext.current
    val profile = DeviceProfileStore.loadLastSelected(context)
        ?.takeIf { ADDeviceSupportPolicy.isPairable(it.selectedClass) }
    val device = buildADDevicePresentation(state, profile)
    val capabilityExecutor = remember(context) { AndroidCapabilityCommandExecutor(context) }
    val activeListener = capabilityExecutor.activeVoiceCapability()
    val activeListeningCapability = activeListener?.let { running ->
        ADAutomation.entries.firstOrNull { it.toAssistantCapability() == running }
    }

    Column(Modifier.fillMaxSize()) {
        ADTopBar(showBrand = true, showSettings = true, onSettings = onOpenSettings)
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 2.dp,
                bottom = 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                ADReadinessStage(
                    state = state,
                    device = device,
                    onOpenDevice = onOpenDevice,
                    onConnect = when {
                        device.connected -> host.onDisconnect
                        device.shouldOpenSetup -> host.onOpenDeviceSetup
                        else -> host.onReconnect
                    },
                )
            }

            if (state.meeting.isRecording || state.transfer.isVisible || activeListeningCapability != null) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text("Active", style = MaterialTheme.typography.titleMedium)
                        if (state.meeting.isRecording) {
                            ADLiveRow(
                                icon = Icons.Outlined.GraphicEq,
                                title = "Audio recording",
                                detail = state.meeting.bannerLabel.ifBlank { state.meeting.sourceLabel },
                                status = "LIVE",
                                onClick = host.onStopRecording,
                            )
                        }
                        if (state.transfer.isVisible) {
                            ADLiveRow(
                                icon = Icons.Outlined.Sync,
                                title = "Media sync",
                                detail = state.transfer.detail,
                                status = "ACTIVE",
                                onClick = onOpenSync,
                            )
                        }
                        activeListeningCapability?.let { capability ->
                            ADLiveRow(
                                icon = capability.homeActiveIcon(),
                                title = capability.title,
                                detail = "Live listening",
                                status = "ON",
                                onClick = if (capability == ADAutomation.TRANSLATOR) onOpenTranslator else onOpenTasks,
                            )
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ADHomeAction(
                            title = "Ask",
                            detail = "Voice question",
                            icon = Icons.Outlined.Mic,
                            modifier = Modifier.weight(1f),
                            onClick = host.onVoiceQuestion,
                        )
                        ADHomeAction(
                            title = "Photo",
                            detail = "Capture now",
                            icon = Icons.Outlined.PhotoCamera,
                            modifier = Modifier.weight(1f),
                            onClick = host.onCapturePhoto,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ADHomeAction(
                            title = "Video",
                            detail = "Record from glasses",
                            icon = Icons.Outlined.Videocam,
                            modifier = Modifier.weight(1f),
                            onClick = host.onToggleVideo,
                        )
                        ADHomeAction(
                            title = "Translate",
                            detail = "Live Translation",
                            icon = Icons.Rounded.Translate,
                            modifier = Modifier.weight(1f),
                            onClick = onOpenTranslator,
                        )
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    ADHomeLink(
                        icon = Icons.Outlined.Visibility,
                        title = "Ask what I see",
                        detail = "Use the glasses camera with AI",
                        onClick = host.onImageQuestion,
                    )
                    ADHomeLink(
                        icon = Icons.Outlined.GraphicEq,
                        title = if (state.meeting.isRecording) "Stop recording" else "Record audio",
                        detail = if (state.meeting.isRecording) "Recording now" else "Save an audio recording",
                        onClick = if (state.meeting.isRecording) host.onStopRecording else host.onStartRecording,
                    )
                    ADHomeLink(
                        icon = Icons.Outlined.Public,
                        title = "Search web",
                        detail = "Start a fresh web-backed question",
                        onClick = onOpenWebSearch,
                    )
                    ADHomeLink(
                        icon = Icons.Outlined.Bolt,
                        title = "Automation",
                        detail = "Apps and supported Android actions",
                        onClick = onOpenPhoneControl,
                    )
                }
            }
        }
    }
}

@Composable
private fun ADReadinessStage(
    state: GlassesDashboardUiState,
    device: ADDevicePresentation,
    onOpenDevice: () -> Unit,
    onConnect: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ADColors.Surface, RoundedCornerShape(24.dp))
            .clickable(onClick = onOpenDevice),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(166.dp)
                .background(
                    Brush.radialGradient(listOf(Color(0xFFF8FAFD), Color(0xFFE9EDF4))),
                    RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ad_glasses_hero_v4),
                contentDescription = "Glasses",
                modifier = Modifier.fillMaxWidth().height(150.dp).padding(horizontal = 22.dp),
                contentScale = ContentScale.Fit,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 66.dp).padding(horizontal = 15.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (device.connecting) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = ADColors.Blue)
            } else {
                Box(
                    Modifier.size(8.dp).background(if (device.connected) ADColors.Success else ADColors.Muted, CircleShape),
                )
            }
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text(
                    device.statusLabel,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (device.connected && device.identityLabel != null) {
                    Text(
                        device.identityLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = ADColors.Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (device.connected) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (state.showBattery && state.batteryPercent != null) {
                        Icon(Icons.Outlined.BatteryFull, null, tint = ADColors.Muted, modifier = Modifier.size(16.dp))
                        Text("${state.batteryPercent}%", style = MaterialTheme.typography.labelMedium)
                    }
                    if (state.showStorage && state.storageLabel != "--") {
                        Icon(Icons.Outlined.Storage, null, tint = ADColors.Muted, modifier = Modifier.size(16.dp))
                        Text(state.storageLabel, style = MaterialTheme.typography.labelMedium)
                    }
                }
            } else if (!device.connecting) {
                Text(
                    "Connect",
                    style = MaterialTheme.typography.labelLarge,
                    color = ADColors.Blue,
                    modifier = Modifier.clickable(onClick = onConnect).padding(8.dp),
                )
            }
        }
    }
}

@Composable
private fun ADHomeAction(
    title: String,
    detail: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .heightIn(min = 116.dp)
            .background(ADColors.Surface, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(15.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            Modifier.size(40.dp).background(ADColors.SurfaceSubtle, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = ADColors.Ink, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Text(
            detail,
            style = MaterialTheme.typography.bodySmall,
            color = ADColors.Muted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ADHomeLink(
    icon: ImageVector,
    title: String,
    detail: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).background(ADColors.SurfaceSubtle, RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = ADColors.Ink, modifier = Modifier.size(20.dp)) }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = ADColors.Muted, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun ADLiveRow(
    icon: ImageVector,
    title: String,
    detail: String,
    status: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ADColors.Surface, RoundedCornerShape(17.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).background(ADColors.SuccessSoft, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = ADColors.Success, modifier = Modifier.size(20.dp)) }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted, maxLines = 1)
        }
        ADStatusChip(status, ADStatusTone.SUCCESS)
    }
}

private fun ADAutomation.homeActiveIcon(): ImageVector = when (this) {
    ADAutomation.TRANSLATOR -> Icons.Rounded.Translate
    ADAutomation.MEETING_NOTES, ADAutomation.LIVE_CAPTIONS -> Icons.Outlined.GraphicEq
    ADAutomation.ERRAND_BRAIN -> Icons.Outlined.EventRepeat
    ADAutomation.LOCAL_AGENT -> Icons.Outlined.Bolt
    ADAutomation.AUTO_DIARY, ADAutomation.AUTO_AUDIO, ADAutomation.VISUAL_DIARY -> Icons.Outlined.Mic
}

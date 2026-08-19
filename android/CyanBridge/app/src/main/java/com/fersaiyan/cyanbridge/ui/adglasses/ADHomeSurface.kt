package com.fersaiyan.cyanbridge.ui.adglasses

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fersaiyan.cyanbridge.R
import com.fersaiyan.cyanbridge.ai.orchestrator.AndroidCapabilityCommandExecutor
import com.fersaiyan.cyanbridge.ai.orchestrator.AssistantCapability
import com.fersaiyan.cyanbridge.ai.orchestrator.AssistantCapabilityAction
import com.fersaiyan.cyanbridge.ai.orchestrator.AssistantCapabilityCommand
import com.fersaiyan.cyanbridge.ai.orchestrator.AssistantCapabilityRuntimeEvents
import com.fersaiyan.cyanbridge.devices.ADDeviceSupportPolicy
import com.fersaiyan.cyanbridge.devices.DeviceProfileStore
import com.fersaiyan.cyanbridge.shared.glasses.GlassesDashboardUiState

/** Glasses-first control surface. Everyday actions live here; configuration stays elsewhere. */
@Composable
internal fun ADHomeSurface(
    state: GlassesDashboardUiState,
    host: ADHostActions,
    onOpenDevice: () -> Unit,
    onOpenSync: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val profile = DeviceProfileStore.loadLastSelected(context)
        ?.takeIf { ADDeviceSupportPolicy.isPairable(it.selectedClass) }
    val device = buildADDevicePresentation(state, profile)
    val runtimeVersion by AssistantCapabilityRuntimeEvents.version.collectAsState()
    val capabilityExecutor = remember(context, runtimeVersion) { AndroidCapabilityCommandExecutor(context) }
    val translateActive = capabilityExecutor.isActive(AssistantCapability.TRANSLATOR)
    val soundbitesActive = capabilityExecutor.isActive(AssistantCapability.MEETING_NOTES)

    fun toggleCapability(capability: AssistantCapability) {
        val action = if (capabilityExecutor.isActive(capability)) {
            AssistantCapabilityAction.STOP
        } else {
            AssistantCapabilityAction.START
        }
        val result = capabilityExecutor.execute(AssistantCapabilityCommand(capability, action))
        Toast.makeText(context, result.spokenText, Toast.LENGTH_SHORT).show()
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
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

            if (state.meeting.isRecording || state.transfer.isVisible) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ADSectionTitle("Active now")
                        if (state.meeting.isRecording) {
                            ADLiveRow(
                                icon = Icons.Outlined.GraphicEq,
                                title = "Audio recording",
                                detail = state.meeting.bannerLabel.ifBlank { state.meeting.sourceLabel },
                                onClick = host.onStopRecording,
                            )
                        }
                        if (state.transfer.isVisible) {
                            ADLiveRow(
                                icon = Icons.Outlined.Sync,
                                title = "Media sync",
                                detail = state.transfer.detail,
                                onClick = onOpenSync,
                            )
                        }
                    }
                }
            }

            item {
                ADAskAiAction(onClick = host.onVoiceQuestion)
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    ADSectionTitle("Capture")
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        ADCompactAction(
                            title = "Photo",
                            icon = Icons.Outlined.PhotoCamera,
                            modifier = Modifier.weight(1f),
                            onClick = host.onCapturePhoto,
                        )
                        ADCompactAction(
                            title = "Video",
                            icon = Icons.Outlined.Videocam,
                            modifier = Modifier.weight(1f),
                            onClick = host.onToggleVideo,
                        )
                        ADCompactAction(
                            title = if (state.meeting.isRecording) "Stop" else "Audio",
                            icon = Icons.Outlined.GraphicEq,
                            active = state.meeting.isRecording,
                            modifier = Modifier.weight(1f),
                            onClick = if (state.meeting.isRecording) host.onStopRecording else host.onStartRecording,
                        )
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    ADSectionTitle("Smart tools")
                    ADCard {
                        ADSmartToolRow(
                            icon = Icons.Outlined.Visibility,
                            title = "Lens",
                            detail = "Ask about what you’re looking at",
                            onClick = host.onImageQuestion,
                        )
                        HorizontalDivider(Modifier.padding(start = 52.dp), color = ADColors.Separator)
                        ADSmartToolRow(
                            icon = Icons.Rounded.Translate,
                            title = "Translate",
                            detail = "Live conversation",
                            active = translateActive,
                            onClick = { toggleCapability(AssistantCapability.TRANSLATOR) },
                        )
                        HorizontalDivider(Modifier.padding(start = 52.dp), color = ADColors.Separator)
                        ADSmartToolRow(
                            icon = Icons.Outlined.GraphicEq,
                            title = "Soundbites",
                            detail = "Turn speech into notes",
                            active = soundbitesActive,
                            onClick = { toggleCapability(AssistantCapability.MEETING_NOTES) },
                        )
                    }
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
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ADColors.Surface, shape)
            .border(1.dp, ADColors.Outline, shape)
            .clickable(onClick = onOpenDevice),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(154.dp)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0xFFFCFEFE), ADColors.CyanMist, Color(0xFFE7EEF0)),
                    ),
                    RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ad_glasses_hero_v4),
                contentDescription = "Glasses",
                modifier = Modifier.fillMaxWidth().height(138.dp).padding(horizontal = 24.dp),
                contentScale = ContentScale.Fit,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 15.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (device.connecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = ADColors.Cyan,
                )
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
                if (device.identityLabel != null) {
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
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (state.showBattery && state.batteryPercent != null) {
                        ADDeviceMiniMetric(Icons.Outlined.BatteryFull, "${state.batteryPercent}%")
                    }
                    if (state.showStorage && state.storageLabel != "--") {
                        ADDeviceMiniMetric(Icons.Outlined.Storage, state.storageLabel)
                    }
                }
            } else if (!device.connecting) {
                Text(
                    "Connect",
                    style = MaterialTheme.typography.labelLarge,
                    color = ADColors.CyanDeep,
                    modifier = Modifier.clickable(onClick = onConnect).padding(8.dp),
                )
            }
        }
    }
}

@Composable
private fun ADDeviceMiniMetric(icon: ImageVector, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, null, tint = ADColors.Muted, modifier = Modifier.size(16.dp))
        Text(value, style = MaterialTheme.typography.labelMedium, color = ADColors.Ink)
    }
}

@Composable
private fun ADAskAiAction(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 90.dp)
            .background(ADColors.Graphite, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(52.dp).background(Color.White.copy(alpha = 0.11f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Mic, null, tint = Color.White, modifier = Modifier.size(25.dp))
        }
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(
                "Ask AI",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Talk naturally through your glasses",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.70f),
            )
        }
        Box(Modifier.size(8.dp).background(ADColors.Cyan, CircleShape))
    }
}

@Composable
private fun ADCompactAction(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .heightIn(min = 88.dp)
            .background(if (active) ADColors.CyanSoft else ADColors.Surface, shape)
            .border(1.dp, if (active) ADColors.Cyan else ADColors.Outline, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            modifier = Modifier.size(34.dp).background(
                if (active) ADColors.Cyan else ADColors.SurfaceSubtle,
                RoundedCornerShape(10.dp),
            ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                null,
                tint = if (active) Color.White else ADColors.Ink,
                modifier = Modifier.size(19.dp),
            )
        }
        Text(title, style = MaterialTheme.typography.labelLarge, color = ADColors.Ink, maxLines = 1)
    }
}

@Composable
private fun ADSmartToolRow(
    icon: ImageVector,
    title: String,
    detail: String,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(if (active) ADColors.CyanSoft else ADColors.SurfaceSubtle, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                null,
                tint = if (active) ADColors.CyanDeep else ADColors.Ink,
                modifier = Modifier.size(21.dp),
            )
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted, maxLines = 1)
        }
        if (active) ADStatusChip("ON", ADStatusTone.INFO)
    }
}

@Composable
private fun ADLiveRow(
    icon: ImageVector,
    title: String,
    detail: String,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ADColors.Surface, shape)
            .border(1.dp, ADColors.Outline, shape)
            .clickable(onClick = onClick)
            .padding(13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).background(ADColors.SuccessSoft, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = ADColors.Success, modifier = Modifier.size(20.dp)) }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted, maxLines = 1)
        }
        ADStatusChip("LIVE", ADStatusTone.SUCCESS)
    }
}

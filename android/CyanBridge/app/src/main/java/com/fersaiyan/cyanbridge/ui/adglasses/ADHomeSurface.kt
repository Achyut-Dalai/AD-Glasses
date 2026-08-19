package com.fersaiyan.cyanbridge.ui.adglasses

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.outlined.AutoAwesome
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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

            if (state.meeting.isRecording || state.transfer.isVisible) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text("Active", style = MaterialTheme.typography.titleMedium)
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
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ADHomeAction(
                            title = "Ask AI",
                            icon = Icons.Outlined.AutoAwesome,
                            modifier = Modifier.weight(1f),
                            onClick = host.onVoiceQuestion,
                        )
                        ADHomeAction(
                            title = "Photo",
                            icon = Icons.Outlined.PhotoCamera,
                            modifier = Modifier.weight(1f),
                            onClick = host.onCapturePhoto,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ADHomeAction(
                            title = "Video",
                            icon = Icons.Outlined.Videocam,
                            modifier = Modifier.weight(1f),
                            onClick = host.onToggleVideo,
                        )
                        ADHomeAction(
                            title = "Translate",
                            icon = Icons.Rounded.Translate,
                            active = translateActive,
                            modifier = Modifier.weight(1f),
                            onClick = { toggleCapability(AssistantCapability.TRANSLATOR) },
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ADHomeAction(
                            title = "Soundbites",
                            icon = Icons.Outlined.GraphicEq,
                            active = soundbitesActive,
                            modifier = Modifier.weight(1f),
                            onClick = { toggleCapability(AssistantCapability.MEETING_NOTES) },
                        )
                        ADHomeAction(
                            title = "Audio",
                            icon = Icons.Outlined.Mic,
                            active = state.meeting.isRecording,
                            modifier = Modifier.weight(1f),
                            onClick = if (state.meeting.isRecording) host.onStopRecording else host.onStartRecording,
                        )
                    }
                }
            }

            item {
                ADSmartLensCard(onClick = host.onImageQuestion)
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
    icon: ImageVector,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    val container = if (active) ADColors.SurfaceSubtle else ADColors.Surface
    val iconContainer = if (active) ADColors.Ink else ADColors.SurfaceSubtle
    val iconColor = if (active) ADColors.Surface else ADColors.Ink

    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 126.dp),
        shape = RoundedCornerShape(26.dp),
        color = container,
        border = BorderStroke(1.dp, if (active) ADColors.Ink.copy(alpha = 0.18f) else ADColors.Separator),
        shadowElevation = if (active) 2.dp else 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                if (active) {
                    Box(Modifier.size(8.dp).background(ADColors.Ink, CircleShape))
                }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .size(60.dp)
                        .background(iconContainer, RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, null, tint = iconColor, modifier = Modifier.size(29.dp))
                }
            }
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ADSmartLensCard(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 108.dp)
            .background(ADColors.Ink, RoundedCornerShape(26.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "Lens",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = ADColors.Surface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Ask about what you’re looking at",
                style = MaterialTheme.typography.bodyMedium,
                color = ADColors.Surface.copy(alpha = 0.70f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(ADColors.Surface.copy(alpha = 0.12f), RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Visibility,
                contentDescription = null,
                tint = ADColors.Surface,
                modifier = Modifier.size(29.dp),
            )
        }
    }
}

@Composable
private fun ADLiveRow(
    icon: ImageVector,
    title: String,
    detail: String,
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
    }
}

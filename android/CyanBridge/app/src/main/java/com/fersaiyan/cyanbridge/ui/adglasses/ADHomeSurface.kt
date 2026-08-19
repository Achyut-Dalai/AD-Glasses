package com.fersaiyan.cyanbridge.ui.adglasses

import android.widget.Toast
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.CircularProgressIndicator
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

private enum class ADHomeArtwork {
    ASK_AI,
    PHOTO,
    VIDEO,
    TRANSLATE,
    SOUNDBITES,
    AUDIO,
}

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
                start = 14.dp,
                end = 14.dp,
                top = 0.dp,
                bottom = 14.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
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
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ADHomeAction(
                            title = "Ask AI",
                            detail = "Ask by voice",
                            artwork = ADHomeArtwork.ASK_AI,
                            modifier = Modifier.weight(1f),
                            onClick = host.onVoiceQuestion,
                        )
                        ADHomeAction(
                            title = "Photo",
                            detail = "Take a photo",
                            artwork = ADHomeArtwork.PHOTO,
                            modifier = Modifier.weight(1f),
                            onClick = host.onCapturePhoto,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ADHomeAction(
                            title = "Video",
                            detail = "Record from glasses",
                            artwork = ADHomeArtwork.VIDEO,
                            modifier = Modifier.weight(1f),
                            onClick = host.onToggleVideo,
                        )
                        ADHomeAction(
                            title = "Translate",
                            detail = "Live conversation",
                            artwork = ADHomeArtwork.TRANSLATE,
                            active = translateActive,
                            modifier = Modifier.weight(1f),
                            onClick = { toggleCapability(AssistantCapability.TRANSLATOR) },
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ADHomeAction(
                            title = "Soundbites",
                            detail = "Turn speech into notes",
                            artwork = ADHomeArtwork.SOUNDBITES,
                            active = soundbitesActive,
                            modifier = Modifier.weight(1f),
                            onClick = { toggleCapability(AssistantCapability.MEETING_NOTES) },
                        )
                        ADHomeAction(
                            title = "Audio",
                            detail = if (state.meeting.isRecording) "Stop recording" else "Start recording",
                            artwork = ADHomeArtwork.AUDIO,
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
            .background(ADColors.Surface, RoundedCornerShape(20.dp))
            .clickable(onClick = onOpenDevice),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(126.dp)
                .background(
                    Brush.radialGradient(listOf(Color(0xFFF8FAFD), Color(0xFFE9EDF4))),
                    RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ad_glasses_hero_v4),
                contentDescription = "Glasses",
                modifier = Modifier.fillMaxWidth().height(116.dp).padding(horizontal = 18.dp),
                contentScale = ContentScale.Fit,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 11.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (device.connecting) {
                CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp, color = ADColors.Blue)
            } else {
                Box(
                    Modifier.size(7.dp).background(if (device.connected) ADColors.Success else ADColors.Muted, CircleShape),
                )
            }
            Column(Modifier.padding(start = 8.dp).weight(1f)) {
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
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (state.showBattery && state.batteryPercent != null) {
                        Icon(Icons.Outlined.BatteryFull, null, tint = ADColors.Muted, modifier = Modifier.size(14.dp))
                        Text("${state.batteryPercent}%", style = MaterialTheme.typography.labelMedium)
                    }
                    if (state.showStorage && state.storageLabel != "--") {
                        Icon(Icons.Outlined.Storage, null, tint = ADColors.Muted, modifier = Modifier.size(14.dp))
                        Text(state.storageLabel, style = MaterialTheme.typography.labelMedium)
                    }
                }
            } else if (!device.connecting) {
                Text(
                    "Connect",
                    style = MaterialTheme.typography.labelLarge,
                    color = ADColors.Blue,
                    modifier = Modifier.clickable(onClick = onConnect).padding(6.dp),
                )
            }
        }
    }
}

@Composable
private fun ADHomeAction(
    title: String,
    detail: String,
    artwork: ADHomeArtwork,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    val container = if (active) ADColors.SurfaceSubtle else ADColors.Surface

    Column(
        modifier = modifier
            .heightIn(min = 88.dp)
            .background(container, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        ADHomeArtworkIcon(artwork = artwork, active = active)
        Spacer(Modifier.height(5.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        Spacer(Modifier.height(1.dp))
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
private fun ADHomeArtworkIcon(
    artwork: ADHomeArtwork,
    active: Boolean,
) {
    val container = if (active) ADColors.Ink else ADColors.SurfaceSubtle
    val ink = if (active) ADColors.Surface else ADColors.Ink
    val cutout = if (active) ADColors.Ink else ADColors.SurfaceSubtle

    Box(
        modifier = Modifier
            .size(40.dp)
            .background(container, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        when (artwork) {
            ADHomeArtwork.ASK_AI -> Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Box(Modifier.width(17.dp).height(4.dp).background(ink, RoundedCornerShape(3.dp)))
                    Box(Modifier.width(12.dp).height(4.dp).background(ink.copy(alpha = 0.55f), RoundedCornerShape(3.dp)))
                }
                Box(Modifier.size(7.dp).background(ink, CircleShape))
            }

            ADHomeArtwork.PHOTO -> Box(
                modifier = Modifier
                    .width(25.dp)
                    .height(21.dp)
                    .background(ink, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .width(19.dp)
                        .height(15.dp)
                        .background(cutout, RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(7.dp).background(ink, CircleShape))
                }
            }

            ADHomeArtwork.VIDEO -> Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .width(21.dp)
                        .height(17.dp)
                        .background(ink, RoundedCornerShape(5.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.width(13.dp).height(9.dp).background(cutout, RoundedCornerShape(3.dp)))
                }
                Box(Modifier.width(5.dp).height(12.dp).background(ink.copy(alpha = 0.60f), RoundedCornerShape(3.dp)))
            }

            ADHomeArtwork.TRANSLATE -> Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("A", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = ink)
                Box(Modifier.width(2.dp).height(18.dp).background(ink.copy(alpha = 0.28f), RoundedCornerShape(2.dp)))
                Text("文", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium, color = ink)
            }

            ADHomeArtwork.SOUNDBITES -> Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(4.dp).height(10.dp).background(ink.copy(alpha = 0.48f), RoundedCornerShape(3.dp)))
                Box(Modifier.width(4.dp).height(20.dp).background(ink, RoundedCornerShape(3.dp)))
                Box(Modifier.width(4.dp).height(15.dp).background(ink.copy(alpha = 0.70f), RoundedCornerShape(3.dp)))
                Box(Modifier.width(4.dp).height(24.dp).background(ink, RoundedCornerShape(3.dp)))
                Box(Modifier.width(4.dp).height(12.dp).background(ink.copy(alpha = 0.48f), RoundedCornerShape(3.dp)))
            }

            ADHomeArtwork.AUDIO -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Box(
                    Modifier
                        .width(11.dp)
                        .height(19.dp)
                        .background(ink, RoundedCornerShape(7.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.width(5.dp).height(11.dp).background(cutout, RoundedCornerShape(4.dp)))
                }
                Box(Modifier.width(17.dp).height(3.dp).background(ink.copy(alpha = 0.62f), RoundedCornerShape(2.dp)))
            }
        }
    }
}

@Composable
private fun ADSmartLensCard(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 78.dp)
            .background(ADColors.Ink, RoundedCornerShape(21.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "Lens",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                color = ADColors.Surface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Ask about what you’re looking at",
                style = MaterialTheme.typography.bodySmall,
                color = ADColors.Surface.copy(alpha = 0.70f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                .size(46.dp)
                .background(ADColors.Surface.copy(alpha = 0.12f), RoundedCornerShape(15.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Visibility,
                contentDescription = null,
                tint = ADColors.Surface,
                modifier = Modifier.size(22.dp),
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
            .background(ADColors.Surface, RoundedCornerShape(15.dp))
            .clickable(onClick = onClick)
            .padding(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(32.dp).background(ADColors.SuccessSoft, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = ADColors.Success, modifier = Modifier.size(17.dp)) }
        Column(Modifier.padding(start = 8.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted, maxLines = 1)
        }
    }
}

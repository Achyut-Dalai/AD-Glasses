package com.fersaiyan.cyanbridge.ui.adglasses

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Sync
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
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 4.dp,
                bottom = 22.dp,
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
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ADSectionTitle("Live now")
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
                    ADSectionTitle("Quick actions")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ADHomeAction(
                            title = "Ask AI",
                            detail = "Ask by voice",
                            glyph = ADGlyph.ASK,
                            modifier = Modifier.weight(1f),
                            onClick = host.onVoiceQuestion,
                        )
                        ADHomeAction(
                            title = "Photo",
                            detail = "Take a photo",
                            glyph = ADGlyph.PHOTO,
                            modifier = Modifier.weight(1f),
                            onClick = host.onCapturePhoto,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ADHomeAction(
                            title = "Video",
                            detail = "Record from glasses",
                            glyph = ADGlyph.VIDEO,
                            modifier = Modifier.weight(1f),
                            onClick = host.onToggleVideo,
                        )
                        ADHomeAction(
                            title = "Translate",
                            detail = "Live conversation",
                            glyph = ADGlyph.TRANSLATE,
                            active = translateActive,
                            modifier = Modifier.weight(1f),
                            onClick = { toggleCapability(AssistantCapability.TRANSLATOR) },
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ADHomeAction(
                            title = "Soundbites",
                            detail = "Speech into notes",
                            glyph = ADGlyph.SOUNDBITES,
                            active = soundbitesActive,
                            modifier = Modifier.weight(1f),
                            onClick = { toggleCapability(AssistantCapability.MEETING_NOTES) },
                        )
                        ADHomeAction(
                            title = "Audio",
                            detail = if (state.meeting.isRecording) "Stop recording" else "Start recording",
                            glyph = ADGlyph.AUDIO,
                            active = state.meeting.isRecording,
                            modifier = Modifier.weight(1f),
                            onClick = if (state.meeting.isRecording) host.onStopRecording else host.onStartRecording,
                        )
                    }
                }
            }

            item { ADSmartLensCard(onClick = host.onImageQuestion) }
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
    Surface(
        onClick = onOpenDevice,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        color = ADColors.Surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 2.dp,
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color(0xFFFFFFFF), Color(0xFFE8E9ED)),
                        ),
                        RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
                    ),
            ) {
                Text(
                    "YOUR GLASSES",
                    style = MaterialTheme.typography.labelSmall,
                    color = ADColors.Muted,
                    modifier = Modifier.padding(start = 18.dp, top = 16.dp),
                )
                Image(
                    painter = painterResource(R.drawable.ad_glasses_hero_v4),
                    contentDescription = "Glasses",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(158.dp)
                        .align(Alignment.Center)
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                    contentScale = ContentScale.Fit,
                )
            }

            Column(Modifier.padding(horizontal = 16.dp, vertical = 15.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (device.connecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(17.dp),
                            strokeWidth = 2.dp,
                            color = ADColors.Ink,
                        )
                    } else {
                        Box(
                            Modifier
                                .size(9.dp)
                                .background(if (device.connected) ADColors.Success else ADColors.Muted, CircleShape),
                        )
                    }
                    Column(Modifier.padding(start = 9.dp).weight(1f)) {
                        Text(
                            device.statusLabel,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val identity = device.identityLabel
                        if (!identity.isNullOrBlank()) {
                            Text(
                                identity,
                                style = MaterialTheme.typography.bodySmall,
                                color = ADColors.Muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (!device.connected && !device.connecting) {
                        Surface(
                            onClick = onConnect,
                            shape = CircleShape,
                            color = ADColors.Ink,
                            contentColor = ADColors.Surface,
                        ) {
                            Text(
                                "Connect",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                            )
                        }
                    }
                }

                if (device.connected &&
                    ((state.showBattery && state.batteryPercent != null) ||
                        (state.showStorage && state.storageLabel != "--"))
                ) {
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.showBattery && state.batteryPercent != null) {
                            ADHomeMetric(
                                icon = Icons.Outlined.BatteryFull,
                                label = "Battery",
                                value = "${state.batteryPercent}%",
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (state.showStorage && state.storageLabel != "--") {
                            ADHomeMetric(
                                icon = Icons.Outlined.Storage,
                                label = "Storage",
                                value = state.storageLabel,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ADHomeMetric(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(ADColors.SurfaceSubtle, RoundedCornerShape(15.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = ADColors.Ink, modifier = Modifier.size(17.dp))
        Column(Modifier.padding(start = 7.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = ADColors.Muted)
            Text(value, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

@Composable
private fun ADHomeAction(
    title: String,
    detail: String,
    glyph: ADGlyph,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    val container by animateColorAsState(
        targetValue = if (active) ADColors.Ink else ADColors.Surface,
        label = "home-action-container",
    )
    val iconContainer by animateColorAsState(
        targetValue = if (active) ADColors.Surface.copy(alpha = 0.14f) else ADColors.SurfaceSubtle,
        label = "home-action-icon-container",
    )
    val foreground by animateColorAsState(
        targetValue = if (active) ADColors.Surface else ADColors.Ink,
        label = "home-action-foreground",
    )
    val secondary by animateColorAsState(
        targetValue = if (active) ADColors.Surface.copy(alpha = 0.68f) else ADColors.Muted,
        label = "home-action-secondary",
    )

    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 120.dp),
        shape = RoundedCornerShape(24.dp),
        color = container,
        contentColor = foreground,
        border = if (active) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = if (active) 2.dp else 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(46.dp).background(iconContainer, RoundedCornerShape(15.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    ADGlyphIcon(glyph, foreground, Modifier.size(25.dp))
                }
                Spacer(Modifier.weight(1f))
                if (active) {
                    Text("ACTIVE", style = MaterialTheme.typography.labelSmall, color = secondary)
                }
            }
            Spacer(Modifier.height(17.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = foreground, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = secondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ADSmartLensCard(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 112.dp),
        shape = RoundedCornerShape(30.dp),
        color = ADColors.Ink,
        contentColor = ADColors.Surface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "LENS",
                    style = MaterialTheme.typography.labelSmall,
                    color = ADColors.Surface.copy(alpha = 0.62f),
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    "Look at it. Ask about it.",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = ADColors.Surface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Use what your glasses see as context",
                    style = MaterialTheme.typography.bodySmall,
                    color = ADColors.Surface.copy(alpha = 0.70f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .background(ADColors.Surface.copy(alpha = 0.13f), RoundedCornerShape(19.dp)),
                contentAlignment = Alignment.Center,
            ) {
                ADGlyphIcon(ADGlyph.LENS, ADColors.Surface, Modifier.size(30.dp))
            }
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
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = ADColors.Surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).background(ADColors.Ink, RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = ADColors.Surface, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = ADColors.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text("LIVE", style = MaterialTheme.typography.labelSmall, color = ADColors.Success)
        }
    }
}

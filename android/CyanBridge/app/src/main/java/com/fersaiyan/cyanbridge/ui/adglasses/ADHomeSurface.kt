package com.fersaiyan.cyanbridge.ui.adglasses

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fersaiyan.cyanbridge.ai.orchestrator.AndroidCapabilityCommandExecutor
import com.fersaiyan.cyanbridge.ai.orchestrator.AssistantCapability
import com.fersaiyan.cyanbridge.ai.orchestrator.AssistantCapabilityAction
import com.fersaiyan.cyanbridge.ai.orchestrator.AssistantCapabilityCommand
import com.fersaiyan.cyanbridge.ai.orchestrator.AssistantCapabilityRuntimeEvents
import com.fersaiyan.cyanbridge.devices.ADDeviceSupportPolicy
import com.fersaiyan.cyanbridge.devices.DeviceProfileStore
import com.fersaiyan.cyanbridge.shared.glasses.GlassesDashboardUiState

/** Compact glasses-first control surface. */
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
                start = 12.dp,
                end = 12.dp,
                top = 4.dp,
                bottom = 18.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(11.dp),
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
                        ADSectionTitle("Live now")
                        if (state.meeting.isRecording) {
                            ADLiveRow(
                                title = "Audio recording",
                                detail = state.meeting.bannerLabel.ifBlank { state.meeting.sourceLabel },
                                live = true,
                                onClick = host.onStopRecording,
                            )
                        }
                        if (state.transfer.isVisible) {
                            ADLiveRow(
                                title = "Media sync",
                                detail = state.transfer.detail,
                                live = false,
                                onClick = onOpenSync,
                            )
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    ADSectionTitle("Quick actions")
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        ADHomeAction(
                            title = "Ask AI",
                            detail = "Voice question",
                            glyph = ADGlyph.ASK,
                            modifier = Modifier.weight(1f),
                            onClick = host.onVoiceQuestion,
                        )
                        ADHomeAction(
                            title = "Photo",
                            detail = "Capture",
                            glyph = ADGlyph.PHOTO,
                            modifier = Modifier.weight(1f),
                            onClick = host.onCapturePhoto,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        ADHomeAction(
                            title = "Video",
                            detail = "Record",
                            glyph = ADGlyph.VIDEO,
                            modifier = Modifier.weight(1f),
                            onClick = host.onToggleVideo,
                        )
                        ADHomeAction(
                            title = "Translate",
                            detail = "Live speech",
                            glyph = ADGlyph.TRANSLATE,
                            active = translateActive,
                            modifier = Modifier.weight(1f),
                            onClick = { toggleCapability(AssistantCapability.TRANSLATOR) },
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        ADHomeAction(
                            title = "Soundbites",
                            detail = "Speech notes",
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
        shape = RoundedCornerShape(17.dp),
        color = Color.Black.copy(alpha = 0.40f),
        border = BorderStroke(1.dp, ADColors.Outline),
    ) {
        Column(Modifier.padding(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(ADColors.SurfaceSubtle, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    ADGlyphIcon(
                        glyph = ADGlyph.DEVICE,
                        tint = ADColors.Ink,
                        modifier = Modifier.size(48.dp),
                        accent = ADColors.Red,
                    )
                }

                Column(Modifier.padding(start = 11.dp).weight(1f)) {
                    Text("YOUR GLASSES", style = MaterialTheme.typography.labelSmall, color = ADColors.Muted)
                    Spacer(Modifier.size(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (device.connecting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 1.5.dp,
                                color = ADColors.Ink,
                            )
                        } else {
                            Box(
                                Modifier.size(6.dp).background(
                                    if (device.connected) ADColors.Success else ADColors.Red,
                                    CircleShape,
                                ),
                            )
                        }
                        Text(
                            device.statusLabel,
                            modifier = Modifier.padding(start = 6.dp),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    val identity = device.identityLabel
                    if (!identity.isNullOrBlank()) {
                        Text(identity, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted, maxLines = 1)
                    }
                }

                if (!device.connected && !device.connecting) {
                    Surface(
                        onClick = onConnect,
                        shape = RoundedCornerShape(10.dp),
                        color = ADColors.Ink,
                        contentColor = Color.Black,
                    ) {
                        Text(
                            "Connect",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        )
                    }
                }
            }

            if (device.connected &&
                ((state.showBattery && state.batteryPercent != null) ||
                    (state.showStorage && state.storageLabel != "--"))
            ) {
                Spacer(Modifier.size(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    if (state.showBattery && state.batteryPercent != null) {
                        ADHomeMetric("BATTERY", "${state.batteryPercent}%", Modifier.weight(1f))
                    }
                    if (state.showStorage && state.storageLabel != "--") {
                        ADHomeMetric("STORAGE", state.storageLabel, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ADHomeMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(ADColors.SurfaceSubtle, RoundedCornerShape(10.dp))
            .padding(horizontal = 9.dp, vertical = 7.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = ADColors.Muted)
        Text(value, style = MaterialTheme.typography.labelLarge, maxLines = 1)
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
        targetValue = if (active) ADColors.SurfacePressed else ADColors.Surface.copy(alpha = 0.88f),
        label = "home-action-container",
    )
    val foreground by animateColorAsState(
        targetValue = ADColors.Ink,
        label = "home-action-foreground",
    )

    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 88.dp),
        shape = RoundedCornerShape(14.dp),
        color = container,
        border = BorderStroke(1.dp, if (active) ADColors.Ink.copy(alpha = 0.28f) else ADColors.Outline),
    ) {
        Column(
            modifier = Modifier.padding(11.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ADGlyphIcon(
                    glyph = glyph,
                    tint = foreground,
                    modifier = Modifier.size(22.dp),
                    accent = if (active || glyph == ADGlyph.ASK || glyph == ADGlyph.VIDEO || glyph == ADGlyph.AUDIO) ADColors.Red else null,
                )
                Spacer(Modifier.weight(1f))
                if (active) Box(Modifier.size(5.dp).background(ADColors.Red, CircleShape))
            }
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, color = foreground, maxLines = 1)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ADSmartLensCard(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
        shape = RoundedCornerShape(14.dp),
        color = ADColors.Red,
        contentColor = Color.White,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ADGlyphIcon(ADGlyph.LENS, Color.White, Modifier.size(24.dp))
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text("LENS", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.72f))
                Text("Look at it. Ask about it.", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ADLiveRow(
    title: String,
    detail: String,
    live: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = ADColors.Surface.copy(alpha = 0.90f),
        border = BorderStroke(1.dp, ADColors.Outline),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (live) {
                Box(Modifier.size(7.dp).background(ADColors.Red, CircleShape))
            } else {
                Icon(Icons.Outlined.Sync, null, tint = ADColors.Ink, modifier = Modifier.size(16.dp))
            }
            Column(Modifier.padding(start = 9.dp).weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

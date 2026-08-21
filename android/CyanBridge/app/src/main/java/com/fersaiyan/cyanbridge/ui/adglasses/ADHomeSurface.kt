package com.fersaiyan.cyanbridge.ui.adglasses

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Videocam
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

@Composable
internal fun ADHomeSurface(
    state: GlassesDashboardUiState,
    host: ADHostActions,
    onOpenSettings: () -> Unit,
    onOpenSync: () -> Unit,
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

    val glassesDetail = when {
        device.connecting -> "Connecting"
        device.connected -> device.identityLabel ?: "Connected"
        else -> "Tap to connect"
    }
    val glassesAction = when {
        device.connected -> host.onDisconnect
        device.shouldOpenSetup -> host.onOpenDeviceSetup
        else -> host.onReconnect
    }
    val recordingDetail = if (state.meeting.isRecording) {
        state.meeting.bannerLabel.ifBlank { "Recording now" }
    } else {
        "Capture audio"
    }
    val syncDetail = if (state.transfer.isVisible) {
        state.transfer.detail.ifBlank { "Transfer active" }
    } else {
        "Bring media over"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = 8.dp,
            bottom = 18.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ADHomeHeader(onOpenSettings = onOpenSettings)
        }

        item {
            ADFloatingGlassesHero()
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ADSectionTitle("Controls")

                ADHomeControlRow {
                    ADHomeControlTile(
                        icon = Icons.Outlined.Bluetooth,
                        title = "Glasses",
                        detail = glassesDetail,
                        active = device.connected || device.connecting,
                        modifier = Modifier.weight(1f),
                        onClick = glassesAction,
                    )
                    ADHomeControlTile(
                        icon = Icons.Outlined.CenterFocusStrong,
                        title = "Lens",
                        detail = "See and ask",
                        modifier = Modifier.weight(1f),
                        onClick = host.onImageQuestion,
                    )
                }

                ADHomeControlRow {
                    ADHomeControlTile(
                        icon = Icons.Outlined.PhotoCamera,
                        title = "Photo",
                        detail = "Capture a still",
                        modifier = Modifier.weight(1f),
                        onClick = host.onCapturePhoto,
                    )
                    ADHomeControlTile(
                        icon = Icons.Outlined.Videocam,
                        title = "Video",
                        detail = "Start or stop",
                        modifier = Modifier.weight(1f),
                        onClick = host.onToggleVideo,
                    )
                }

                ADHomeControlRow {
                    ADHomeControlTile(
                        icon = Icons.Outlined.AutoAwesome,
                        title = "Ask AI",
                        detail = "Ask by voice",
                        modifier = Modifier.weight(1f),
                        onClick = host.onVoiceQuestion,
                    )
                    ADHomeControlTile(
                        icon = Icons.Outlined.Translate,
                        title = "Translate",
                        detail = if (translateActive) "Listening" else "Live translation",
                        active = translateActive,
                        modifier = Modifier.weight(1f),
                        onClick = { toggleCapability(AssistantCapability.TRANSLATOR) },
                    )
                }

                ADHomeControlRow {
                    ADHomeControlTile(
                        icon = Icons.Outlined.Mic,
                        title = "Record",
                        detail = recordingDetail,
                        active = state.meeting.isRecording,
                        modifier = Modifier.weight(1f),
                        onClick = if (state.meeting.isRecording) host.onStopRecording else host.onStartRecording,
                    )
                    ADHomeControlTile(
                        icon = Icons.Outlined.GraphicEq,
                        title = "Soundbites",
                        detail = if (soundbitesActive) "Active" else "Meeting notes",
                        active = soundbitesActive,
                        modifier = Modifier.weight(1f),
                        onClick = { toggleCapability(AssistantCapability.MEETING_NOTES) },
                    )
                }

                ADHomeControlTile(
                    icon = Icons.Outlined.Sync,
                    title = "Sync",
                    detail = syncDetail,
                    active = state.transfer.isVisible,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onOpenSync,
                )
            }
        }
    }
}

@Composable
private fun ADHomeHeader(onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "AD GLASSES",
                style = ADMetaTextStyle,
                color = ADColors.Muted,
            )
            Text(
                "Home",
                style = MaterialTheme.typography.headlineLarge,
                color = ADColors.Ink,
            )
        }
        Surface(
            onClick = onOpenSettings,
            modifier = Modifier.size(42.dp),
            shape = CircleShape,
            color = ADColors.Surface,
            contentColor = ADColors.Ink,
            border = BorderStroke(1.dp, ADColors.Outline),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Outlined.Settings,
                    contentDescription = "Settings",
                    tint = ADColors.Ink,
                    modifier = Modifier.size(21.dp),
                )
            }
        }
    }
}

@Composable
private fun ADFloatingGlassesHero() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ad_glasses_hero_v4),
            contentDescription = "AD Glasses",
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 5.dp),
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
private fun ADHomeControlRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun ADHomeControlTile(
    icon: ImageVector,
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 106.dp),
        shape = RoundedCornerShape(18.dp),
        color = if (active) ADColors.SurfacePressed else ADColors.Surface,
        contentColor = ADColors.Ink,
        border = BorderStroke(1.dp, if (active) ADColors.Ink.copy(alpha = 0.22f) else ADColors.Outline),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(39.dp),
                    shape = CircleShape,
                    color = if (active) ADColors.Red else ADColors.SurfaceSubtle,
                    contentColor = ADColors.Ink,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = ADColors.Ink,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                if (active) {
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.size(6.dp).background(ADColors.Red, CircleShape))
                }
            }
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = ADColors.Ink,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = ADColors.Muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

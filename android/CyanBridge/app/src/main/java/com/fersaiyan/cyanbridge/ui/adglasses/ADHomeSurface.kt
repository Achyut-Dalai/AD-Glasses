package com.fersaiyan.cyanbridge.ui.adglasses

import android.content.Intent
import android.provider.Settings
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.fersaiyan.cyanbridge.ai.orchestrator.AssistantCapability
import com.fersaiyan.cyanbridge.ai.orchestrator.AssistantCapabilityAction
import com.fersaiyan.cyanbridge.ai.orchestrator.AssistantCapabilityCommand
import com.fersaiyan.cyanbridge.ai.orchestrator.AssistantCapabilityRuntimeEvents
import com.fersaiyan.cyanbridge.ai.orchestrator.AssistantWebMode
import com.fersaiyan.cyanbridge.ai.orchestrator.AssistantWebModePreferences
import com.fersaiyan.cyanbridge.devices.ADDeviceSupportPolicy
import com.fersaiyan.cyanbridge.devices.DeviceProfileStore
import com.fersaiyan.cyanbridge.shared.glasses.GlassesDashboardUiState
import com.fersaiyan.cyanbridge.ui.hasAccessibilityServicePermission

/** Primary control surface: what the glasses and assistant can do right now. */
@Composable
internal fun ADHomeSurface(
    state: GlassesDashboardUiState,
    host: ADHostActions,
    onOpenDevice: () -> Unit,
    onOpenSync: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val runtimeVersion by AssistantCapabilityRuntimeEvents.version.collectAsState()
    val capabilityExecutor = remember(context, runtimeVersion) { AndroidCapabilityCommandExecutor(context) }
    val profile = DeviceProfileStore.loadLastSelected(context)
        ?.takeIf { ADDeviceSupportPolicy.isPairable(it.selectedClass) }
    val device = buildADDevicePresentation(state, profile)
    var webMode by remember(context) { mutableStateOf(AssistantWebModePreferences.get(context)) }
    var capabilityFeedback by remember { mutableStateOf<String?>(null) }

    fun toggleCapability(capability: AssistantCapability) {
        val enable = !capabilityExecutor.isActive(capability)
        val result = capabilityExecutor.execute(
            AssistantCapabilityCommand(
                capability = capability,
                action = if (enable) AssistantCapabilityAction.START else AssistantCapabilityAction.STOP,
            ),
        )
        capabilityFeedback = result.spokenText
    }

    fun capabilityActive(capability: AssistantCapability): Boolean = capabilityExecutor.isActive(capability)

    val automationReady = hasAccessibilityServicePermission(context)

    Column(Modifier.fillMaxSize()) {
        ADTopBar(showBrand = true, showSettings = true, onSettings = onOpenSettings)
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 2.dp,
                bottom = 30.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp),
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
                        ADSectionLabel("ACTIVE")
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
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ADSectionLabel("AI ON YOUR GLASSES")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ADCapabilityTile(
                            title = "Ask AI",
                            detail = "Ask with your voice",
                            icon = Icons.Outlined.Mic,
                            modifier = Modifier.weight(1f),
                            onClick = host.onVoiceQuestion,
                        )
                        ADCapabilityTile(
                            title = "What I see",
                            detail = "Ask using the glasses camera",
                            icon = Icons.Outlined.Visibility,
                            modifier = Modifier.weight(1f),
                            onClick = host.onImageQuestion,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ADCapabilityTile(
                            title = "Web",
                            detail = if (webMode == AssistantWebMode.ON) {
                                "Web preferred for every AI question"
                            } else {
                                "Automatic when freshness matters"
                            },
                            icon = Icons.Outlined.Public,
                            status = if (webMode == AssistantWebMode.ON) "ON" else "AUTO",
                            active = webMode == AssistantWebMode.ON,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                webMode = if (webMode == AssistantWebMode.ON) AssistantWebMode.AUTO else AssistantWebMode.ON
                                AssistantWebModePreferences.set(context, webMode)
                                capabilityFeedback = if (webMode == AssistantWebMode.ON) {
                                    "Web is on for the assistant, including glasses questions."
                                } else {
                                    "Web returned to automatic mode."
                                }
                            },
                        )
                        ADCapabilityTile(
                            title = "Translate",
                            detail = if (capabilityActive(AssistantCapability.TRANSLATOR)) "Live translation is listening" else "Live conversation translation",
                            icon = Icons.Rounded.Translate,
                            status = if (capabilityActive(AssistantCapability.TRANSLATOR)) "ON" else "OFF",
                            active = capabilityActive(AssistantCapability.TRANSLATOR),
                            modifier = Modifier.weight(1f),
                            onClick = { toggleCapability(AssistantCapability.TRANSLATOR) },
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ADCapabilityTile(
                            title = "Soundbites",
                            detail = if (capabilityActive(AssistantCapability.MEETING_NOTES)) "Listening and building notes" else "Turn spoken moments into notes",
                            icon = Icons.Outlined.GraphicEq,
                            status = if (capabilityActive(AssistantCapability.MEETING_NOTES)) "ON" else "OFF",
                            active = capabilityActive(AssistantCapability.MEETING_NOTES),
                            modifier = Modifier.weight(1f),
                            onClick = { toggleCapability(AssistantCapability.MEETING_NOTES) },
                        )
                        ADCapabilityTile(
                            title = "Cron",
                            detail = if (capabilityActive(AssistantCapability.ERRAND_BRAIN)) "Listening for scheduled requests" else "Create reminders and scheduled tasks",
                            icon = Icons.Outlined.EventRepeat,
                            status = if (capabilityActive(AssistantCapability.ERRAND_BRAIN)) "ON" else "OFF",
                            active = capabilityActive(AssistantCapability.ERRAND_BRAIN),
                            modifier = Modifier.weight(1f),
                            onClick = { toggleCapability(AssistantCapability.ERRAND_BRAIN) },
                        )
                    }
                    ADWideCapabilityTile(
                        title = "Automation",
                        detail = when {
                            capabilityActive(AssistantCapability.LOCAL_AGENT) -> "Android actions are available to your assistant"
                            !automationReady -> "Needs Accessibility permission before it can act"
                            else -> "Let the assistant complete supported Android actions"
                        },
                        icon = Icons.Outlined.Bolt,
                        status = when {
                            capabilityActive(AssistantCapability.LOCAL_AGENT) -> "ON"
                            !automationReady -> "SETUP"
                            else -> "OFF"
                        },
                        active = capabilityActive(AssistantCapability.LOCAL_AGENT),
                        onClick = {
                            if (!automationReady) {
                                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            } else {
                                toggleCapability(AssistantCapability.LOCAL_AGENT)
                            }
                        },
                    )
                    capabilityFeedback?.let { feedback ->
                        Text(
                            feedback,
                            style = MaterialTheme.typography.bodySmall,
                            color = ADColors.Muted,
                            modifier = Modifier.padding(horizontal = 3.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ADSectionLabel("CAPTURE")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
                            modifier = Modifier.weight(1f),
                            active = state.meeting.isRecording,
                            onClick = if (state.meeting.isRecording) host.onStopRecording else host.onStartRecording,
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ADColors.Surface, RoundedCornerShape(22.dp))
            .clickable(onClick = onOpenDevice),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(158.dp)
                .background(
                    Brush.radialGradient(
                        listOf(
                            Color(0xFFFCFCFD),
                            Color(0xFFF2F3F5),
                            Color(0xFFE8EAEE),
                        ),
                    ),
                    RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ad_glasses_hero_v4),
                contentDescription = "Glasses",
                modifier = Modifier.fillMaxWidth().height(144.dp).padding(horizontal = 24.dp),
                contentScale = ContentScale.Fit,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 15.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (device.connecting) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = ADColors.Ink)
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
                    color = ADColors.Ink,
                    modifier = Modifier.clickable(onClick = onConnect).padding(8.dp),
                )
            }
        }
    }
}

@Composable
private fun ADSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = ADColors.Muted,
        letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified,
        modifier = Modifier.padding(start = 3.dp),
    )
}

@Composable
private fun ADCapabilityTile(
    title: String,
    detail: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    status: String? = null,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .heightIn(min = 112.dp)
            .background(if (active) ADColors.SurfaceSubtle else ADColors.Surface, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).background(
                    if (active) ADColors.Ink else ADColors.SurfaceSubtle,
                    RoundedCornerShape(11.dp),
                ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    null,
                    tint = if (active) Color.White else ADColors.Ink,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            status?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (active) ADColors.Ink else ADColors.Muted,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
private fun ADWideCapabilityTile(
    title: String,
    detail: String,
    icon: ImageVector,
    status: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (active) ADColors.SurfaceSubtle else ADColors.Surface, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(42.dp).background(if (active) ADColors.Ink else ADColors.SurfaceSubtle, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = if (active) Color.White else ADColors.Ink, modifier = Modifier.size(21.dp))
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(2.dp))
            Text(detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted, maxLines = 2)
        }
        Text(status, style = MaterialTheme.typography.labelSmall, color = if (active) ADColors.Ink else ADColors.Muted)
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
    Column(
        modifier = modifier
            .background(if (active) ADColors.SurfaceSubtle else ADColors.Surface, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 13.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = ADColors.Ink, modifier = Modifier.size(21.dp))
        Spacer(Modifier.height(7.dp))
        Text(title, style = MaterialTheme.typography.labelLarge, maxLines = 1)
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

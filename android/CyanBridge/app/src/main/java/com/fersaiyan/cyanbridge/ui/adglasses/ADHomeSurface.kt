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
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.RadioButtonChecked
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ADHomePalette.Page),
    ) {
        ADTopBar(showBrand = true, showSettings = true, onSettings = onOpenSettings)
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(ADHomePalette.Page),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 14.dp,
                end = 14.dp,
                top = 2.dp,
                bottom = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
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
                        ADHomeSectionLabel("Active now")
                        if (state.meeting.isRecording) {
                            ADLiveRow(
                                icon = Icons.Outlined.RadioButtonChecked,
                                title = "Audio recording",
                                detail = state.meeting.bannerLabel.ifBlank { state.meeting.sourceLabel },
                                accent = ADHomePalette.AudioInk,
                                onClick = host.onStopRecording,
                            )
                        }
                        if (state.transfer.isVisible) {
                            ADLiveRow(
                                icon = Icons.Outlined.Sync,
                                title = "Media sync",
                                detail = state.transfer.detail,
                                accent = ADHomePalette.TranslateInk,
                                onClick = onOpenSync,
                            )
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    ADHomeSectionLabel("Quick actions")

                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        ADHomeAction(
                            title = "Ask AI",
                            detail = "Ask by voice",
                            icon = Icons.Outlined.Mic,
                            containerColor = ADHomePalette.AskAiSoft,
                            iconTint = ADHomePalette.AskAiInk,
                            modifier = Modifier.weight(1.15f),
                            onClick = host.onVoiceQuestion,
                        )
                        ADHomeAction(
                            title = "Photo",
                            detail = "Take a photo",
                            icon = Icons.Outlined.PhotoCamera,
                            containerColor = ADHomePalette.PhotoSoft,
                            iconTint = ADHomePalette.PhotoInk,
                            modifier = Modifier.weight(0.85f),
                            onClick = host.onCapturePhoto,
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        ADHomeAction(
                            title = "Video",
                            detail = "Record from glasses",
                            icon = Icons.Outlined.Videocam,
                            containerColor = ADHomePalette.VideoSoft,
                            iconTint = ADHomePalette.VideoInk,
                            modifier = Modifier.weight(0.88f),
                            onClick = host.onToggleVideo,
                        )
                        ADHomeAction(
                            title = "Translate",
                            detail = "Live conversation",
                            icon = Icons.Rounded.Translate,
                            containerColor = ADHomePalette.TranslateSoft,
                            iconTint = ADHomePalette.TranslateInk,
                            active = translateActive,
                            modifier = Modifier.weight(1.12f),
                            onClick = { toggleCapability(AssistantCapability.TRANSLATOR) },
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        ADHomeAction(
                            title = "Soundbites",
                            detail = "Turn speech into notes",
                            icon = Icons.Outlined.GraphicEq,
                            containerColor = ADHomePalette.SoundbitesSoft,
                            iconTint = ADHomePalette.SoundbitesInk,
                            active = soundbitesActive,
                            modifier = Modifier.weight(1.08f),
                            onClick = { toggleCapability(AssistantCapability.MEETING_NOTES) },
                        )
                        ADHomeAction(
                            title = "Audio",
                            detail = if (state.meeting.isRecording) "Stop recording" else "Start recording",
                            icon = Icons.Outlined.RadioButtonChecked,
                            containerColor = ADHomePalette.AudioSoft,
                            iconTint = ADHomePalette.AudioInk,
                            active = state.meeting.isRecording,
                            modifier = Modifier.weight(0.92f),
                            onClick = if (state.meeting.isRecording) host.onStopRecording else host.onStartRecording,
                        )
                    }
                }
            }

            item {
                ADLensFeature(onClick = host.onImageQuestion)
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
    val statusContainer = if (device.connected) ADColors.SuccessSoft else Color.White.copy(alpha = 0.72f)
    val statusInk = if (device.connected) ADColors.Success else ADColors.Ink

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.linearGradient(
                    listOf(ADHomePalette.HeroStart, ADHomePalette.HeroEnd),
                ),
                shape = RoundedCornerShape(26.dp),
            )
            .clickable(onClick = onOpenDevice)
            .padding(horizontal = 14.dp, vertical = 13.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "YOUR GLASSES",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = ADHomePalette.HeroInk.copy(alpha = 0.52f),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    device.identityLabel ?: "AD Glasses",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium,
                    color = ADHomePalette.HeroInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Surface(
                shape = RoundedCornerShape(999.dp),
                color = statusContainer,
                contentColor = statusInk,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (device.connecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(11.dp),
                            strokeWidth = 1.8.dp,
                            color = statusInk,
                        )
                    } else {
                        Box(
                            Modifier
                                .size(6.dp)
                                .background(statusInk, CircleShape),
                        )
                    }
                    Text(
                        when {
                            device.connected -> "Connected"
                            device.connecting -> "Connecting"
                            else -> "Offline"
                        },
                        modifier = Modifier.padding(start = 5.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        Image(
            painter = painterResource(R.drawable.ad_glasses_hero_v4),
            contentDescription = "Glasses",
            modifier = Modifier
                .fillMaxWidth()
                .height(104.dp)
                .padding(horizontal = 18.dp, vertical = 2.dp),
            contentScale = ContentScale.Fit,
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (device.connected && state.showBattery && state.batteryPercent != null) {
                    ADHeroMetric(Icons.Outlined.BatteryFull, "${state.batteryPercent}%")
                }
                if (device.connected && state.showStorage && state.storageLabel != "--") {
                    ADHeroMetric(Icons.Outlined.Storage, state.storageLabel)
                }
                if (!device.connected) {
                    Text(
                        device.statusLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = ADHomePalette.HeroInk.copy(alpha = 0.62f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (!device.connected && !device.connecting) {
                Surface(
                    onClick = onConnect,
                    shape = RoundedCornerShape(12.dp),
                    color = ADHomePalette.HeroInk,
                    contentColor = Color.White,
                ) {
                    Text(
                        "Connect",
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun ADHeroMetric(icon: ImageVector, value: String) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color.White.copy(alpha = 0.60f),
        contentColor = ADHomePalette.HeroInk,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(13.dp))
            Text(
                value,
                modifier = Modifier.padding(start = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun ADHomeSectionLabel(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Medium,
        color = ADColors.Ink,
        modifier = Modifier.padding(start = 1.dp),
    )
}

@Composable
private fun ADHomeAction(
    title: String,
    detail: String,
    icon: ImageVector,
    containerColor: Color,
    iconTint: Color,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    val resolvedContainer = if (active) iconTint else containerColor
    val primaryContent = if (active) Color.White else ADColors.Ink
    val secondaryContent = if (active) Color.White.copy(alpha = 0.74f) else ADColors.Muted
    val iconContainer = if (active) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.72f)
    val iconColor = if (active) Color.White else iconTint

    Column(
        modifier = modifier
            .heightIn(min = 98.dp)
            .background(resolvedContainer, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(11.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .background(iconContainer, RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(18.dp))
        }

        Column {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = primaryContent,
                maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = secondaryContent,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ADLensFeature(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 88.dp)
            .background(
                brush = Brush.linearGradient(
                    listOf(ADHomePalette.LensStart, ADHomePalette.LensEnd),
                ),
                shape = RoundedCornerShape(26.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(44.dp),
            shape = RoundedCornerShape(14.dp),
            color = Color.White.copy(alpha = 0.17f),
            contentColor = Color.White,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Outlined.Visibility,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        Column(
            modifier = Modifier
                .padding(start = 11.dp)
                .weight(1f),
        ) {
            Text(
                "Lens",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                color = Color.White,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "See it. Ask it.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.76f),
            )
        }

        Surface(
            modifier = Modifier.size(42.dp),
            shape = CircleShape,
            color = ADHomePalette.LensButton,
            contentColor = ADHomePalette.LensButtonInk,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Outlined.ArrowForward,
                    contentDescription = "Open Lens",
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun ADLiveRow(
    icon: ImageVector,
    title: String,
    detail: String,
    accent: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(17.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .background(accent.copy(alpha = 0.12f), RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.padding(start = 9.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = ADColors.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private object ADHomePalette {
    val Page = Color(0xFFF7F8FC)

    val HeroStart = Color(0xFFECEEFF)
    val HeroEnd = Color(0xFFF6F2FF)
    val HeroInk = Color(0xFF25233A)

    val AskAiSoft = Color(0xFFF0EAFF)
    val AskAiInk = Color(0xFF6F4BD3)
    val PhotoSoft = Color(0xFFE8F7EE)
    val PhotoInk = Color(0xFF2C8A5A)
    val VideoSoft = Color(0xFFFFEEE7)
    val VideoInk = Color(0xFFD95F32)
    val TranslateSoft = Color(0xFFE9F1FF)
    val TranslateInk = Color(0xFF376FD0)
    val SoundbitesSoft = Color(0xFFFFF4DC)
    val SoundbitesInk = Color(0xFFB97900)
    val AudioSoft = Color(0xFFFFEBEF)
    val AudioInk = Color(0xFFC84C67)

    val LensStart = Color(0xFF5D5FEF)
    val LensEnd = Color(0xFF7A55D9)
    val LensButton = Color(0xFFFFFFFF)
    val LensButtonInk = Color(0xFF5E58DF)
}

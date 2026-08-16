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
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fersaiyan.cyanbridge.R
import com.fersaiyan.cyanbridge.devices.DeviceProfileStore
import com.fersaiyan.cyanbridge.shared.glasses.GlassesDashboardUiState

/** Quiet control surface for the glasses. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ADHomeSurface(
    state: GlassesDashboardUiState,
    host: ADHostActions,
    onOpenDevice: () -> Unit,
    onOpenSync: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenConversations: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenModes: () -> Unit,
) {
    var captureSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val profile = DeviceProfileStore.loadLastSelected(context)
    val connected = state.connectionLabel.contains("connected", ignoreCase = true) &&
        !state.connectionLabel.contains("disconnected", ignoreCase = true)
    val connecting = state.connectionLabel.contains("connecting", ignoreCase = true) ||
        state.connectionLabel.contains("reconnect", ignoreCase = true)
    val activeMode = state.nativePluginShortcut?.takeIf { it.isEnabled }?.title
    val deviceName = profile?.advertisedName
        ?.trim()
        ?.takeIf { it.isNotBlank() && !it.equals("Unknown", ignoreCase = true) }
    val deviceClass = state.deviceClassLabel
        .trim()
        .takeIf { it.isNotBlank() && !it.equals("Unknown", ignoreCase = true) }
    val deviceIdentity = when {
        deviceName != null && deviceClass != null && !deviceName.contains(deviceClass, ignoreCase = true) ->
            "$deviceName · $deviceClass"
        deviceName != null -> deviceName
        deviceClass != null -> deviceClass
        else -> null
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
                    connected = connected,
                    connecting = connecting,
                    deviceIdentity = deviceIdentity,
                    onOpenDevice = onOpenDevice,
                    onConnect = when {
                        connected -> host.onDisconnect
                        deviceIdentity == null -> host.onOpenDeviceSetup
                        else -> host.onReconnect
                    },
                )
            }

            if (state.meeting.isRecording || state.transfer.isVisible || activeMode != null) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text("Active", style = MaterialTheme.typography.titleLarge)
                        if (state.meeting.isRecording) {
                            ADLiveRow(
                                icon = Icons.Outlined.GraphicEq,
                                title = "Meeting recording",
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
                        activeMode?.let {
                            ADLiveRow(
                                icon = Icons.Outlined.AutoAwesome,
                                title = it,
                                detail = "Mode active",
                                status = "ON",
                                onClick = onOpenModes,
                            )
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ADHomeAction(
                        title = "Ask",
                        detail = "Voice",
                        icon = Icons.Outlined.Mic,
                        modifier = Modifier.weight(1f),
                        onClick = host.onVoiceQuestion,
                    )
                    ADHomeAction(
                        title = "See",
                        detail = "Glasses camera",
                        icon = Icons.Outlined.Visibility,
                        modifier = Modifier.weight(1f),
                        onClick = host.onImageQuestion,
                    )
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    ADHomeLink(
                        icon = Icons.Outlined.AutoAwesome,
                        title = "Conversations",
                        detail = "Continue or review",
                        onClick = onOpenConversations,
                    )
                    ADHomeLink(
                        icon = Icons.Outlined.CameraAlt,
                        title = "Capture",
                        detail = "Photo or video",
                        onClick = { captureSheet = true },
                    )
                    ADHomeLink(
                        icon = Icons.Outlined.Sync,
                        title = "Library",
                        detail = if (state.transfer.isVisible) "Sync in progress" else "Media and saved results",
                        onClick = if (state.transfer.isVisible) onOpenSync else onOpenLibrary,
                    )
                    ADHomeLink(
                        icon = Icons.Outlined.GraphicEq,
                        title = if (state.meeting.isRecording) "Stop recording" else "Record",
                        detail = if (state.meeting.isRecording) "Recording now" else "Audio recording",
                        onClick = if (state.meeting.isRecording) host.onStopRecording else host.onStartRecording,
                    )
                }
            }
        }
    }

    if (captureSheet) {
        ModalBottomSheet(
            onDismissRequest = { captureSheet = false },
            containerColor = ADColors.Surface,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Glasses camera", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Ask about what the glasses see, or save a photo or video.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ADColors.Muted,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                ADSettingsRow(Icons.Outlined.Visibility, "Ask what I see", onClick = {
                    captureSheet = false
                    host.onImageQuestion()
                })
                ADSettingsRow(Icons.Outlined.PhotoCamera, "Take photo", onClick = {
                    captureSheet = false
                    host.onCapturePhoto()
                })
                ADSettingsRow(Icons.Outlined.Videocam, "Record video", onClick = {
                    captureSheet = false
                    host.onToggleVideo()
                })
            }
        }
    }
}

@Composable
private fun ADReadinessStage(
    state: GlassesDashboardUiState,
    connected: Boolean,
    connecting: Boolean,
    deviceIdentity: String?,
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
            if (connecting) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = ADColors.Blue)
            } else {
                Box(
                    Modifier.size(8.dp).background(if (connected) ADColors.Success else ADColors.Muted, CircleShape),
                )
            }
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text(
                    when {
                        connected -> "Connected"
                        connecting -> state.connectionLabel
                        else -> state.connectionLabel.takeUnless { it.equals("Unknown", ignoreCase = true) } ?: "Disconnected"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (connected && deviceIdentity != null) {
                    Text(
                        deviceIdentity,
                        style = MaterialTheme.typography.bodySmall,
                        color = ADColors.Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (connected) {
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
            } else if (!connecting) {
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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .heightIn(min = 102.dp)
            .background(ADColors.Surface, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(15.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(38.dp).background(ADColors.SurfaceSubtle, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = ADColors.Ink, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(9.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
    }
}

@Composable
private fun ADHomeLink(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
        Icon(Icons.Rounded.KeyboardArrowRight, null, tint = ADColors.Muted)
    }
}

@Composable
private fun ADLiveRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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

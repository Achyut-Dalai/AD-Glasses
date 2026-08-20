package com.fersaiyan.cyanbridge.ui.adglasses

import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.GraphicEq
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        ADTopBar(showBrand = false, showSettings = true, onSettings = onOpenSettings)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp, 3.dp, 12.dp, 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ADGlassesDeviceCard(
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
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        if (state.meeting.isRecording) {
                            ADLiveTile(
                                title = "RECORDING",
                                detail = state.meeting.bannerLabel.ifBlank { state.meeting.sourceLabel },
                                icon = Icons.Outlined.GraphicEq,
                                modifier = Modifier.weight(1f),
                                onClick = host.onStopRecording,
                            )
                        }
                        if (state.transfer.isVisible) {
                            ADLiveTile(
                                title = "SYNCING",
                                detail = state.transfer.detail,
                                glyph = ADGlyph.SYNC,
                                modifier = Modifier.weight(1f),
                                onClick = onOpenSync,
                            )
                        }
                    }
                }
            }

            item { ADLensCard(onClick = host.onImageQuestion) }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ADCameraSkillCard(
                        modifier = Modifier.weight(1f),
                        onPhoto = host.onCapturePhoto,
                        onVideo = host.onToggleVideo,
                    )
                    ADAskSkillCard(
                        modifier = Modifier.weight(1f),
                        onClick = host.onVoiceQuestion,
                    )
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ADSectionTitle("AUDIO")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ADAudioActionPill(
                            title = "Translate",
                            active = translateActive,
                            modifier = Modifier.weight(1f),
                            onClick = { toggleCapability(AssistantCapability.TRANSLATOR) },
                        )
                        ADAudioActionPill(
                            title = "Record",
                            active = state.meeting.isRecording,
                            modifier = Modifier.weight(1f),
                            onClick = if (state.meeting.isRecording) host.onStopRecording else host.onStartRecording,
                        )
                        ADAudioActionPill(
                            title = "Soundbites",
                            active = soundbitesActive,
                            modifier = Modifier.weight(1f),
                            onClick = { toggleCapability(AssistantCapability.MEETING_NOTES) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ADGlassesDeviceCard(
    state: GlassesDashboardUiState,
    device: ADDevicePresentation,
    onOpenDevice: () -> Unit,
    onConnect: () -> Unit,
) {
    Surface(
        onClick = onOpenDevice,
        modifier = Modifier.fillMaxWidth().heightIn(min = 138.dp),
        shape = RoundedCornerShape(18.dp),
        color = ADColors.Surface,
        contentColor = ADColors.Ink,
        border = BorderStroke(1.dp, ADColors.Outline),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1.06f)) {
                Text(
                    "AD GLASSES",
                    style = MaterialTheme.typography.titleLarge,
                    color = ADColors.Ink,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (device.connecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(11.dp),
                            strokeWidth = 1.4.dp,
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
                        style = MaterialTheme.typography.bodySmall,
                        color = ADColors.Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (device.connected && ((state.showBattery && state.batteryPercent != null) || (state.showStorage && state.storageLabel != "--"))) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.showBattery && state.batteryPercent != null) {
                            ADDeviceInlineMetric("BAT", "${state.batteryPercent}%")
                        }
                        if (state.showStorage && state.storageLabel != "--") {
                            ADDeviceInlineMetric("MEM", state.storageLabel)
                        }
                    }
                }

                Spacer(Modifier.height(9.dp))
                if (device.connecting) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = ADColors.SurfaceSubtle,
                        border = BorderStroke(1.dp, ADColors.Outline),
                    ) {
                        Text(
                            "CONNECTING",
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = ADColors.InkSoft,
                        )
                    }
                } else {
                    Surface(
                        onClick = onConnect,
                        shape = RoundedCornerShape(8.dp),
                        color = ADColors.SurfaceSubtle,
                        contentColor = ADColors.Ink,
                        border = BorderStroke(1.dp, ADColors.Outline),
                    ) {
                        Text(
                            when {
                                device.connected -> "DISCONNECT"
                                device.shouldOpenSetup -> "CONNECT"
                                else -> "RECONNECT"
                            },
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = ADTechFontFamily,
                                letterSpacing = 0.65.sp,
                            ),
                            color = ADColors.Ink,
                        )
                    }
                }
            }

            Spacer(Modifier.width(10.dp))
            Surface(
                modifier = Modifier.weight(0.94f).height(112.dp),
                shape = RoundedCornerShape(15.dp),
                color = Color.Black,
                border = BorderStroke(1.dp, ADColors.Outline),
            ) {
                Image(
                    painter = painterResource(R.drawable.ad_glasses_hero_v4),
                    contentDescription = "AD Glasses",
                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp),
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }
}

@Composable
private fun ADDeviceInlineMetric(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = ADColors.Muted)
        Text(value, style = MaterialTheme.typography.labelMedium, color = ADColors.Ink, maxLines = 1)
    }
}

@Composable
private fun ADLensCard(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 138.dp)
            .semantics { contentDescription = "Lens. See, capture, ask." },
        shape = RoundedCornerShape(18.dp),
        color = ADColors.Surface,
        contentColor = ADColors.Ink,
        border = BorderStroke(1.dp, ADColors.Outline),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1.06f)) {
                Text(
                    "LENS",
                    style = MaterialTheme.typography.titleLarge,
                    color = ADColors.Ink,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    "SEE · CAPTURE · ASK",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = ADTechFontFamily,
                        letterSpacing = 0.75.sp,
                    ),
                    color = ADColors.Muted,
                )
            }
            Spacer(Modifier.width(10.dp))
            ADLensShutterArtwork(Modifier.weight(0.94f).height(112.dp))
        }
    }
}

@Composable
private fun ADLensShutterArtwork(modifier: Modifier = Modifier) {
    val pulse by rememberInfiniteTransition(label = "lens-focus").animateFloat(
        initialValue = 0.72f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1450),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "lens-focus-pulse",
    )
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(15.dp),
        color = ADColors.SurfaceSubtle,
        border = BorderStroke(1.dp, ADColors.Outline),
    ) {
        Canvas(Modifier.fillMaxSize().padding(17.dp)) {
            val stroke = 2.dp.toPx()
            val arm = size.minDimension * 0.22f
            val inset = stroke
            val right = size.width - inset
            val bottom = size.height - inset
            val ink = ADColors.Ink

            drawLine(ink, Offset(inset, inset + arm), Offset(inset, inset), stroke, StrokeCap.Round)
            drawLine(ink, Offset(inset, inset), Offset(inset + arm, inset), stroke, StrokeCap.Round)
            drawLine(ink, Offset(right - arm, inset), Offset(right, inset), stroke, StrokeCap.Round)
            drawLine(ink, Offset(right, inset), Offset(right, inset + arm), stroke, StrokeCap.Round)
            drawLine(ink, Offset(inset, bottom - arm), Offset(inset, bottom), stroke, StrokeCap.Round)
            drawLine(ink, Offset(inset, bottom), Offset(inset + arm, bottom), stroke, StrokeCap.Round)
            drawLine(ink, Offset(right - arm, bottom), Offset(right, bottom), stroke, StrokeCap.Round)
            drawLine(ink, Offset(right, bottom - arm), Offset(right, bottom), stroke, StrokeCap.Round)

            val center = Offset(size.width / 2f, size.height / 2f)
            val lensRadius = size.minDimension * 0.20f
            drawCircle(
                color = ink.copy(alpha = 0.22f + pulse * 0.14f),
                radius = lensRadius * 1.55f,
                center = center,
                style = Stroke(width = stroke),
            )
            drawCircle(
                color = ink,
                radius = lensRadius,
                center = center,
                style = Stroke(width = stroke),
            )
            drawCircle(
                color = ADColors.Red.copy(alpha = 0.70f + pulse * 0.30f),
                radius = 2.4.dp.toPx(),
                center = center,
            )
        }
    }
}

@Composable
private fun ADCameraSkillCard(
    modifier: Modifier = Modifier,
    onPhoto: () -> Unit,
    onVideo: () -> Unit,
) {
    Surface(
        modifier = modifier.heightIn(min = 174.dp),
        shape = RoundedCornerShape(17.dp),
        color = ADColors.Surface,
        contentColor = ADColors.Ink,
        border = BorderStroke(1.dp, ADColors.Outline),
    ) {
        Column(Modifier.padding(10.dp)) {
            Surface(
                modifier = Modifier.fillMaxWidth().height(76.dp),
                shape = RoundedCornerShape(13.dp),
                color = ADColors.SurfaceSubtle,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Surface(
                        modifier = Modifier.size(46.dp),
                        shape = RoundedCornerShape(13.dp),
                        color = ADColors.Surface,
                        border = BorderStroke(1.dp, ADColors.Outline),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Outlined.CameraAlt,
                                contentDescription = null,
                                tint = ADColors.Ink,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(9.dp))
            Text("Camera", style = MaterialTheme.typography.titleMedium, color = ADColors.Ink, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(7.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                ADHomeMiniPill("Photo", Modifier.weight(1f), onPhoto)
                ADHomeMiniPill("Video", Modifier.weight(1f), onVideo)
            }
        }
    }
}

@Composable
private fun ADAskSkillCard(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 174.dp).semantics { contentDescription = "Ask AI" },
        shape = RoundedCornerShape(17.dp),
        color = ADColors.Surface,
        contentColor = ADColors.Ink,
        border = BorderStroke(1.dp, ADColors.Outline),
    ) {
        Column(Modifier.padding(10.dp)) {
            Surface(
                modifier = Modifier.fillMaxWidth().height(76.dp),
                shape = RoundedCornerShape(13.dp),
                color = ADColors.SurfaceSubtle,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Surface(
                        modifier = Modifier.size(46.dp),
                        shape = RoundedCornerShape(13.dp),
                        color = ADColors.Surface,
                        border = BorderStroke(1.dp, ADColors.Outline),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            ADGlyphIcon(ADGlyph.ASK, ADColors.Ink, Modifier.size(25.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(9.dp))
            Text("Ask AI", style = MaterialTheme.typography.titleMedium, color = ADColors.Ink, fontWeight = FontWeight.SemiBold)
            Text("Voice", style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
        }
    }
}

@Composable
private fun ADHomeMiniPill(
    title: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 36.dp),
        shape = RoundedCornerShape(10.dp),
        color = ADColors.SurfaceSubtle,
        contentColor = ADColors.Ink,
        border = BorderStroke(1.dp, ADColors.Outline),
    ) {
        Box(Modifier.padding(horizontal = 7.dp, vertical = 7.dp), contentAlignment = Alignment.Center) {
            Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ADAudioActionPill(
    title: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 42.dp).semantics {
            contentDescription = title
            if (active) stateDescription = "Active"
        },
        shape = RoundedCornerShape(10.dp),
        color = if (active) ADColors.SurfacePressed else ADColors.SurfaceSubtle,
        contentColor = ADColors.Ink,
        border = BorderStroke(1.dp, if (active) ADColors.Ink.copy(alpha = 0.42f) else ADColors.Outline),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (active) {
                Box(Modifier.size(4.dp).background(ADColors.Red, CircleShape))
                Spacer(Modifier.width(5.dp))
            }
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ADLiveTile(
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
    glyph: ADGlyph? = null,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = "$title. $detail" },
        shape = RoundedCornerShape(12.dp),
        color = ADColors.Surface,
        contentColor = ADColors.Ink,
        border = BorderStroke(1.dp, ADColors.Outline),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                icon != null -> Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                glyph != null -> ADGlyphIcon(glyph, ADColors.Ink, Modifier.size(17.dp))
            }
            Column(Modifier.padding(start = 7.dp).weight(1f)) {
                Text(title, style = MaterialTheme.typography.labelSmall, color = ADColors.InkSoft)
                Text(detail, style = MaterialTheme.typography.labelMedium, color = ADColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

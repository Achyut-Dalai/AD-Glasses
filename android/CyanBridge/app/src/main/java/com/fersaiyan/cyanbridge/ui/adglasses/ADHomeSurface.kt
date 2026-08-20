package com.fersaiyan.cyanbridge.ui.adglasses

import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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

/** Home follows the same compact black-card language as the locked AI surface. */
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
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        ADSectionTitle("Live now")
                        if (state.meeting.isRecording) {
                            ADLiveRow(
                                title = "Audio recording",
                                detail = state.meeting.bannerLabel.ifBlank { state.meeting.sourceLabel },
                                glyph = ADGlyph.AUDIO,
                                live = true,
                                onClick = host.onStopRecording,
                            )
                        }
                        if (state.transfer.isVisible) {
                            ADLiveRow(
                                title = "Media sync",
                                detail = state.transfer.detail,
                                glyph = ADGlyph.SYNC,
                                live = true,
                                onClick = onOpenSync,
                            )
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ADSectionTitle("Quick actions")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ADHomeActionCard(
                            code = "01",
                            title = "Ask AI",
                            detail = "Voice question",
                            glyph = ADGlyph.ASK,
                            modifier = Modifier.weight(1f),
                            onClick = host.onVoiceQuestion,
                        )
                        ADHomeActionCard(
                            code = "02",
                            title = "Photo",
                            detail = "Capture",
                            glyph = ADGlyph.PHOTO,
                            modifier = Modifier.weight(1f),
                            onClick = host.onCapturePhoto,
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ADHomeActionCard(
                            code = "03",
                            title = "Video",
                            detail = "Record",
                            glyph = ADGlyph.VIDEO,
                            modifier = Modifier.weight(1f),
                            onClick = host.onToggleVideo,
                        )
                        ADHomeActionCard(
                            code = "04",
                            title = "Translate",
                            detail = "Live speech",
                            glyph = ADGlyph.TRANSLATE,
                            active = translateActive,
                            modifier = Modifier.weight(1f),
                            onClick = { toggleCapability(AssistantCapability.TRANSLATOR) },
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ADHomeActionCard(
                            code = "05",
                            title = "Soundbites",
                            detail = "Speech notes",
                            glyph = ADGlyph.SOUNDBITES,
                            active = soundbitesActive,
                            modifier = Modifier.weight(1f),
                            onClick = { toggleCapability(AssistantCapability.MEETING_NOTES) },
                        )
                        ADHomeActionCard(
                            code = "06",
                            title = "Audio",
                            detail = if (state.meeting.isRecording) "Stop recording" else "Start recording",
                            glyph = ADGlyph.AUDIO,
                            active = state.meeting.isRecording,
                            modifier = Modifier.weight(1f),
                            onClick = if (state.meeting.isRecording) host.onStopRecording else host.onStartRecording,
                        )
                    }
                    ADLensCard(onClick = host.onImageQuestion)
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
    Surface(
        onClick = onOpenDevice,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(17.dp),
        color = ADColors.Surface,
        border = BorderStroke(1.dp, ADColors.Outline),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(104.dp)
                        .height(72.dp)
                        .background(Color.Black, RoundedCornerShape(13.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ad_glasses_hero_v4),
                        contentDescription = "AD Glasses",
                        modifier = Modifier.fillMaxSize().padding(4.dp),
                        contentScale = ContentScale.Fit,
                    )
                    ADHeroSignalMatrix(
                        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 7.dp, bottom = 6.dp),
                    )
                }

                Column(Modifier.padding(start = 11.dp).weight(1f)) {
                    Text(
                        "GLASSES / STATUS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = ADTechFontFamily,
                            letterSpacing = 1.sp,
                        ),
                        color = ADColors.InkSoft,
                    )
                    Spacer(Modifier.size(3.dp))
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
                            color = ADColors.Ink,
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
                        shape = RoundedCornerShape(9.dp),
                        color = ADColors.Ink,
                        contentColor = Color.Black,
                    ) {
                        Text(
                            "Connect",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                        )
                    }
                }
            }

            if (device.connected &&
                ((state.showBattery && state.batteryPercent != null) ||
                    (state.showStorage && state.storageLabel != "--"))
            ) {
                Spacer(Modifier.size(9.dp))
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
private fun ADHeroSignalMatrix(modifier: Modifier = Modifier) {
    val sweep by rememberInfiniteTransition(label = "home-matrix-sweep").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500),
            repeatMode = RepeatMode.Restart,
        ),
        label = "home-matrix-phase",
    )
    val active = ((sweep * 15f).toInt()).coerceIn(0, 14)

    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(5.dp))
            .padding(horizontal = 4.dp, vertical = 3.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        repeat(3) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(5) { column ->
                    val index = row * 5 + column
                    Box(
                        Modifier
                            .size(2.6.dp)
                            .background(
                                if (index == active) ADColors.Red else ADColors.Ink.copy(alpha = 0.52f),
                                RoundedCornerShape(0.8.dp),
                            ),
                    )
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
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = ADTechFontFamily, letterSpacing = 0.7.sp),
            color = ADColors.InkSoft,
        )
        Text(value, style = MaterialTheme.typography.labelLarge, color = ADColors.Ink, maxLines = 1)
    }
}

@Composable
private fun ADHomeActionCard(
    code: String,
    title: String,
    detail: String,
    glyph: ADGlyph,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 108.dp)
            .semantics {
                contentDescription = "$title. $detail"
                if (active) stateDescription = "Active"
            },
        shape = RoundedCornerShape(17.dp),
        color = ADColors.Surface,
        border = BorderStroke(1.dp, if (active) ADColors.Ink.copy(alpha = 0.42f) else ADColors.Outline),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.Top) {
                ADGlyphIcon(
                    glyph = glyph,
                    tint = ADColors.Ink,
                    modifier = Modifier.size(37.dp),
                    accent = if (active) ADColors.Red else null,
                )
                Spacer(Modifier.weight(1f))
                if (active) Box(Modifier.size(5.dp).background(ADColors.Red, CircleShape))
            }
            Column {
                Text(
                    "$code / ${title.uppercase()}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = ADTechFontFamily,
                        letterSpacing = 0.85.sp,
                    ),
                    color = ADColors.InkSoft,
                    maxLines = 1,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    detail,
                    style = MaterialTheme.typography.titleMedium,
                    color = ADColors.Ink,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ADLensCard(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 78.dp)
            .semantics { contentDescription = "Lens. Look at it and ask about it." },
        shape = RoundedCornerShape(17.dp),
        color = ADColors.Surface,
        border = BorderStroke(1.dp, ADColors.Outline),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ADGlyphIcon(
                glyph = ADGlyph.LENS,
                tint = ADColors.Ink,
                modifier = Modifier.size(42.dp),
                accent = ADColors.Red,
            )
            Column(Modifier.padding(start = 11.dp).weight(1f)) {
                Text(
                    "07 / LENS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = ADTechFontFamily,
                        letterSpacing = 0.85.sp,
                    ),
                    color = ADColors.InkSoft,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    "Look at it. Ask about it.",
                    style = MaterialTheme.typography.titleMedium,
                    color = ADColors.Ink,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun ADLiveRow(
    title: String,
    detail: String,
    glyph: ADGlyph,
    live: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = ADColors.Surface,
        border = BorderStroke(1.dp, ADColors.Outline),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ADGlyphIcon(
                glyph = glyph,
                tint = ADColors.Ink,
                modifier = Modifier.size(20.dp),
                accent = if (live) ADColors.Red else null,
            )
            Column(Modifier.padding(start = 9.dp).weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = ADColors.Ink)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

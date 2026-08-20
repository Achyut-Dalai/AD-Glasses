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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.Color
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
        ADTopBar(showBrand = true, showSettings = true, onSettings = onOpenSettings)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = 3.dp,
                bottom = 18.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            item {
                ADLargeGlassesHero(
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

            item { ADLensMatrixAction(onClick = host.onImageQuestion) }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ADSectionTitle("Actions")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ADHomeAction(
                            title = "Ask AI",
                            glyph = ADGlyph.ASK,
                            modifier = Modifier.weight(1f),
                            onClick = host.onVoiceQuestion,
                        )
                        ADHomeAction(
                            title = "Photo",
                            glyph = ADGlyph.PHOTO,
                            modifier = Modifier.weight(1f),
                            onClick = host.onCapturePhoto,
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ADHomeAction(
                            title = "Video",
                            glyph = ADGlyph.VIDEO,
                            modifier = Modifier.weight(1f),
                            onClick = host.onToggleVideo,
                        )
                        ADHomeAction(
                            title = "Translate",
                            glyph = ADGlyph.TRANSLATE,
                            active = translateActive,
                            modifier = Modifier.weight(1f),
                            onClick = { toggleCapability(AssistantCapability.TRANSLATOR) },
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ADHomeAction(
                            title = "Soundbites",
                            glyph = ADGlyph.SOUNDBITES,
                            active = soundbitesActive,
                            modifier = Modifier.weight(1f),
                            onClick = { toggleCapability(AssistantCapability.MEETING_NOTES) },
                        )
                        ADHomeAction(
                            title = "Audio",
                            icon = Icons.Outlined.GraphicEq,
                            active = state.meeting.isRecording,
                            modifier = Modifier.weight(1f),
                            onClick = if (state.meeting.isRecording) host.onStopRecording else host.onStartRecording,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ADLargeGlassesHero(
    state: GlassesDashboardUiState,
    device: ADDevicePresentation,
    onOpenDevice: () -> Unit,
    onConnect: () -> Unit,
) {
    Surface(
        onClick = onOpenDevice,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(19.dp),
        color = Color.Black,
        contentColor = ADColors.Ink,
        border = BorderStroke(1.dp, ADColors.Outline),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(184.dp)
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ad_glasses_hero_v4),
                    contentDescription = "AD Glasses",
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
                    contentScale = ContentScale.Fit,
                )

                if (!device.connected && !device.connecting) {
                    Surface(
                        onClick = onConnect,
                        modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
                        shape = RoundedCornerShape(9.dp),
                        color = ADColors.Ink,
                        contentColor = Color.Black,
                    ) {
                        Text(
                            "CONNECT",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = ADTechFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.7.sp,
                            ),
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (device.connecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(13.dp),
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
                Column(Modifier.padding(start = 7.dp).weight(1f)) {
                    Text(
                        device.statusLabel.uppercase(),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontFamily = ADTechFontFamily,
                            letterSpacing = 0.55.sp,
                        ),
                        color = ADColors.Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    device.identityLabel?.takeIf { it.isNotBlank() }?.let { identity ->
                        Text(
                            identity,
                            style = MaterialTheme.typography.labelSmall,
                            color = ADColors.Muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                if (device.connected && state.showBattery && state.batteryPercent != null) {
                    ADHeroMetric("BAT", "${state.batteryPercent}%")
                }
                if (device.connected && state.showStorage && state.storageLabel != "--") {
                    Spacer(Modifier.size(9.dp))
                    ADHeroMetric("MEM", state.storageLabel)
                }
            }
        }
    }
}

@Composable
private fun ADHeroMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.End) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = ADTechFontFamily,
                letterSpacing = 0.65.sp,
            ),
            color = ADColors.Muted,
        )
        Text(value, style = MaterialTheme.typography.labelLarge, color = ADColors.Ink, maxLines = 1)
    }
}

/** Lens is the one deliberately matrix-driven Home feature. */
@Composable
private fun ADLensMatrixAction(onClick: () -> Unit) {
    val phase by rememberInfiniteTransition(label = "lens-matrix").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1850),
            repeatMode = RepeatMode.Restart,
        ),
        label = "lens-matrix-phase",
    )
    val pattern = remember {
        listOf(
            "01110",
            "11011",
            "10101",
            "11011",
            "01110",
        )
    }
    val litCells = remember(pattern) {
        buildList {
            pattern.forEachIndexed { row, line ->
                line.forEachIndexed { column, value ->
                    if (value == '1') add(row to column)
                }
            }
        }
    }
    val activeIndex = ((phase * litCells.size).toInt()).coerceIn(0, litCells.lastIndex)
    val activeCell = litCells[activeIndex]

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Lens matrix. See, capture, ask." },
        shape = RoundedCornerShape(15.dp),
        color = ADColors.Surface,
        contentColor = ADColors.Ink,
        border = BorderStroke(1.dp, ADColors.Outline),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "LENS MATRIX / V1",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = ADTechFontFamily,
                        letterSpacing = 1.05.sp,
                    ),
                    color = ADColors.InkSoft,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "SEE   CAPTURE   ASK",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = ADTechFontFamily,
                        letterSpacing = 0.75.sp,
                    ),
                    color = ADColors.Ink,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                pattern.forEachIndexed { row, line ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        line.forEachIndexed { column, value ->
                            val isActive = activeCell.first == row && activeCell.second == column
                            Box(
                                Modifier
                                    .size(5.dp)
                                    .background(
                                        when {
                                            isActive -> ADColors.Red
                                            value == '1' -> ADColors.Ink.copy(alpha = 0.72f)
                                            else -> ADColors.Outline.copy(alpha = 0.58f)
                                        },
                                        RoundedCornerShape(1.3.dp),
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ADHomeAction(
    title: String,
    modifier: Modifier = Modifier,
    glyph: ADGlyph? = null,
    icon: ImageVector? = null,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 92.dp)
            .semantics {
                contentDescription = title
                if (active) stateDescription = "Active"
            },
        shape = RoundedCornerShape(14.dp),
        color = ADColors.Surface,
        contentColor = ADColors.Ink,
        border = BorderStroke(1.dp, if (active) ADColors.Ink.copy(alpha = 0.42f) else ADColors.Outline),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Surface(
                    modifier = Modifier.size(32.dp),
                    shape = RoundedCornerShape(9.dp),
                    color = ADColors.SurfaceSubtle,
                    contentColor = ADColors.Ink,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        when {
                            icon != null -> Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp))
                            glyph != null -> ADGlyphIcon(
                                glyph = glyph,
                                tint = ADColors.Ink,
                                modifier = Modifier.size(20.dp),
                                accent = if (active) ADColors.Red else null,
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                if (active) Box(Modifier.size(5.dp).background(ADColors.Red, CircleShape))
            }
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = ADColors.Ink,
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
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                icon != null -> Icon(icon, contentDescription = null, tint = ADColors.Ink, modifier = Modifier.size(17.dp))
                glyph != null -> ADGlyphIcon(glyph, ADColors.Ink, Modifier.size(17.dp), accent = ADColors.Red)
            }
            Text(
                title,
                modifier = Modifier.padding(start = 7.dp),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = ADTechFontFamily,
                    letterSpacing = 0.65.sp,
                ),
                color = ADColors.Ink,
                maxLines = 1,
            )
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(5.dp).background(ADColors.Red, CircleShape))
        }
    }
}

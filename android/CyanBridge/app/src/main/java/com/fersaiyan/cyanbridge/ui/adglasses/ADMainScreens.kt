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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fersaiyan.cyanbridge.R
import com.fersaiyan.cyanbridge.shared.glasses.GlassesDashboardUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ADHomeScreen(
    state: GlassesDashboardUiState,
    host: ADHostActions,
    onOpenDevice: () -> Unit,
    onOpenSync: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAssistant: () -> Unit,
    onOpenLibrary: () -> Unit,
) {
    var showCaptureSheet by remember { mutableStateOf(false) }
    val connected = state.connectionLabel.contains("connected", ignoreCase = true) &&
        !state.connectionLabel.contains("disconnected", ignoreCase = true)
    val connecting = state.connectionLabel.contains("connecting", ignoreCase = true) ||
        state.connectionLabel.contains("reconnect", ignoreCase = true)

    Column(Modifier.fillMaxSize()) {
        ADTopBar(showBrand = true, showSettings = true, onSettings = onOpenSettings)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 4.dp,
                bottom = 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                ADDeviceStage(
                    state = state,
                    connected = connected,
                    connecting = connecting,
                    onOpenDevice = onOpenDevice,
                    onConnect = when {
                        connected -> host.onDisconnect
                        state.deviceClassLabel == "Unknown" -> host.onOpenDeviceSetup
                        else -> host.onReconnect
                    },
                )
            }
            if (state.meeting.isRecording || state.transfer.isVisible) {
                item {
                    ADActivityBanner(
                        state = state,
                        onOpen = if (state.meeting.isRecording) host.onStopRecording else onOpenSync,
                    )
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ADQuickAction(
                            "Ask",
                            Icons.Outlined.AutoAwesome,
                            modifier = Modifier.weight(1f),
                        ) {
                            onOpenAssistant()
                        }
                        ADQuickAction(
                            "Capture",
                            Icons.Outlined.CameraAlt,
                            modifier = Modifier.weight(1f),
                        ) { showCaptureSheet = true }
                        ADQuickAction(
                            "Sync",
                            Icons.Outlined.Sync,
                            primary = state.transfer.isVisible,
                            modifier = Modifier.weight(1f),
                        ) { onOpenSync() }
                        ADQuickAction(
                            if (state.meeting.isRecording) "Stop" else "Record",
                            if (state.meeting.isRecording) Icons.Outlined.StopCircle else Icons.Outlined.Mic,
                            destructive = state.meeting.isRecording,
                            modifier = Modifier.weight(1f),
                        ) {
                            if (state.meeting.isRecording) host.onStopRecording() else host.onStartRecording()
                        }
                    }
                }
            }
            if (!state.transfer.isVisible && !state.meeting.isRecording) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ADSectionTitle("Recent")
                        ADCard(onClick = onOpenLibrary) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(44.dp).background(ADColors.BlueSoft, RoundedCornerShape(13.dp)),
                                    contentAlignment = Alignment.Center,
                                ) { Icon(Icons.Outlined.PhotoLibrary, null, tint = ADColors.Blue) }
                                Text(
                                    "No recent captures",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(start = 12.dp).weight(1f),
                                )
                                Icon(Icons.Rounded.KeyboardArrowRight, null, tint = ADColors.Muted)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCaptureSheet) {
        ModalBottomSheet(
            onDismissRequest = { showCaptureSheet = false },
            containerColor = ADColors.Surface,
        ) {
            Column(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Capture", style = MaterialTheme.typography.headlineMedium)
                ADCaptureChoice(Icons.Outlined.Visibility, "Ask what I see") {
                    showCaptureSheet = false
                    host.onImageQuestion()
                }
                ADCaptureChoice(Icons.Outlined.PhotoCamera, "Take photo") {
                    showCaptureSheet = false
                    host.onCapturePhoto()
                }
                ADCaptureChoice(Icons.Outlined.Videocam, "Record video") {
                    showCaptureSheet = false
                    host.onToggleVideo()
                }
            }
        }
    }
}

@Composable
private fun ADDeviceStage(
    state: GlassesDashboardUiState,
    connected: Boolean,
    connecting: Boolean,
    onOpenDevice: () -> Unit,
    onConnect: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(208.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFFF8FAFD), Color(0xFFE9EDF4)),
                    ),
                    RoundedCornerShape(24.dp),
                )
                .clickable(onClick = onOpenDevice),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ad_glasses_hero_v4),
                contentDescription = "Smart glasses",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(188.dp)
                    .padding(horizontal = 20.dp),
                contentScale = ContentScale.Fit,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .background(ADColors.Surface, RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (connecting) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = ADColors.Blue,
                )
            } else {
                Box(Modifier.size(8.dp).background(
                    if (connected) ADColors.Success else ADColors.Muted,
                    CircleShape,
                ))
            }
            Text(
                state.connectionLabel,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 8.dp).weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (state.showBattery && state.batteryPercent != null) {
                    Icon(Icons.Outlined.BatteryFull, null, tint = ADColors.Blue, modifier = Modifier.size(15.dp))
                    Text("${state.batteryPercent}%", style = MaterialTheme.typography.labelMedium)
                }
                if (state.showStorage && state.storageLabel != "--") {
                    Icon(Icons.Outlined.Storage, null, tint = ADColors.Blue, modifier = Modifier.size(15.dp))
                    Text(state.storageLabel, style = MaterialTheme.typography.labelMedium)
                }
                if (!connected && !connecting) {
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
}

@Composable
private fun ADQuickAction(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val background = when {
        primary -> ADColors.Blue
        destructive -> ADColors.ErrorSoft
        else -> ADColors.Surface
    }
    val foreground = when {
        primary -> Color.White
        destructive -> ADColors.Error
        else -> ADColors.Ink
    }
    Column(
        modifier = modifier
            .heightIn(min = 92.dp)
            .background(background, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(43.dp).background(
                if (primary) Color.White.copy(alpha = 0.17f)
                else if (destructive) Color.White.copy(alpha = 0.52f)
                else ADColors.Blue.copy(alpha = 0.10f),
                CircleShape,
            ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, label, tint = foreground, modifier = Modifier.size(23.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = foreground)
    }
}

@Composable
private fun ADActivityBanner(state: GlassesDashboardUiState, onOpen: () -> Unit) {
    val recording = state.meeting.isRecording
    val title = if (recording) "Meeting recording active" else "Syncing media"
    val detail = if (recording) {
        state.meeting.bannerLabel.ifBlank { state.meeting.sourceLabel }
    } else state.transfer.detail
    ADCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpen,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).background(
                    if (recording) ADColors.ErrorSoft else ADColors.BlueSoft,
                    CircleShape,
                ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (recording) Icons.Outlined.GraphicEq else Icons.Outlined.Sync,
                    null,
                    tint = if (recording) ADColors.Error else ADColors.Blue,
                )
            }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = ADColors.Muted, maxLines = 2)
            }
            ADStatusChip(if (recording) "LIVE" else "ACTIVE", if (recording) ADStatusTone.ERROR else ADStatusTone.INFO)
        }
    }
}

@Composable
private fun ADCaptureChoice(icon: ImageVector, title: String, onClick: () -> Unit) {
    ADSettingsRow(icon = icon, title = title, onClick = onClick)
}

@Composable
internal fun ADAssistantScreen(host: ADHostActions) {
    var message by remember { mutableStateOf("") }
    var webSearch by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        ADTopBar(title = "Assistant")
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ADAssistantModeTile("Voice", Icons.Outlined.Mic, Modifier.weight(1f), host.onVoiceQuestion)
                        ADAssistantModeTile("Vision", Icons.Outlined.Visibility, Modifier.weight(1f), host.onImageQuestion)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ADAssistantModeTile("Live", Icons.Outlined.GraphicEq, Modifier.weight(1f), host.onOpenChat)
                        ADAssistantModeTile("Translate", Icons.Outlined.Translate, Modifier.weight(1f), host.onOpenChat)
                    }
                }
            }
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ADColors.Surface, RoundedCornerShape(20.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    BasicTextField(
                        value = message,
                        onValueChange = { message = it },
                        modifier = Modifier.fillMaxWidth().height(62.dp),
                        singleLine = false,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = ADColors.Ink),
                        cursorBrush = SolidColor(ADColors.Blue),
                        decorationBox = { textField ->
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 4.dp, vertical = 4.dp),
                                contentAlignment = Alignment.TopStart,
                            ) {
                                if (message.isBlank()) {
                                    Text("Ask anything", style = MaterialTheme.typography.bodyLarge, color = ADColors.Muted)
                                }
                                textField()
                            }
                        },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        IconButton(onClick = host.onVoiceQuestion, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Outlined.Mic, "Voice", tint = ADColors.Muted, modifier = Modifier.size(21.dp))
                        }
                        IconButton(onClick = host.onImageQuestion, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Outlined.CameraAlt, "Add image", tint = ADColors.Muted, modifier = Modifier.size(21.dp))
                        }
                        IconButton(
                            onClick = { webSearch = !webSearch },
                            modifier = Modifier
                                .size(40.dp)
                                .background(if (webSearch) ADColors.BlueSoft else Color.Transparent, CircleShape),
                        ) {
                            Icon(
                                Icons.Outlined.Public,
                                "Web search",
                                tint = if (webSearch) ADColors.Blue else ADColors.Muted,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(
                            onClick = {
                                val prompt = message.trim()
                                if (prompt.isEmpty()) {
                                    host.onOpenChat()
                                } else {
                                    host.onOpenChatWithPrompt(prompt)
                                    message = ""
                                }
                            },
                            modifier = Modifier.size(40.dp).background(ADColors.Blue, CircleShape),
                        ) { Icon(Icons.Rounded.ArrowUpward, "Send", tint = Color.White, modifier = Modifier.size(21.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ADAssistantModeTile(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .heightIn(min = 104.dp)
            .background(ADColors.Surface, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(42.dp).background(ADColors.BlueSoft, RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = ADColors.Blue, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.height(9.dp))
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun ADLibraryScreen(
    host: ADHostActions,
    transferActive: Boolean,
    onOpenSync: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val collections = listOf(
        ADLibraryDestination("Photos and videos", Icons.Outlined.PhotoLibrary, host.onOpenPhotos),
        ADLibraryDestination("Recordings and transcripts", Icons.Outlined.GraphicEq, host.onOpenMedia),
        ADLibraryDestination("Notes and summaries", Icons.Outlined.Description, host.onOpenNotes),
    ).filter { query.isBlank() || it.title.contains(query, ignoreCase = true) }
    Column(Modifier.fillMaxSize()) {
        ADTopBar(title = "Library")
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .background(ADColors.SurfaceSubtle, RoundedCornerShape(12.dp))
                        .padding(horizontal = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Search, null, tint = ADColors.Muted, modifier = Modifier.size(20.dp))
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.padding(start = 10.dp).weight(1f),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = ADColors.Ink),
                        cursorBrush = SolidColor(ADColors.Blue),
                        decorationBox = { textField ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (query.isBlank()) {
                                    Text("Search library", style = MaterialTheme.typography.bodyLarge, color = ADColors.Muted)
                                }
                                textField()
                            }
                        },
                    )
                }
            }
            if (transferActive) {
                item {
                    ADCard(onClick = onOpenSync) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Sync, null, tint = ADColors.Blue)
                            Text(
                                "Media sync active",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(start = 12.dp).weight(1f),
                            )
                            Icon(Icons.Rounded.KeyboardArrowRight, null, tint = ADColors.Muted)
                        }
                    }
                }
            }
            item {
                if (collections.isEmpty()) {
                    Box(Modifier.fillMaxWidth().heightIn(min = 120.dp), contentAlignment = Alignment.Center) {
                        Text("No matching collections", color = ADColors.Muted)
                    }
                } else {
                    ADCard {
                        collections.forEachIndexed { index, collection ->
                            ADLibraryCollection(
                                icon = collection.icon,
                                title = collection.title,
                                onClick = collection.onClick,
                            )
                            if (index != collections.lastIndex) {
                                androidx.compose.material3.HorizontalDivider(
                                    modifier = Modifier.padding(start = 52.dp),
                                    color = ADColors.Separator,
                                )
                            }
                        }
                    }
                }
            }
            item {
                Button(
                    onClick = onOpenSync,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ADColors.Blue),
                ) {
                    Icon(Icons.Outlined.Sync, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Sync from glasses")
                }
            }
        }
    }
}

private data class ADLibraryDestination(
    val title: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

@Composable
private fun ADLibraryCollection(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).background(ADColors.BlueSoft, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = ADColors.Blue, modifier = Modifier.size(21.dp)) }
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 12.dp).weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(Icons.Rounded.KeyboardArrowRight, null, tint = ADColors.Muted)
    }
}

@Composable
internal fun ADAutomationsScreen(
    activeShortcutTitle: String?,
    onAutomation: (ADAutomation) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        ADTopBar(title = "Automations")
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (activeShortcutTitle != null) {
                item {
                    ADCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(9.dp).background(ADColors.Success, CircleShape))
                            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                Text("Active now", style = MaterialTheme.typography.labelMedium, color = ADColors.Success)
                                Text(activeShortcutTitle, style = MaterialTheme.typography.titleMedium)
                            }
                            TextButton(onClick = {}) { Text("Pause") }
                        }
                    }
                }
            }
            items(ADAutomation.entries, key = { it.name }) { automation ->
                ADAutomationCard(
                    automation = automation,
                    active = automation.title == activeShortcutTitle,
                    onClick = { onAutomation(automation) },
                )
            }
        }
    }
}

@Composable
private fun ADAutomationCard(automation: ADAutomation, active: Boolean, onClick: () -> Unit) {
    val icon = when (automation) {
        ADAutomation.LOCAL_AGENT -> Icons.Outlined.AutoAwesome
        ADAutomation.MEETING_NOTES -> Icons.Outlined.Notes
        ADAutomation.LIVE_CAPTIONS -> Icons.Outlined.GraphicEq
        ADAutomation.TRANSLATOR -> Icons.Outlined.Translate
        ADAutomation.ERRAND_BRAIN -> Icons.Outlined.Checklist
        ADAutomation.AUTO_DIARY -> Icons.Outlined.Description
        ADAutomation.AUTO_AUDIO -> Icons.Outlined.Mic
        ADAutomation.VISUAL_DIARY -> Icons.Outlined.Image
    }
    val accent = ADColors.Blue
    ADCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).background(
                    if (active) ADColors.SuccessSoft else accent.copy(alpha = 0.11f),
                    RoundedCornerShape(10.dp),
                ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = if (active) ADColors.Success else accent)
            }
            Column(Modifier.padding(start = 13.dp).weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(automation.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (active) ADStatusChip("ON", ADStatusTone.SUCCESS, showCheck = true)
                }
                Text(
                    automation.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ADColors.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(automation.outcome, style = MaterialTheme.typography.labelMedium, color = accent)
            }
            Icon(Icons.Rounded.KeyboardArrowRight, null, tint = ADColors.Muted)
        }
    }
}

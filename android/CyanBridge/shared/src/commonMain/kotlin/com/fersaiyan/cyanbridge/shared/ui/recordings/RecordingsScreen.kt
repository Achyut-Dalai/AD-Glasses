package com.fersaiyan.cyanbridge.shared.ui.recordings

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image as ImageIcon
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.fersaiyan.cyanbridge.shared.recordings.MeetingRecordingUiState
import com.fersaiyan.cyanbridge.shared.recordings.RecordingItem
import com.fersaiyan.cyanbridge.shared.recordings.SyncedMediaItem
import com.fersaiyan.cyanbridge.shared.recordings.TranscriptDialogUiState
import com.fersaiyan.cyanbridge.shared.recordings.TranscriptionEngine
import com.fersaiyan.cyanbridge.shared.recordings.TranscriptionProgressUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingsScreen(
    sessions: List<RecordingItem>,
    isLoading: Boolean,
    recentSyncedMedia: List<SyncedMediaItem>,
    playingSessionId: Long?,
    transcribingSessionId: Long?,
    meetingRecording: MeetingRecordingUiState,
    showEngineChooser: Boolean,
    selectedEngine: TranscriptionEngine,
    transcriptionProgress: TranscriptionProgressUiState?,
    transcriptDialog: TranscriptDialogUiState?,
    formatTimestamp: (Long) -> String,
    loadThumbnail: suspend (String) -> ImageBitmap?,
    onOpenSyncedMedia: () -> Unit,
    onOpenSyncedMediaItem: (SyncedMediaItem) -> Unit,
    onPlay: (RecordingItem) -> Unit,
    onTranscribe: (RecordingItem) -> Unit,
    onViewTranscript: (RecordingItem) -> Unit,
    onStopMeetingCapture: () -> Unit,
    onEngineSelected: (TranscriptionEngine) -> Unit,
    onConfirmEngine: () -> Unit,
    onDismissEngineChooser: () -> Unit,
    onDismissTranscript: () -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("Recordings") },
                actions = {
                    IconButton(onClick = onOpenSyncedMedia) {
                        Icon(
                            imageVector = Icons.Outlined.ImageIcon,
                            contentDescription = "Open synced media",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (meetingRecording.isRecording) {
                item {
                    MeetingRecordingBanner(
                        sourceLabel = meetingRecording.sourceLabel,
                        onStop = onStopMeetingCapture,
                    )
                }
            }
            if (recentSyncedMedia.isNotEmpty()) {
                item {
                    Text(
                        text = "Recent synced photos",
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("recent_synced_media"),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        recentSyncedMedia.take(4).forEach { item ->
                            var thumbnail by remember(item.contentUriString) {
                                mutableStateOf<ImageBitmap?>(null)
                            }
                            LaunchedEffect(item.contentUriString) {
                                thumbnail = loadThumbnail(item.contentUriString)
                            }
                            SyncedMediaPreview(
                                item = item,
                                thumbnail = thumbnail,
                                modifier = Modifier.weight(1f),
                                onClick = { onOpenSyncedMediaItem(item) },
                            )
                        }
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = onOpenSyncedMedia,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ImageIcon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Open synced media")
                }
            }
            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (sessions.isEmpty()) {
                item {
                    EmptyRecordingsState()
                }
            } else {
                item {
                    Text(
                        text = "Meeting captures",
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                items(sessions, key = { it.id }) { session ->
                    RecordingCard(
                        title = session.title,
                        metadata = session.metadata,
                        stopReason = session.stopReason,
                        isPlaying = playingSessionId == session.id,
                        isTranscribing = transcribingSessionId == session.id,
                        onPlay = { onPlay(session) },
                        onTranscribe = { onTranscribe(session) },
                        onViewTranscript = { onViewTranscript(session) },
                    )
                }
            }
        }
    }

    if (showEngineChooser) {
        TranscriptionEngineDialog(
            selectedEngine = selectedEngine,
            onEngineSelected = onEngineSelected,
            onConfirm = onConfirmEngine,
            onDismiss = onDismissEngineChooser,
        )
    }
    transcriptionProgress?.let { progress ->
        TranscriptionProgressDialog(progress)
    }
    transcriptDialog?.let { transcript ->
        AlertDialog(
            onDismissRequest = onDismissTranscript,
            title = { Text(transcript.title) },
            text = { Text(transcript.text) },
            confirmButton = {
                TextButton(onClick = onDismissTranscript) {
                    Text("Close")
                }
            },
        )
    }
}

@Composable
private fun MeetingRecordingBanner(
    sourceLabel: String?,
    onStop: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 10.dp, end = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Recording active", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = sourceLabel ?: "Detecting audio source",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = onStop) {
                Icon(
                    imageVector = Icons.Outlined.Stop,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text("Stop")
            }
        }
    }
}

@Composable
private fun SyncedMediaPreview(
    item: SyncedMediaItem,
    thumbnail: ImageBitmap?,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier
            .aspectRatio(1f)
            .testTag("recent_synced_media_${item.id}")
            .clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail!!,
                    contentDescription = item.displayName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.ImageIcon,
                    contentDescription = item.displayName,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptyRecordingsState() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("No recordings yet", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Start a meeting capture from the Glasses tab to create one.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RecordingCard(
    title: String,
    metadata: String,
    stopReason: String?,
    isPlaying: Boolean,
    isTranscribing: Boolean,
    onPlay: () -> Unit,
    onTranscribe: () -> Unit,
    onViewTranscript: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPlay) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        contentDescription = if (isPlaying) "Stop playback" else "Play recording",
                    )
                }
                Spacer(Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = metadata,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    stopReason?.takeIf { it.isNotBlank() }?.let { reason ->
                        Text(
                            text = "Stopped: $reason",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = onTranscribe,
                    enabled = !isTranscribing,
                ) {
                    if (isTranscribing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Transcribing")
                    } else {
                        Text("Transcribe")
                    }
                }
                OutlinedButton(
                    onClick = onViewTranscript,
                    enabled = !isTranscribing,
                ) {
                    Text("View transcript")
                }
            }
        }
    }
}

@Composable
private fun TranscriptionEngineDialog(
    selectedEngine: TranscriptionEngine,
    onEngineSelected: (TranscriptionEngine) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Transcription engine") },
        text = {
            Column {
                TranscriptionEngine.entries.forEach { engine ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEngineSelected(engine) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = engine == selectedEngine,
                            onClick = { onEngineSelected(engine) },
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(engine.title, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = if (engine == TranscriptionEngine.MOONSHINE) {
                                    "Local Moonshine speech model"
                                } else {
                                    "Gemma with the LiteRT local runtime"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Start") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun TranscriptionProgressDialog(state: TranscriptionProgressUiState) {
    val progress = state.progress
    Dialog(onDismissRequest = {}) {
        Card {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(state.title, style = MaterialTheme.typography.titleLarge)
                Text(
                    state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (progress == null) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

private val TranscriptionEngine.title: String
    get() = when (this) {
        TranscriptionEngine.MOONSHINE -> "Moonshine (local)"
        TranscriptionEngine.GEMMA -> "Gemma (LiteRT local)"
    }

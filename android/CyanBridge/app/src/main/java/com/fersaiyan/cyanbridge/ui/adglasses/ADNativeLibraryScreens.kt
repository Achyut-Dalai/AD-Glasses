package com.fersaiyan.cyanbridge.ui.adglasses

import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fersaiyan.cyanbridge.data.local.entity.Note
import com.fersaiyan.cyanbridge.data.local.entity.TranscriptionRecord
import com.fersaiyan.cyanbridge.shared.recordings.SyncedMediaItem
import com.fersaiyan.cyanbridge.ui.MyApplication
import com.fersaiyan.cyanbridge.ui.recordings.SyncedMediaQuery
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun ADNativeLibraryScreen(
    transferActive: Boolean,
    onOpenSync: () -> Unit,
    onCaptures: () -> Unit,
    onRecordings: () -> Unit,
    onNotes: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        ADTopBar(title = "Library")
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (transferActive) {
                item {
                    ADCard(onClick = onOpenSync) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(40.dp).background(ADColors.CyanSoft, RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Outlined.Sync, null, tint = ADColors.CyanDeep) }
                            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                Text("Sync in progress", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "New glasses media is being added to Library",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ADColors.Muted,
                                )
                            }
                            ADStatusChip("LIVE", ADStatusTone.INFO)
                        }
                    }
                }
            }

            item { ADSectionTitle("Saved on this phone") }

            item {
                ADCard {
                    ADNativeLibraryRow(
                        icon = Icons.Outlined.Image,
                        title = "Captures",
                        subtitle = "Photos and videos synced from the glasses",
                        onClick = onCaptures,
                    )
                    HorizontalDivider(Modifier.padding(start = 52.dp), color = ADColors.Separator)
                    ADNativeLibraryRow(
                        icon = Icons.Outlined.GraphicEq,
                        title = "Recordings & transcripts",
                        subtitle = "Audio sessions and saved transcription text",
                        onClick = onRecordings,
                    )
                    HorizontalDivider(Modifier.padding(start = 52.dp), color = ADColors.Separator)
                    ADNativeLibraryRow(
                        icon = Icons.Outlined.Notes,
                        title = "Notes & summaries",
                        subtitle = "Meeting notes and summaries created from transcripts",
                        onClick = onNotes,
                    )
                }
            }

            item {
                Button(
                    onClick = onOpenSync,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ADColors.Graphite),
                ) {
                    Icon(Icons.Outlined.Sync, null)
                    Spacer(Modifier.size(8.dp))
                    Text("Sync from glasses")
                }
            }
        }
    }
}

@Composable
internal fun ADNativeCapturesScreen(
    onBack: () -> Unit,
    onOpenSync: () -> Unit,
) {
    val context = LocalContext.current
    var media by remember { mutableStateOf<List<SyncedMediaItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    fun open(item: SyncedMediaItem) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(item.contentUriString), if (item.isVideo) "video/*" else "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { context.startActivity(intent) }
            .onFailure { error = "Couldn’t open that capture." }
    }

    LaunchedEffect(Unit) {
        loading = true
        error = null
        media = runCatching {
            withContext(Dispatchers.IO) { SyncedMediaQuery.query(context) }
        }.onFailure {
            error = "Couldn’t read synced media."
        }.getOrDefault(emptyList())
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        ADTopBar(title = "Captures", showBack = true, onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 4.dp, 16.dp, 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            error?.let { message -> item { ADLibraryMessage(message, warning = true) } }
            when {
                loading -> item {
                    ADCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = ADColors.Cyan,
                            )
                            Text(
                                "Loading captures",
                                modifier = Modifier.padding(start = 11.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = ADColors.Muted,
                            )
                        }
                    }
                }
                media.isEmpty() -> {
                    item {
                        ADEmptyLibraryState(
                            title = "No captures yet",
                            detail = "Sync photos and videos from the glasses when you’re ready.",
                        )
                    }
                    item {
                        Button(
                            onClick = onOpenSync,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ADColors.Graphite),
                        ) { Text("Sync from glasses") }
                    }
                }
                else -> items(media, key = { "${it.id}-${it.isVideo}" }) { item ->
                    ADCaptureCard(item = item, onClick = { open(item) })
                }
            }
        }
    }
}

@Composable
private fun ADCaptureCard(
    item: SyncedMediaItem,
    onClick: () -> Unit,
) {
    ADCard(onClick = onClick) {
        ADCapturePreview(item)
        Spacer(Modifier.size(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (item.isVideo) "Video capture" else "Photo capture",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(
                    item.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = ADColors.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            ADStatusChip(if (item.isVideo) "VIDEO" else "PHOTO", ADStatusTone.NEUTRAL)
        }
    }
}

@Composable
private fun ADCapturePreview(item: SyncedMediaItem) {
    val context = LocalContext.current
    var thumbnail by remember(item.contentUriString) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(item.contentUriString) {
        thumbnail = withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext null
            runCatching {
                // 960px was visibly soft on high-density 1080p/1440p devices when stretched
                // across a full-width 16:10 card. Keep enough source pixels for crisp display.
                context.contentResolver.loadThumbnail(
                    Uri.parse(item.contentUriString),
                    Size(1440, 900),
                    null,
                )
            }.getOrNull()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 10f)
            .clip(RoundedCornerShape(14.dp))
            .background(ADColors.SurfaceSubtle),
        contentAlignment = Alignment.Center,
    ) {
        thumbnail?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = item.displayName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } ?: Icon(
            if (item.isVideo) Icons.Outlined.Videocam else Icons.Outlined.Image,
            contentDescription = null,
            tint = ADColors.Muted,
            modifier = Modifier.size(34.dp),
        )

        if (item.isVideo) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(ADColors.Graphite.copy(alpha = 0.84f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.PlayArrow,
                    contentDescription = "Play video",
                    tint = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
internal fun ADNativeRecordingsScreen(onBack: () -> Unit) {
    val sessionsFlow = remember { MyApplication.repository.getAllCaptureSessions() }
    val sessions by sessionsFlow.collectAsState(initial = emptyList())
    var transcripts by remember { mutableStateOf<Map<Long, TranscriptionRecord>>(emptyMap()) }
    var expandedTranscriptId by remember { mutableStateOf<Long?>(null) }
    var playingId by remember { mutableStateOf<Long?>(null) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    val player = remember { MediaPlayer() }

    DisposableEffect(player) {
        onDispose {
            runCatching { player.stop() }
            player.release()
        }
    }

    LaunchedEffect(sessions.map { it.id }) {
        transcripts = withContext(Dispatchers.IO) {
            sessions.mapNotNull { session ->
                MyApplication.repository.getTranscriptionByCaptureSessionId(session.id)
                    ?.let { session.id to it }
            }.toMap()
        }
    }

    fun togglePlayback(sessionId: Long, audioPath: String) {
        playbackError = null
        if (playingId == sessionId) {
            runCatching { player.stop(); player.reset() }
            playingId = null
            return
        }
        val file = File(audioPath)
        if (!file.exists()) {
            playbackError = "Audio file is no longer available on this phone."
            return
        }
        runCatching {
            player.reset()
            player.setDataSource(audioPath)
            player.setOnCompletionListener {
                runCatching { it.reset() }
                playingId = null
            }
            player.prepare()
            player.start()
            playingId = sessionId
        }.onFailure {
            playingId = null
            playbackError = "Couldn’t play that recording."
        }
    }

    Column(Modifier.fillMaxSize()) {
        ADTopBar(title = "Recordings & transcripts", showBack = true, onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 4.dp, 16.dp, 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            playbackError?.let { message -> item { ADLibraryMessage(message, warning = true) } }
            if (sessions.isEmpty()) {
                item {
                    ADEmptyLibraryState(
                        title = "No recordings yet",
                        detail = "Record from Home or start Meeting Notes through the glasses.",
                    )
                }
            } else {
                items(sessions, key = { it.id }) { session ->
                    val transcription = transcripts[session.id]
                    val isPlaying = playingId == session.id
                    ADCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(
                                        if (isPlaying) ADColors.CyanSoft else ADColors.SurfaceSubtle,
                                        RoundedCornerShape(13.dp),
                                    )
                                    .clickable { togglePlayback(session.id, session.audioPath) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                                    contentDescription = if (isPlaying) "Stop" else "Play",
                                    tint = if (isPlaying) ADColors.CyanDeep else ADColors.Ink,
                                )
                            }
                            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                Text(formatDate(session.startedAt), style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${formatDuration(session.durationSec)} · ${friendlySource(session.captureSource)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ADColors.Muted,
                                )
                            }
                            transcription?.let {
                                ADStatusChip(
                                    if (!it.transcriptText.isNullOrBlank()) "TEXT" else it.status.uppercase(),
                                    if (it.error == null) ADStatusTone.NEUTRAL else ADStatusTone.WARNING,
                                )
                            }
                        }
                        if (transcription != null && !transcription.transcriptText.isNullOrBlank()) {
                            Spacer(Modifier.size(11.dp))
                            HorizontalDivider(color = ADColors.Separator)
                            Spacer(Modifier.size(9.dp))
                            Text(
                                if (expandedTranscriptId == session.id) transcription.transcriptText.orEmpty()
                                else transcription.transcriptText.orEmpty().take(220),
                                style = MaterialTheme.typography.bodyMedium,
                                color = ADColors.Muted,
                            )
                            if (transcription.transcriptText.orEmpty().length > 220) {
                                Text(
                                    if (expandedTranscriptId == session.id) "Show less" else "Show full transcript",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = ADColors.CyanDeep,
                                    modifier = Modifier
                                        .clickable {
                                            expandedTranscriptId = if (expandedTranscriptId == session.id) null else session.id
                                        }
                                        .padding(top = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ADNativeNotesScreen(onBack: () -> Unit) {
    val notesFlow = remember { MyApplication.notesRepository.getAllNotes() }
    val notes by notesFlow.collectAsState(initial = emptyList())
    var expandedNoteId by remember { mutableStateOf<Long?>(null) }

    Column(Modifier.fillMaxSize()) {
        ADTopBar(title = "Notes & summaries", showBack = true, onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 4.dp, 16.dp, 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (notes.isEmpty()) {
                item {
                    ADEmptyLibraryState(
                        title = "No notes yet",
                        detail = "Meeting summaries and transcript-derived notes will appear here.",
                    )
                }
            } else {
                items(notes, key = { it.id }) { note ->
                    ADNativeNoteCard(
                        note = note,
                        expanded = expandedNoteId == note.id,
                        onToggle = {
                            expandedNoteId = if (expandedNoteId == note.id) null else note.id
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ADNativeNoteCard(note: Note, expanded: Boolean, onToggle: () -> Unit) {
    ADCard(onClick = onToggle) {
        Text(
            note.title.ifBlank { "Untitled note" },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.size(2.dp))
        Text(formatDate(note.createdAt), style = MaterialTheme.typography.labelSmall, color = ADColors.Muted)
        Spacer(Modifier.size(9.dp))
        Text(
            if (expanded) note.summary else note.summary.take(260),
            style = MaterialTheme.typography.bodyMedium,
            color = ADColors.Ink,
        )
        if (!note.transcript.isNullOrBlank() && expanded) {
            Spacer(Modifier.size(11.dp))
            HorizontalDivider(color = ADColors.Separator)
            Spacer(Modifier.size(9.dp))
            Text("Transcript", style = MaterialTheme.typography.labelLarge, color = ADColors.Muted)
            Spacer(Modifier.size(4.dp))
            Text(note.transcript.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = ADColors.Muted)
        }
        if (note.summary.length > 260 || !note.transcript.isNullOrBlank()) {
            Text(
                if (expanded) "Show less" else "Open note",
                style = MaterialTheme.typography.labelLarge,
                color = ADColors.CyanDeep,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun ADNativeLibraryRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).background(ADColors.SurfaceSubtle, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = ADColors.Ink, modifier = Modifier.size(20.dp)) }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
        }
        Icon(Icons.Rounded.KeyboardArrowRight, null, tint = ADColors.Muted, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun ADEmptyLibraryState(title: String, detail: String) {
    ADCard {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.size(4.dp))
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = ADColors.Muted)
    }
}

@Composable
private fun ADLibraryMessage(message: String, warning: Boolean = false) {
    ADCard {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = if (warning) ADColors.Warning else ADColors.Muted,
        )
    }
}

private fun formatDate(timestamp: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp))

private fun formatDuration(seconds: Long): String {
    val minutes = seconds / 60
    val remaining = seconds % 60
    return if (minutes > 0) "${minutes}m ${remaining}s" else "${remaining}s"
}

private fun friendlySource(source: String): String = source
    .lowercase()
    .replace('_', ' ')
    .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

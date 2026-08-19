package com.fersaiyan.cyanbridge.ui.adglasses

import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
    ADExpressiveLibraryHome(
        transferActive = transferActive,
        onOpenSync = onOpenSync,
        onCaptures = onCaptures,
        onRecordings = onRecordings,
        onNotes = onNotes,
    )
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
        ADTopBar(showBack = true, onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 4.dp, 16.dp, 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                ADLibraryDetailHeader(
                    eyebrow = "Library",
                    title = "Captures",
                    detail = "Photos and videos copied from your glasses live here in the order they arrived.",
                    glyph = ADGlyph.LIBRARY,
                )
            }
            error?.let { message -> item { ADLibraryMessage(message, warning = true) } }
            when {
                loading -> item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        color = ADColors.Surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = ADColors.Ink)
                            Text("Loading captures", Modifier.padding(start = 10.dp), style = MaterialTheme.typography.bodyMedium, color = ADColors.Muted)
                        }
                    }
                }
                media.isEmpty() -> {
                    item {
                        ADEmptyLibraryState(
                            glyph = ADGlyph.PHOTO,
                            title = "No captures yet",
                            detail = "Sync photos and videos from the glasses when you’re ready.",
                        )
                    }
                    item { ADPrimaryButton(text = "Sync from glasses", onClick = onOpenSync) }
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
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = ADColors.Surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(10.dp)) {
            ADCapturePreview(item)
            Row(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(40.dp).background(ADColors.SurfaceSubtle, RoundedCornerShape(13.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    ADGlyphIcon(if (item.isVideo) ADGlyph.VIDEO else ADGlyph.PHOTO, ADColors.Ink, Modifier.size(22.dp))
                }
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Text(item.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (item.isVideo) "Video from glasses" else "Photo from glasses", style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
                }
                ADStatusChip(if (item.isVideo) "VIDEO" else "PHOTO")
            }
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
                context.contentResolver.loadThumbnail(Uri.parse(item.contentUriString), Size(960, 600), null)
            }.getOrNull()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 10f)
            .clip(RoundedCornerShape(20.dp))
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
        } ?: ADGlyphIcon(if (item.isVideo) ADGlyph.VIDEO else ADGlyph.PHOTO, ADColors.Muted, Modifier.size(38.dp))

        if (item.isVideo) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = ADColors.Ink.copy(alpha = 0.86f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = "Play video", tint = ADColors.Surface, modifier = Modifier.size(27.dp))
                }
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
                MyApplication.repository.getTranscriptionByCaptureSessionId(session.id)?.let { session.id to it }
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
        ADTopBar(showBack = true, onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 4.dp, 16.dp, 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                ADLibraryDetailHeader(
                    eyebrow = "Library",
                    title = "Recordings",
                    detail = "Audio sessions and transcripts, kept together so you can listen or read without switching views.",
                    glyph = ADGlyph.AUDIO,
                )
            }
            playbackError?.let { message -> item { ADLibraryMessage(message, warning = true) } }
            if (sessions.isEmpty()) {
                item {
                    ADEmptyLibraryState(
                        glyph = ADGlyph.AUDIO,
                        title = "No recordings yet",
                        detail = "Record from Home or start Soundbites through the glasses.",
                    )
                }
            } else {
                items(sessions, key = { it.id }) { session ->
                    val transcription = transcripts[session.id]
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        color = ADColors.Surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    onClick = { togglePlayback(session.id, session.audioPath) },
                                    modifier = Modifier.size(46.dp),
                                    shape = RoundedCornerShape(15.dp),
                                    color = if (playingId == session.id) ADColors.Ink else ADColors.SurfaceSubtle,
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            if (playingId == session.id) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                                            contentDescription = if (playingId == session.id) "Stop" else "Play",
                                            tint = if (playingId == session.id) ADColors.Surface else ADColors.Ink,
                                            modifier = Modifier.size(23.dp),
                                        )
                                    }
                                }
                                Column(Modifier.padding(start = 11.dp).weight(1f)) {
                                    Text(formatDate(session.startedAt), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                                    Text("${formatDuration(session.durationSec)} · ${friendlySource(session.captureSource)}", style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
                                }
                                transcription?.let {
                                    ADStatusChip(
                                        if (!it.transcriptText.isNullOrBlank()) "TEXT" else it.status.uppercase(),
                                        if (it.error == null) ADStatusTone.SUCCESS else ADStatusTone.WARNING,
                                    )
                                }
                            }
                            if (transcription != null && !transcription.transcriptText.isNullOrBlank()) {
                                Spacer(Modifier.size(11.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Spacer(Modifier.size(10.dp))
                                Text(
                                    if (expandedTranscriptId == session.id) transcription.transcriptText.orEmpty() else transcription.transcriptText.orEmpty().take(220),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = ADColors.Muted,
                                )
                                if (transcription.transcriptText.orEmpty().length > 220) {
                                    Text(
                                        if (expandedTranscriptId == session.id) "Show less" else "Show full transcript",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = ADColors.Ink,
                                        modifier = Modifier.clickable {
                                            expandedTranscriptId = if (expandedTranscriptId == session.id) null else session.id
                                        }.padding(top = 8.dp),
                                    )
                                }
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
        ADTopBar(showBack = true, onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 4.dp, 16.dp, 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                ADLibraryDetailHeader(
                    eyebrow = "Library",
                    title = "Notes",
                    detail = "Summaries and transcript-derived notes, shaped for review instead of raw log browsing.",
                    glyph = ADGlyph.PROMPT,
                )
            }
            if (notes.isEmpty()) {
                item {
                    ADEmptyLibraryState(
                        glyph = ADGlyph.PROMPT,
                        title = "No notes yet",
                        detail = "Meeting summaries and transcript-derived notes will appear here.",
                    )
                }
            } else {
                items(notes, key = { it.id }) { note ->
                    ADNativeNoteCard(
                        note = note,
                        expanded = expandedNoteId == note.id,
                        onToggle = { expandedNoteId = if (expandedNoteId == note.id) null else note.id },
                    )
                }
            }
        }
    }
}

@Composable
private fun ADNativeNoteCard(note: Note, expanded: Boolean, onToggle: () -> Unit) {
    Surface(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = ADColors.Surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    Modifier.size(44.dp).background(ADColors.SurfaceSubtle, RoundedCornerShape(15.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    ADGlyphIcon(ADGlyph.PROMPT, ADColors.Ink, Modifier.size(24.dp))
                }
                Column(Modifier.padding(start = 11.dp).weight(1f)) {
                    Text(note.title.ifBlank { "Untitled note" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    Text(formatDate(note.createdAt), style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
                }
            }
            Spacer(Modifier.size(10.dp))
            Text(if (expanded) note.summary else note.summary.take(260), style = MaterialTheme.typography.bodyMedium, color = ADColors.Muted)
            if (!note.transcript.isNullOrBlank() && expanded) {
                Spacer(Modifier.size(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.size(9.dp))
                Text("TRANSCRIPT", style = MaterialTheme.typography.labelSmall.copy(fontFamily = ADTechFontFamily), color = ADColors.Muted)
                Spacer(Modifier.size(4.dp))
                Text(note.transcript.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = ADColors.Muted)
            }
            if (note.summary.length > 260 || !note.transcript.isNullOrBlank()) {
                Text(
                    if (expanded) "Show less" else "Open note",
                    style = MaterialTheme.typography.labelMedium,
                    color = ADColors.Ink,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun ADLibraryDetailHeader(
    eyebrow: String,
    title: String,
    detail: String,
    glyph: ADGlyph,
) {
    Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
        ADScreenIntro(eyebrow = eyebrow, title = title, detail = detail)
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(min = 92.dp),
            shape = RoundedCornerShape(24.dp),
            color = ADColors.Ink,
            contentColor = ADColors.Surface,
        ) {
            Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(50.dp),
                    shape = RoundedCornerShape(17.dp),
                    color = ADColors.Surface.copy(alpha = 0.13f),
                ) {
                    Box(contentAlignment = Alignment.Center) { ADGlyphIcon(glyph, ADColors.Surface, Modifier.size(27.dp)) }
                }
                Column(Modifier.padding(start = 12.dp)) {
                    Text("ON THIS PHONE", style = MaterialTheme.typography.labelSmall.copy(fontFamily = ADTechFontFamily), color = ADColors.Surface.copy(alpha = 0.58f))
                    Text("Private library", style = MaterialTheme.typography.titleMedium, color = ADColors.Surface)
                }
            }
        }
    }
}

@Composable
private fun ADEmptyLibraryState(glyph: ADGlyph, title: String, detail: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = ADColors.Surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(58.dp).background(ADColors.SurfaceSubtle, RoundedCornerShape(19.dp)),
                contentAlignment = Alignment.Center,
            ) { ADGlyphIcon(glyph, ADColors.Ink, Modifier.size(31.dp)) }
            Spacer(Modifier.size(12.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
            Spacer(Modifier.size(4.dp))
            Text(detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
        }
    }
}

@Composable
private fun ADLibraryMessage(message: String, warning: Boolean = false) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = if (warning) ADColors.WarningSoft else ADColors.SurfaceSubtle,
    ) {
        Text(message, Modifier.padding(13.dp), style = MaterialTheme.typography.bodySmall, color = if (warning) ADColors.Warning else ADColors.Muted)
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

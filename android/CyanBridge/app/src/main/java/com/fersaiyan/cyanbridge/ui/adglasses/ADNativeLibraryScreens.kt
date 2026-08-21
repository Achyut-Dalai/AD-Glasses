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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
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
import androidx.compose.ui.graphics.Color
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
    ADExpressiveLibraryHome(transferActive, onOpenSync, onCaptures, onRecordings, onNotes)
}

@Composable
internal fun ADNativeCapturesScreen(onBack: () -> Unit, onOpenSync: () -> Unit) {
    val context = LocalContext.current
    var media by remember { mutableStateOf<List<SyncedMediaItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    fun open(item: SyncedMediaItem) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(item.contentUriString), if (item.isVideo) "video/*" else "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { context.startActivity(intent) }.onFailure { error = "Couldn’t open that capture." }
    }

    LaunchedEffect(Unit) {
        loading = true
        error = null
        media = runCatching { withContext(Dispatchers.IO) { SyncedMediaQuery.query(context) } }
            .onFailure { error = "Couldn’t read synced media." }
            .getOrDefault(emptyList())
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        ADTopBar(title = "Captures", showBack = true, onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp, 5.dp, 12.dp, 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                ADLibraryDetailHeader(
                    title = "Captures",
                    detail = "Photos and video copied from your glasses.",
                    glyph = ADMatrixGlyph.PHOTO,
                    meta = if (loading) "READING" else "${media.size} ITEMS",
                )
            }
            error?.let { message -> item { ADLibraryMessage(message, warning = true) } }
            when {
                loading -> item { ADLibraryLoading("Reading captures") }
                media.isEmpty() -> {
                    item {
                        ADEmptyLibraryState(
                            ADMatrixGlyph.PHOTO,
                            "No captures yet",
                            "Sync photos and video from the glasses when you’re ready.",
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
private fun ADCaptureCard(item: SyncedMediaItem, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        color = ADColors.Surface,
        border = BorderStroke(1.dp, ADColors.Outline),
    ) {
        Column(Modifier.padding(7.dp)) {
            ADCapturePreview(item)
            Row(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(31.dp).background(ADColors.SurfaceSubtle, RoundedCornerShape(9.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    ADMatrixGlyphIcon(
                        if (item.isVideo) ADMatrixGlyph.VIDEO else ADMatrixGlyph.PHOTO,
                        ADColors.Ink,
                        Modifier.size(18.dp),
                        accent = if (item.isVideo) ADColors.Red else null,
                    )
                }
                Column(Modifier.padding(start = 8.dp).weight(1f)) {
                    Text(
                        item.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = ADColors.Ink,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (item.isVideo) "Video from glasses" else "Photo from glasses",
                        style = MaterialTheme.typography.bodySmall,
                        color = ADColors.Muted,
                    )
                }
                Text(
                    if (item.isVideo) "VIDEO" else "PHOTO",
                    style = ADMetaTextStyle,
                    color = ADColors.Muted,
                )
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
            runCatching { context.contentResolver.loadThumbnail(Uri.parse(item.contentUriString), Size(960, 600), null) }.getOrNull()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 10f)
            .clip(RoundedCornerShape(12.dp))
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
        } ?: ADMatrixGlyphIcon(
            if (item.isVideo) ADMatrixGlyph.VIDEO else ADMatrixGlyph.PHOTO,
            ADColors.Muted,
            Modifier.size(32.dp),
        )

        if (item.isVideo) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = Color.Black.copy(alpha = .74f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = .16f)),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.PlayArrow, "Play video", tint = Color.White, modifier = Modifier.size(22.dp))
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
        ADTopBar(title = "Recordings", showBack = true, onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp, 5.dp, 12.dp, 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                ADLibraryDetailHeader(
                    title = "Recordings",
                    detail = "Audio sessions with transcript context when available.",
                    glyph = ADMatrixGlyph.AUDIO,
                    meta = "${sessions.size} SESSIONS",
                )
            }
            playbackError?.let { message -> item { ADLibraryMessage(message, warning = true) } }
            if (sessions.isEmpty()) {
                item {
                    ADEmptyLibraryState(
                        ADMatrixGlyph.AUDIO,
                        "No recordings yet",
                        "Record from Home or start Soundbites through the glasses.",
                    )
                }
            } else {
                items(sessions, key = { it.id }) { session ->
                    val transcription = transcripts[session.id]
                    val active = playingId == session.id
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(15.dp),
                        color = ADColors.Surface,
                        border = BorderStroke(1.dp, if (active) ADColors.Ink.copy(alpha = .30f) else ADColors.Outline),
                    ) {
                        Column(Modifier.padding(11.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    onClick = { togglePlayback(session.id, session.audioPath) },
                                    modifier = Modifier.size(40.dp),
                                    shape = RoundedCornerShape(11.dp),
                                    color = if (active) ADColors.Red else ADColors.SurfaceSubtle,
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            if (active) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                                            contentDescription = if (active) "Stop" else "Play",
                                            tint = if (active) Color.White else ADColors.Ink,
                                            modifier = Modifier.size(19.dp),
                                        )
                                    }
                                }
                                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                                    Text(
                                        formatDate(session.startedAt),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = ADColors.Ink,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Text(
                                        "${formatDuration(session.durationSec)} · ${friendlySource(session.captureSource)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ADColors.Muted,
                                    )
                                }
                                if (transcription != null) {
                                    ADMatrixGlyphIcon(
                                        if (!transcription.transcriptText.isNullOrBlank()) ADMatrixGlyph.DIARY else ADMatrixGlyph.AUDIO,
                                        if (transcription.error == null) ADColors.InkSoft else ADColors.Warning,
                                        Modifier.size(17.dp),
                                    )
                                }
                            }

                            Spacer(Modifier.size(9.dp))
                            ADRecordingWaveStrip(active = active)

                            if (transcription != null && !transcription.transcriptText.isNullOrBlank()) {
                                Spacer(Modifier.size(9.dp))
                                HorizontalDivider(color = ADColors.Separator)
                                Spacer(Modifier.size(8.dp))
                                Text("TRANSCRIPT", style = ADMetaTextStyle, color = ADColors.Muted)
                                Spacer(Modifier.size(4.dp))
                                Text(
                                    if (expandedTranscriptId == session.id) {
                                        transcription.transcriptText.orEmpty()
                                    } else {
                                        transcription.transcriptText.orEmpty().take(220)
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = ADColors.InkSoft,
                                )
                                if (transcription.transcriptText.orEmpty().length > 220) {
                                    Text(
                                        if (expandedTranscriptId == session.id) "Show less" else "Read transcript",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = ADColors.Ink,
                                        modifier = Modifier
                                            .clickable {
                                                expandedTranscriptId = if (expandedTranscriptId == session.id) null else session.id
                                            }
                                            .padding(top = 7.dp),
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
private fun ADRecordingWaveStrip(active: Boolean) {
    val heights = listOf(7, 13, 20, 11, 17, 9, 15, 6, 18, 10, 14, 8)
    Surface(
        modifier = Modifier.fillMaxWidth().height(32.dp),
        shape = RoundedCornerShape(9.dp),
        color = ADColors.SurfaceSubtle,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            heights.forEachIndexed { index, value ->
                Box(
                    Modifier
                        .width(2.dp)
                        .height(value.dp)
                        .background(
                            if (active && index in 3..7) ADColors.Red else ADColors.InkSoft.copy(alpha = .42f),
                            CircleShape,
                        ),
                )
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
        ADTopBar(title = "Notes", showBack = true, onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp, 5.dp, 12.dp, 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                ADLibraryDetailHeader(
                    title = "Notes",
                    detail = "Summaries and transcript-derived memory you can revisit.",
                    glyph = ADMatrixGlyph.DIARY,
                    meta = "${notes.size} NOTES",
                )
            }
            if (notes.isEmpty()) {
                item {
                    ADEmptyLibraryState(
                        ADMatrixGlyph.DIARY,
                        "No notes yet",
                        "Meeting summaries and transcript-derived notes will appear here.",
                    )
                }
            } else {
                items(notes, key = { it.id }) { note ->
                    ADNativeNoteCard(note, expandedNoteId == note.id) {
                        expandedNoteId = if (expandedNoteId == note.id) null else note.id
                    }
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
        shape = RoundedCornerShape(15.dp),
        color = ADColors.Surface,
        border = BorderStroke(1.dp, ADColors.Outline),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier.size(34.dp).background(ADColors.SurfaceSubtle, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    ADMatrixGlyphIcon(ADMatrixGlyph.DIARY, ADColors.Ink, Modifier.size(19.dp))
                }
                Column(Modifier.padding(start = 9.dp).weight(1f)) {
                    Text(
                        note.title.ifBlank { "Untitled note" },
                        style = MaterialTheme.typography.titleMedium,
                        color = ADColors.Ink,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(formatDate(note.createdAt), style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
                }
                ADMatrixGlyphIcon(
                    if (expanded) ADMatrixGlyph.BACK else ADMatrixGlyph.NEXT,
                    ADColors.Muted,
                    Modifier.size(15.dp),
                )
            }
            Spacer(Modifier.size(9.dp))
            Text(
                if (expanded) note.summary else note.summary.take(260),
                style = MaterialTheme.typography.bodyMedium,
                color = ADColors.InkSoft,
            )
            if (!note.transcript.isNullOrBlank() && expanded) {
                Spacer(Modifier.size(9.dp))
                HorizontalDivider(color = ADColors.Separator)
                Spacer(Modifier.size(8.dp))
                Text("SOURCE TRANSCRIPT", style = ADMetaTextStyle, color = ADColors.Muted)
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
    title: String,
    detail: String,
    glyph: ADMatrixGlyph,
    meta: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(17.dp),
        color = ADColors.Surface,
        border = BorderStroke(1.dp, ADColors.Outline),
    ) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(44.dp).background(ADColors.SurfaceSubtle, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                ADMatrixGlyphIcon(
                    glyph,
                    ADColors.Ink,
                    Modifier.size(25.dp),
                    accent = if (glyph == ADMatrixGlyph.AUDIO) ADColors.Red else null,
                )
            }
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text("LIBRARY · $meta", style = ADMetaTextStyle, color = ADColors.Muted)
                Text(title, style = MaterialTheme.typography.titleLarge, color = ADColors.Ink, fontWeight = FontWeight.SemiBold)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted, maxLines = 2)
            }
        }
    }
}

@Composable
private fun ADEmptyLibraryState(glyph: ADMatrixGlyph, title: String, detail: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = ADColors.Surface,
        border = BorderStroke(1.dp, ADColors.Outline),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(40.dp).background(ADColors.SurfaceSubtle, RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center,
            ) {
                ADMatrixGlyphIcon(
                    glyph,
                    ADColors.InkSoft,
                    Modifier.size(23.dp),
                    accent = if (glyph == ADMatrixGlyph.AUDIO) ADColors.Red else null,
                )
            }
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = ADColors.Ink, fontWeight = FontWeight.Medium)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
            }
        }
    }
}

@Composable
private fun ADLibraryLoading(label: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(13.dp),
        color = ADColors.Surface,
        border = BorderStroke(1.dp, ADColors.Outline),
    ) {
        Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 1.5.dp, color = ADColors.Red)
            Text(label, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium, color = ADColors.Muted)
        }
    }
}

@Composable
private fun ADLibraryMessage(message: String, warning: Boolean = false) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(11.dp),
        color = if (warning) ADColors.WarningSoft else ADColors.SurfaceSubtle,
        border = BorderStroke(1.dp, if (warning) ADColors.Warning.copy(alpha = .28f) else ADColors.Outline),
    ) {
        Text(
            message,
            Modifier.padding(10.dp),
            style = MaterialTheme.typography.bodySmall,
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

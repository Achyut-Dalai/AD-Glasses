package com.fersaiyan.cyanbridge.ui.adglasses

import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fersaiyan.cyanbridge.agent.LocalAgentPrefs
import com.fersaiyan.cyanbridge.media.SyncedMediaFolder
import com.fersaiyan.cyanbridge.privacy.PrivacyPrefs
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun ADPrivacyCenterScreenRefined(onBack: () -> Unit) {
    val context = LocalContext.current
    var storeTranscripts by remember { mutableStateOf(PrivacyPrefs.isTranscriptStorageEnabled(context)) }
    var redactNames by remember { mutableStateOf(PrivacyPrefs.isRedactNamesEnabled(context)) }
    var fullExports by remember { mutableStateOf(PrivacyPrefs.isIncludeFullTranscriptionInExportsEnabled(context)) }
    var confirmations by remember { mutableStateOf(LocalAgentPrefs.isRequireConfirmationEnabled(context)) }

    ADPageLayout("Privacy", onBack) {
        ADCompactInfoHeader(
            glyph = ADGlyph.PRIVACY,
            title = "Your data, your rules",
            detail = "Local by default. Remote only when a configured capability needs it.",
        )

        ADPrivacyControlGroup("Conversation data", "What stays on this phone.") {
            ADPrivacyControlRow(
                icon = Icons.Outlined.Description,
                title = "Save transcripts",
                detail = "Keep supported transcripts locally",
                checked = storeTranscripts,
                onCheckedChange = {
                    storeTranscripts = it
                    PrivacyPrefs.setTranscriptStorageEnabled(context, it)
                },
            )
            HorizontalDivider(color = ADColors.Separator)
            ADPrivacyControlRow(
                glyph = ADGlyph.PRIVACY,
                title = "Redact names",
                detail = "Best-effort redaction in saved text",
                checked = redactNames,
                onCheckedChange = {
                    redactNames = it
                    PrivacyPrefs.setRedactNamesEnabled(context, it)
                },
            )
            HorizontalDivider(color = ADColors.Separator)
            ADPrivacyControlRow(
                icon = Icons.Outlined.Description,
                title = "Full transcript in exports",
                detail = "Include complete transcription in exports",
                checked = fullExports,
                onCheckedChange = {
                    fullExports = it
                    PrivacyPrefs.setIncludeFullTranscriptionInExportsEnabled(context, it)
                },
            )
        }

        ADPrivacyControlGroup("Automation safety", "Protection for actions that affect other apps.") {
            ADPrivacyControlRow(
                icon = Icons.Outlined.Security,
                title = "Confirm sensitive actions",
                detail = "Ask before protected automation runs",
                checked = confirmations,
                onCheckedChange = {
                    confirmations = it
                    LocalAgentPrefs.setRequireConfirmationEnabled(context, it)
                },
            )
        }
    }
}

@Composable
private fun ADCompactInfoHeader(glyph: ADGlyph, title: String, detail: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = ADColors.Surface.copy(alpha = 0.88f),
        border = BorderStroke(1.dp, ADColors.Outline),
    ) {
        Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
            ADGlyphIcon(glyph, ADColors.Ink, Modifier.size(22.dp))
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
            }
        }
    }
}

@Composable
private fun ADPrivacyControlGroup(
    title: String,
    detail: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ADSectionTitle(title)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(13.dp),
            color = ADColors.Surface.copy(alpha = 0.88f),
            border = BorderStroke(1.dp, ADColors.Outline),
        ) {
            Column(Modifier.padding(horizontal = 11.dp, vertical = 2.dp)) { content() }
        }
    }
}

@Composable
private fun ADPrivacyControlRow(
    icon: ImageVector? = null,
    glyph: ADGlyph? = null,
    title: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(30.dp).background(ADColors.SurfaceSubtle, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (glyph != null) {
                ADGlyphIcon(glyph, ADColors.Ink, Modifier.size(16.dp), accent = if (checked) ADColors.Red else null)
            } else if (icon != null) {
                Icon(icon, null, tint = ADColors.Ink, modifier = Modifier.size(15.dp))
            }
        }
        Column(Modifier.padding(start = 9.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.padding(start = 7.dp),
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = ADColors.Red,
                uncheckedThumbColor = ADColors.Muted,
                uncheckedTrackColor = ADColors.SurfaceSubtle,
                uncheckedBorderColor = ADColors.Outline,
            ),
        )
    }
}

@Composable
internal fun ADStorageScreenRefined(onBack: () -> Unit) {
    val context = LocalContext.current
    var cacheBytes by remember { mutableStateOf<Long?>(null) }
    var filesBytes by remember { mutableStateOf<Long?>(null) }
    var synced by remember { mutableStateOf<ADStorageMediaStatsRefined?>(null) }

    LaunchedEffect(Unit) {
        val stats = withContext(Dispatchers.IO) {
            Triple(
                adRefinedFolderBytes(context.cacheDir),
                adRefinedFolderBytes(context.filesDir),
                adRefinedQuerySyncedMedia(context),
            )
        }
        cacheBytes = stats.first
        filesBytes = stats.second
        synced = stats.third
    }

    val totalBytes = if (cacheBytes != null && filesBytes != null) cacheBytes!! + filesBytes!! else null
    val dataFraction = if (totalBytes != null && totalBytes > 0L) {
        ((filesBytes ?: 0L).toDouble() / totalBytes.toDouble()).toFloat().coerceIn(0.05f, 0.95f)
    } else 0.5f
    val cacheFraction = 1f - dataFraction

    ADPageLayout("Storage", onBack) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = ADColors.Surface.copy(alpha = 0.88f),
            border = BorderStroke(1.dp, ADColors.Outline),
        ) {
            Column(Modifier.padding(11.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ADGlyphIcon(ADGlyph.STORAGE, ADColors.Ink, Modifier.size(22.dp))
                    Column(Modifier.padding(start = 9.dp).weight(1f)) {
                        Text("LOCAL STORAGE", style = MaterialTheme.typography.labelSmall, color = ADColors.Muted)
                        Text(totalBytes?.let(::adRefinedFormatBytes) ?: "Reading…", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(9.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().height(6.dp).background(ADColors.SurfaceSubtle, CircleShape),
                ) {
                    Box(Modifier.weight(dataFraction).height(6.dp).background(ADColors.Ink, CircleShape))
                    Box(Modifier.weight(cacheFraction).height(6.dp).background(ADColors.Red.copy(alpha = 0.75f), CircleShape))
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ADStorageLegendDot(ADColors.Ink)
                    Text("App data", style = MaterialTheme.typography.labelSmall, color = ADColors.Muted)
                    Spacer(Modifier.weight(1f))
                    ADStorageLegendDot(ADColors.Red)
                    Text("Cache", style = MaterialTheme.typography.labelSmall, color = ADColors.Muted)
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            ADSectionTitle("Breakdown")
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                ADStorageBreakdownCard("App data", "Files & state", filesBytes?.let(::adRefinedFormatBytes) ?: "…", Modifier.weight(1f))
                ADStorageBreakdownCard("Cache", "Safe to clear", cacheBytes?.let(::adRefinedFormatBytes) ?: "…", Modifier.weight(1f))
            }
        }

        val media = synced
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(13.dp),
            color = ADColors.Surface.copy(alpha = 0.88f),
            border = BorderStroke(1.dp, ADColors.Outline),
        ) {
            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                ADGlyphIcon(ADGlyph.LIBRARY, ADColors.Ink, Modifier.size(20.dp))
                Column(Modifier.padding(start = 9.dp).weight(1f)) {
                    Text("Glasses media", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    Text(
                        when {
                            media == null -> "Reading library…"
                            media.count == 0 -> "No synced media yet"
                            else -> "${media.count} synced items"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = ADColors.Muted,
                    )
                }
                Text(
                    when {
                        media == null -> "…"
                        media.count == 0 -> "0 B"
                        else -> adRefinedFormatBytes(media.bytes)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        ADPrimaryButton(
            text = "Clear temporary cache",
            onClick = {
                runCatching {
                    context.cacheDir.deleteRecursively()
                    context.cacheDir.mkdirs()
                }
                cacheBytes = adRefinedFolderBytes(context.cacheDir)
            },
        )
    }
}

@Composable
private fun ADStorageBreakdownCard(
    label: String,
    detail: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.heightIn(min = 76.dp),
        shape = RoundedCornerShape(12.dp),
        color = ADColors.Surface.copy(alpha = 0.88f),
        border = BorderStroke(1.dp, ADColors.Outline),
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Column {
                Text(label, style = MaterialTheme.typography.labelLarge)
                Text(detail, style = MaterialTheme.typography.labelSmall, color = ADColors.Muted)
            }
        }
    }
}

@Composable
private fun ADStorageLegendDot(color: Color) {
    Box(Modifier.size(5.dp).background(color, CircleShape))
    Spacer(Modifier.size(4.dp))
}

private data class ADStorageMediaStatsRefined(val count: Int, val bytes: Long)

private fun adRefinedQuerySyncedMedia(context: Context): ADStorageMediaStatsRefined = runCatching {
    if (Build.VERSION.SDK_INT < 29) return@runCatching ADStorageMediaStatsRefined(0, 0)
    var count = 0
    var bytes = 0L
    val projection = arrayOf(MediaStore.Files.FileColumns.SIZE)
    context.contentResolver.query(
        MediaStore.Files.getContentUri("external"),
        projection,
        "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
        arrayOf(SyncedMediaFolder.relativePathLikePattern()),
        null,
    )?.use { cursor ->
        val sizeIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns.SIZE)
        while (cursor.moveToNext()) {
            count++
            if (sizeIndex >= 0) bytes += cursor.getLong(sizeIndex).coerceAtLeast(0L)
        }
    }
    ADStorageMediaStatsRefined(count, bytes)
}.getOrDefault(ADStorageMediaStatsRefined(0, 0))

private fun adRefinedFolderBytes(root: File): Long = runCatching {
    root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
}.getOrDefault(0L)

private fun adRefinedFormatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(Locale.US, kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(Locale.US, mb)
    return "%.1f GB".format(Locale.US, mb / 1024.0)
}

package com.fersaiyan.cyanbridge.ui.adglasses

import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.HorizontalDivider
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
        ADScreenIntro(
            eyebrow = "Privacy",
            title = "Your data stays yours",
            detail = "Keep local data intentional and require confirmation before sensitive automation.",
        )

        ADPrivacySummaryCard()

        ADPrivacyControlGroup("Saved text", "Choose what transcript-derived text remains on this phone.") {
            ADPrivacyControlRow(
                glyph = ADMatrixGlyph.DIARY,
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
                glyph = ADMatrixGlyph.PRIVACY,
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
                glyph = ADMatrixGlyph.STORAGE,
                title = "Full transcript in exports",
                detail = "Include complete transcription when exporting",
                checked = fullExports,
                onCheckedChange = {
                    fullExports = it
                    PrivacyPrefs.setIncludeFullTranscriptionInExportsEnabled(context, it)
                },
            )
        }

        ADPrivacyControlGroup("Automation safety", "Protection for actions that affect other apps.") {
            ADPrivacyControlRow(
                glyph = ADMatrixGlyph.AUTOMATION,
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
private fun ADPrivacySummaryCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(17.dp),
        color = ADColors.Surface,
        border = BorderStroke(1.dp, ADColors.Outline),
    ) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(46.dp).background(ADColors.SurfaceSubtle, RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center,
            ) {
                ADMatrixGlyphIcon(ADMatrixGlyph.PRIVACY, ADColors.Ink, Modifier.size(27.dp))
            }
            Column(Modifier.padding(start = 11.dp).weight(1f)) {
                Text("LOCAL BY DEFAULT", style = ADMetaTextStyle, color = ADColors.InkSoft)
                Spacer(Modifier.height(3.dp))
                Text(
                    "Remote processing only when a configured capability needs it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ADColors.Muted,
                )
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
            shape = RoundedCornerShape(14.dp),
            color = ADColors.Surface,
            border = BorderStroke(1.dp, ADColors.Outline),
        ) {
            Column(Modifier.padding(horizontal = 11.dp, vertical = 2.dp)) { content() }
        }
    }
}

@Composable
private fun ADPrivacyControlRow(
    glyph: ADMatrixGlyph,
    title: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(32.dp).background(ADColors.SurfaceSubtle, RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center,
        ) {
            ADMatrixGlyphIcon(
                glyph = glyph,
                tint = ADColors.Ink,
                modifier = Modifier.size(18.dp),
                accent = if (checked) ADColors.Red else null,
            )
        }
        Column(Modifier.padding(start = 9.dp, end = 5.dp).weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = ADColors.Ink,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = ADColors.Muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.padding(start = 5.dp),
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
        ADScreenIntro(
            eyebrow = "On this phone",
            title = "Storage",
            detail = "App data, temporary cache and media copied from your glasses.",
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(17.dp),
            color = ADColors.Surface,
            border = BorderStroke(1.dp, ADColors.Outline),
        ) {
            Column(Modifier.padding(13.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(44.dp).background(ADColors.SurfaceSubtle, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        ADMatrixGlyphIcon(ADMatrixGlyph.STORAGE, ADColors.Ink, Modifier.size(25.dp))
                    }
                    Column(Modifier.padding(start = 10.dp).weight(1f)) {
                        Text("LOCAL STORAGE", style = ADMetaTextStyle, color = ADColors.Muted)
                        Text(
                            totalBytes?.let(::adRefinedFormatBytes) ?: "Reading…",
                            style = MaterialTheme.typography.headlineMedium,
                            color = ADColors.Ink,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().height(7.dp).background(ADColors.SurfaceSubtle, CircleShape),
                ) {
                    Box(Modifier.weight(dataFraction).height(7.dp).background(ADColors.Ink, CircleShape))
                    Box(Modifier.weight(cacheFraction).height(7.dp).background(ADColors.Red.copy(alpha = 0.75f), CircleShape))
                }
                Spacer(Modifier.height(7.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ADStorageLegendDot(ADColors.Ink)
                    Text("App data", style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
                    Spacer(Modifier.weight(1f))
                    ADStorageLegendDot(ADColors.Red)
                    Text("Cache", style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            ADSectionTitle("Breakdown")
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                ADStorageBreakdownCard(
                    glyph = ADMatrixGlyph.STORAGE,
                    label = "App data",
                    detail = "Files & state",
                    value = filesBytes?.let(::adRefinedFormatBytes) ?: "…",
                    modifier = Modifier.weight(1f),
                )
                ADStorageBreakdownCard(
                    glyph = ADMatrixGlyph.CLOSE,
                    label = "Cache",
                    detail = "Safe to clear",
                    value = cacheBytes?.let(::adRefinedFormatBytes) ?: "…",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        val media = synced
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = ADColors.Surface,
            border = BorderStroke(1.dp, ADColors.Outline),
        ) {
            Row(modifier = Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(36.dp).background(ADColors.SurfaceSubtle, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    ADMatrixGlyphIcon(ADMatrixGlyph.LIBRARY, ADColors.Ink, Modifier.size(21.dp))
                }
                Column(Modifier.padding(start = 9.dp).weight(1f)) {
                    Text("Glasses media", style = MaterialTheme.typography.titleMedium, color = ADColors.Ink, fontWeight = FontWeight.Medium)
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
                    color = ADColors.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Surface(
            onClick = {
                runCatching {
                    context.cacheDir.deleteRecursively()
                    context.cacheDir.mkdirs()
                }
                cacheBytes = adRefinedFolderBytes(context.cacheDir)
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp),
            shape = RoundedCornerShape(11.dp),
            color = ADColors.Surface,
            border = BorderStroke(1.dp, ADColors.Outline),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ADMatrixGlyphIcon(ADMatrixGlyph.CLOSE, ADColors.Muted, Modifier.size(17.dp))
                Text(
                    "Clear temporary cache",
                    modifier = Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = ADColors.InkSoft,
                )
            }
        }
    }
}

@Composable
private fun ADStorageBreakdownCard(
    glyph: ADMatrixGlyph,
    label: String,
    detail: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.heightIn(min = 92.dp),
        shape = RoundedCornerShape(13.dp),
        color = ADColors.Surface,
        border = BorderStroke(1.dp, ADColors.Outline),
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ADMatrixGlyphIcon(glyph, ADColors.InkSoft, Modifier.size(17.dp))
                Spacer(Modifier.weight(1f))
                Text(
                    value,
                    style = MaterialTheme.typography.titleMedium,
                    color = ADColors.Ink,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column {
                Text(label, style = MaterialTheme.typography.labelLarge, color = ADColors.Ink)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
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

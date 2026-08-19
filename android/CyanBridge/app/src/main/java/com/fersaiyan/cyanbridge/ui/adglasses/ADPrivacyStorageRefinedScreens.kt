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
        Text(
            "Privacy controls should read like choices, not legal settings. Everything here describes what is saved and when AD Glasses asks before acting.",
            style = MaterialTheme.typography.bodyMedium,
            color = ADColors.Muted,
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(30.dp),
            color = ADColors.Ink,
            contentColor = ADColors.Surface,
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = RoundedCornerShape(19.dp),
                    color = ADColors.Surface.copy(alpha = 0.13f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        ADGlyphIcon(ADGlyph.PRIVACY, ADColors.Surface, Modifier.size(31.dp))
                    }
                }
                Column(Modifier.padding(start = 13.dp).weight(1f)) {
                    Text(
                        "Your data, your rules",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "Saved locally unless a capability you configure needs a remote service.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ADColors.Surface.copy(alpha = 0.68f),
                    )
                }
            }
        }

        ADPrivacyControlGroup(
            title = "Conversation data",
            detail = "What gets kept on this phone.",
        ) {
            ADPrivacyControlRow(
                icon = Icons.Outlined.Description,
                title = "Save transcripts",
                detail = "Keep supported transcripts on this phone",
                checked = storeTranscripts,
                onCheckedChange = {
                    storeTranscripts = it
                    PrivacyPrefs.setTranscriptStorageEnabled(context, it)
                },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ADPrivacyControlRow(
                glyph = ADGlyph.PRIVACY,
                title = "Redact names",
                detail = "Best-effort name redaction in saved text",
                checked = redactNames,
                onCheckedChange = {
                    redactNames = it
                    PrivacyPrefs.setRedactNamesEnabled(context, it)
                },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ADPrivacyControlRow(
                icon = Icons.Outlined.Description,
                title = "Full transcript in exports",
                detail = "Include complete transcription when exporting",
                checked = fullExports,
                onCheckedChange = {
                    fullExports = it
                    PrivacyPrefs.setIncludeFullTranscriptionInExportsEnabled(context, it)
                },
            )
        }

        ADPrivacyControlGroup(
            title = "Automation safety",
            detail = "Protection for actions that can affect other apps or services.",
        ) {
            ADPrivacyControlRow(
                icon = Icons.Outlined.Security,
                title = "Confirm sensitive actions",
                detail = "Ask before protected automation actions run",
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
private fun ADPrivacyControlGroup(
    title: String,
    detail: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        ADSectionTitle(title)
        Text(
            detail,
            style = MaterialTheme.typography.bodySmall,
            color = ADColors.Muted,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = ADColors.Surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) { content() }
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
        modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(42.dp),
            shape = RoundedCornerShape(14.dp),
            color = if (checked) ADColors.Ink else ADColors.SurfaceSubtle,
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (glyph != null) {
                    ADGlyphIcon(
                        glyph,
                        if (checked) ADColors.Surface else ADColors.Ink,
                        Modifier.size(22.dp),
                    )
                } else if (icon != null) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (checked) ADColors.Surface else ADColors.Ink,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        Column(Modifier.padding(start = 11.dp).weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
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
            modifier = Modifier.padding(start = 8.dp),
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
    } else {
        0.5f
    }
    val cacheFraction = 1f - dataFraction

    ADPageLayout("Storage", onBack) {
        Text(
            "See what AD Glasses keeps on this phone, what came from your glasses, and what you can safely clear.",
            style = MaterialTheme.typography.bodyMedium,
            color = ADColors.Muted,
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(30.dp),
            color = ADColors.Ink,
            contentColor = ADColors.Surface,
        ) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(56.dp),
                        shape = RoundedCornerShape(19.dp),
                        color = ADColors.Surface.copy(alpha = 0.13f),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            ADGlyphIcon(ADGlyph.STORAGE, ADColors.Surface, Modifier.size(31.dp))
                        }
                    }
                    Column(Modifier.padding(start = 13.dp).weight(1f)) {
                        Text(
                            totalBytes?.let(::adRefinedFormatBytes) ?: "Reading…",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Used by AD Glasses on this phone",
                            style = MaterialTheme.typography.bodySmall,
                            color = ADColors.Surface.copy(alpha = 0.68f),
                        )
                    }
                }

                Spacer(Modifier.height(17.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(9.dp)
                        .background(ADColors.Surface.copy(alpha = 0.16f), RoundedCornerShape(5.dp)),
                ) {
                    Box(
                        Modifier
                            .weight(dataFraction)
                            .height(9.dp)
                            .background(ADColors.Surface, RoundedCornerShape(topStart = 5.dp, bottomStart = 5.dp)),
                    )
                    Box(
                        Modifier
                            .weight(cacheFraction)
                            .height(9.dp)
                            .background(ADColors.Surface.copy(alpha = 0.30f), RoundedCornerShape(topEnd = 5.dp, bottomEnd = 5.dp)),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ADStorageLegendDot(ADColors.Surface)
                    Text("App data", style = MaterialTheme.typography.labelSmall, color = ADColors.Surface.copy(alpha = 0.68f))
                    Spacer(Modifier.weight(1f))
                    ADStorageLegendDot(ADColors.Surface.copy(alpha = 0.30f))
                    Text("Cache", style = MaterialTheme.typography.labelSmall, color = ADColors.Surface.copy(alpha = 0.68f))
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ADSectionTitle("Breakdown")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ADStorageBreakdownCard(
                    label = "App data",
                    detail = "Files and local state",
                    value = filesBytes?.let(::adRefinedFormatBytes) ?: "…",
                    modifier = Modifier.weight(1f),
                )
                ADStorageBreakdownCard(
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
            shape = RoundedCornerShape(24.dp),
            color = ADColors.Surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(48.dp).background(ADColors.SurfaceSubtle, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    ADGlyphIcon(ADGlyph.LIBRARY, ADColors.Ink, Modifier.size(27.dp))
                }
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text("Glasses media", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    Text(
                        when {
                            media == null -> "Reading Library…"
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
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
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
        modifier = modifier.heightIn(min = 116.dp),
        shape = RoundedCornerShape(22.dp),
        color = ADColors.Surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Column {
                Text(label, style = MaterialTheme.typography.labelLarge)
                Text(detail, style = MaterialTheme.typography.labelSmall, color = ADColors.Muted)
            }
        }
    }
}

@Composable
private fun ADStorageLegendDot(color: Color) {
    Box(Modifier.size(6.dp).background(color, CircleShape))
    Spacer(Modifier.size(5.dp))
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

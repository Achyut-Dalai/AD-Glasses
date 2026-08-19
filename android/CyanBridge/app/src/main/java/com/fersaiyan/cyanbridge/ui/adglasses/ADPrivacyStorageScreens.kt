package com.fersaiyan.cyanbridge.ui.adglasses

import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
internal fun ADPrivacyCenterScreenV2(onBack: () -> Unit) {
    val context = LocalContext.current
    var storeTranscripts by remember { mutableStateOf(PrivacyPrefs.isTranscriptStorageEnabled(context)) }
    var redactNames by remember { mutableStateOf(PrivacyPrefs.isRedactNamesEnabled(context)) }
    var fullExports by remember { mutableStateOf(PrivacyPrefs.isIncludeFullTranscriptionInExportsEnabled(context)) }
    var confirmations by remember { mutableStateOf(LocalAgentPrefs.isRequireConfirmationEnabled(context)) }

    ADPageLayout("Privacy", onBack) {
        ADSectionTitle("Controls")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ADPrivacyToggleTile(
                icon = Icons.Outlined.Description,
                title = "Save transcripts",
                checked = storeTranscripts,
                emphasized = true,
                modifier = Modifier.weight(1.08f),
                onChecked = {
                    storeTranscripts = it
                    PrivacyPrefs.setTranscriptStorageEnabled(context, it)
                },
            )
            ADPrivacyToggleTile(
                icon = Icons.Outlined.Lock,
                title = "Redact names",
                checked = redactNames,
                modifier = Modifier.weight(0.92f),
                onChecked = {
                    redactNames = it
                    PrivacyPrefs.setRedactNamesEnabled(context, it)
                },
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ADPrivacyToggleTile(
                icon = Icons.Outlined.Description,
                title = "Full exports",
                checked = fullExports,
                modifier = Modifier.weight(0.92f),
                onChecked = {
                    fullExports = it
                    PrivacyPrefs.setIncludeFullTranscriptionInExportsEnabled(context, it)
                },
            )
            ADPrivacyToggleTile(
                icon = Icons.Outlined.Security,
                title = "Confirm actions",
                checked = confirmations,
                emphasized = true,
                modifier = Modifier.weight(1.08f),
                onChecked = {
                    confirmations = it
                    LocalAgentPrefs.setRequireConfirmationEnabled(context, it)
                },
            )
        }
    }
}

@Composable
private fun ADPrivacyToggleTile(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    onChecked: (Boolean) -> Unit,
) {
    Surface(
        onClick = { onChecked(!checked) },
        modifier = modifier.heightIn(min = 112.dp),
        shape = RoundedCornerShape(21.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(11.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(38.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = if (emphasized) ADColors.Ink else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (emphasized) ADColors.Surface else ADColors.Ink,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(19.dp))
                    }
                }
                Spacer(Modifier.weight(1f))
                Switch(checked = checked, onCheckedChange = onChecked)
            }
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun ADStorageScreenV2(onBack: () -> Unit) {
    val context = LocalContext.current
    var cacheBytes by remember { mutableStateOf<Long?>(null) }
    var filesBytes by remember { mutableStateOf<Long?>(null) }
    var synced by remember { mutableStateOf<ADStorageSyncedStats?>(null) }

    LaunchedEffect(Unit) {
        val stats = withContext(Dispatchers.IO) {
            Triple(
                adFolderBytes(context.cacheDir),
                adFolderBytes(context.filesDir),
                adQuerySyncedMedia(context),
            )
        }
        cacheBytes = stats.first
        filesBytes = stats.second
        synced = stats.third
    }

    val totalBytes = if (cacheBytes != null && filesBytes != null) cacheBytes!! + filesBytes!! else null
    val media = synced

    ADPageLayout("Storage", onBack) {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(min = 118.dp),
            shape = RoundedCornerShape(24.dp),
            color = ADColors.Ink,
            contentColor = ADColors.Surface,
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = RoundedCornerShape(17.dp),
                    color = ADColors.Surface.copy(alpha = 0.13f),
                    contentColor = ADColors.Surface,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Storage, contentDescription = null, modifier = Modifier.size(25.dp))
                    }
                }
                Column(Modifier.padding(start = 13.dp).weight(1f)) {
                    Text(
                        totalBytes?.let(::adFormatBytes) ?: "Reading…",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "App footprint on this phone",
                        style = MaterialTheme.typography.bodySmall,
                        color = ADColors.Surface.copy(alpha = 0.68f),
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ADStorageMiniTile(
                label = "App data",
                value = filesBytes?.let(::adFormatBytes) ?: "…",
                modifier = Modifier.weight(1.08f),
                emphasized = true,
            )
            ADStorageMiniTile(
                label = "Cache",
                value = cacheBytes?.let(::adFormatBytes) ?: "…",
                modifier = Modifier.weight(0.92f),
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.CameraAlt, contentDescription = null, tint = ADColors.Ink, modifier = Modifier.size(19.dp))
                    }
                }
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Text("Glasses media", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    Text(
                        when {
                            media == null -> "Reading Library…"
                            media.count == 0 -> "No synced media"
                            else -> "${media.count} items"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = ADColors.Muted,
                    )
                }
                Text(
                    when {
                        media == null -> "…"
                        media.count == 0 -> "0 B"
                        else -> adFormatBytes(media.bytes)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        OutlinedButton(
            onClick = {
                runCatching {
                    context.cacheDir.deleteRecursively()
                    context.cacheDir.mkdirs()
                }
                cacheBytes = adFolderBytes(context.cacheDir)
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Clear app cache") }
    }
}

@Composable
private fun ADStorageMiniTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    Surface(
        modifier = modifier.heightIn(min = 96.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(11.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Surface(
                modifier = Modifier.size(32.dp),
                shape = RoundedCornerShape(10.dp),
                color = if (emphasized) ADColors.Ink else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (emphasized) ADColors.Surface else ADColors.Ink,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Storage, contentDescription = null, modifier = Modifier.size(17.dp))
                }
            }
            Column {
                Text(
                    value,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(label, style = MaterialTheme.typography.labelSmall, color = ADColors.Muted)
            }
        }
    }
}

private data class ADStorageSyncedStats(val count: Int, val bytes: Long)

private fun adQuerySyncedMedia(context: Context): ADStorageSyncedStats = runCatching {
    if (Build.VERSION.SDK_INT < 29) return@runCatching ADStorageSyncedStats(0, 0)
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
    ADStorageSyncedStats(count, bytes)
}.getOrDefault(ADStorageSyncedStats(0, 0))

private fun adFolderBytes(root: File): Long = runCatching {
    root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
}.getOrDefault(0L)

private fun adFormatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(Locale.US, kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(Locale.US, mb)
    return "%.1f GB".format(Locale.US, mb / 1024.0)
}

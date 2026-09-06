package com.adglasses.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adglasses.app.core.media.LocalMediaKind

@Composable
fun LibraryScreen(padding: PaddingValues, vm: ADViewModel, openDeviceCenter: () -> Unit) {
    val connection by vm.glasses.collectAsStateWithLifecycle()
    val media by vm.mediaItems.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Library", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Original media stays original. Analysis, OCR and thumbnails are derivatives.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.PhotoLibrary, null)
                    Column(Modifier.weight(1f)) {
                        Text("Glasses media", fontWeight = FontWeight.SemiBold)
                        Text(
                            if (connection.isReady) {
                                "One tap prepares BLE, joins the glasses Wi-Fi, syncs new originals and cleans up."
                            } else {
                                "Connect glasses to sync"
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                if (connection.isReady) {
                    Button(onClick = vm::syncMediaConfig, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.CloudDownload, null)
                        Text("  Sync Library")
                    }
                } else {
                    OutlinedButton(onClick = openDeviceCenter, modifier = Modifier.fillMaxWidth()) {
                        Text("Connect glasses")
                    }
                }
            }
        }

        if (media.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("No synced media yet", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Photos, videos and glasses recordings will appear here after sync.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            Text("On this phone • ${media.size}", fontWeight = FontWeight.SemiBold)
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(media, key = { it.fileName }) { item ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 13.dp)) {
                            Text(item.fileName, fontWeight = FontWeight.Medium)
                            Text(
                                "${kindLabel(item.kind)} • ${formatBytes(item.bytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                item { HorizontalDivider(Modifier.padding(top = 4.dp)) }
            }
        }
    }
}

private fun kindLabel(kind: LocalMediaKind): String = when (kind) {
    LocalMediaKind.Photo -> "Photo"
    LocalMediaKind.Video -> "Video"
    LocalMediaKind.Audio -> "Audio"
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576L -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1_024L -> "%.1f KB".format(bytes / 1_024.0)
    else -> "$bytes B"
}
package com.adglasses.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adglasses.app.core.media.LocalMediaKind

@Composable
fun LibraryScreen(padding: PaddingValues, vm: ADViewModel, openDeviceCenter: () -> Unit) {
    val connection by vm.glasses.collectAsStateWithLifecycle()
    val media by vm.mediaItems.collectAsStateWithLifecycle()

    ADAmbientBackground(Modifier.fillMaxSize().padding(padding)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(
                        modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            "Library",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Your originals stay untouched. AD keeps synced photos, videos and recordings together on this phone.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    ADGroupedCard(
                        modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
                        cornerRadius = 20.dp,
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                LibraryIcon(Icons.Outlined.PhotoLibrary, ADAccent.Indigo)
                                Column(Modifier.weight(1f)) {
                                    Text("Glasses media", fontWeight = FontWeight.SemiBold)
                                    Text(
                                        if (connection.isReady) {
                                            "Ready to sync new originals from your glasses."
                                        } else {
                                            "Connect your glasses to sync media."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            if (connection.isReady) {
                                Button(
                                    onClick = vm::syncMediaConfig,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(Icons.Outlined.CloudDownload, null)
                                    Spacer(Modifier.size(8.dp))
                                    Text("Sync new media")
                                }
                            } else {
                                OutlinedButton(
                                    onClick = openDeviceCenter,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("Connect glasses")
                                }
                            }
                        }
                    }
                }
            }

            if (media.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Surface(
                            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                LibraryIcon(Icons.Outlined.PhotoLibrary, MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("No synced media yet", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Photos, videos and glasses recordings will appear here after your first sync.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            } else {
                item {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                        Text(
                            "ON THIS PHONE · ${media.size}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                items(media, key = { it.fileName }) { item ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Surface(
                            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                LibraryIcon(kindIcon(item.kind), kindTint(item.kind))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        item.fileName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 2,
                                    )
                                    Text(
                                        "${kindLabel(item.kind)} · ${formatBytes(item.bytes)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
private fun LibraryIcon(icon: ImageVector, tint: Color) {
    Surface(
        modifier = Modifier.size(42.dp),
        shape = RoundedCornerShape(13.dp),
        color = tint.copy(alpha = 0.10f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        }
    }
}

private fun kindIcon(kind: LocalMediaKind): ImageVector = when (kind) {
    LocalMediaKind.Photo -> Icons.Outlined.Image
    LocalMediaKind.Video -> Icons.Outlined.Movie
    LocalMediaKind.Audio -> Icons.Outlined.GraphicEq
}

private fun kindTint(kind: LocalMediaKind): Color = when (kind) {
    LocalMediaKind.Photo -> ADAccent.Teal
    LocalMediaKind.Video -> ADAccent.Pink
    LocalMediaKind.Audio -> ADAccent.Orange
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

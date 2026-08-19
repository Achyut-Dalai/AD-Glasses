package com.fersaiyan.cyanbridge.ui.adglasses

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Library landing surface; detail viewers remain unchanged. */
@Composable
internal fun ADExpressiveLibraryHome(
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
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 6.dp, 16.dp, 30.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                ADLibraryHero(
                    title = "Everything the glasses keep",
                    detail = "Captures, recordings, transcripts and notes live here after they reach this phone.",
                    transferActive = transferActive,
                    onOpenSync = onOpenSync,
                )
            }

            if (transferActive) {
                item {
                    ADCard(onClick = onOpenSync) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(ADColors.SurfaceSubtle, RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Outlined.Sync, contentDescription = null, tint = ADColors.Ink)
                            }
                            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                Text("New media is arriving", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Open Sync for live transfer progress",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ADColors.Muted,
                                )
                            }
                            ADStatusChip("ACTIVE", ADStatusTone.INFO)
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    ADLibraryDestinationRow(
                        icon = Icons.Outlined.Image,
                        title = "Captures",
                        detail = "Photos and videos from your glasses",
                        onClick = onCaptures,
                    )
                    ADLibraryDestinationRow(
                        icon = Icons.Outlined.GraphicEq,
                        title = "Recordings",
                        detail = "Audio sessions and transcripts",
                        onClick = onRecordings,
                    )
                    ADLibraryDestinationRow(
                        icon = Icons.Outlined.Notes,
                        title = "Notes",
                        detail = "Notes and summaries you kept",
                        onClick = onNotes,
                    )
                }
            }
        }
    }
}

@Composable
private fun ADLibraryHero(
    title: String,
    detail: String,
    transferActive: Boolean,
    onOpenSync: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = ADColors.Surface,
        shadowElevation = 1.dp,
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = RoundedCornerShape(17.dp),
                    color = ADColors.Ink,
                    contentColor = ADColors.Surface,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.VideoLibrary, contentDescription = null, modifier = Modifier.size(25.dp))
                    }
                }
                Column(Modifier.padding(start = 14.dp).weight(1f)) {
                    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(detail, style = MaterialTheme.typography.bodyMedium, color = ADColors.Muted)
                }
                ADStatusChip(
                    if (transferActive) "SYNCING" else "LOCAL",
                    if (transferActive) ADStatusTone.INFO else ADStatusTone.NEUTRAL,
                )
            }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = ADColors.Separator)
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onOpenSync,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ADColors.Ink),
            ) {
                Icon(Icons.Outlined.Sync, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(if (transferActive) "View sync" else "Sync from glasses")
            }
        }
    }
}

@Composable
private fun ADLibraryDestinationRow(
    icon: ImageVector,
    title: String,
    detail: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 82.dp),
        shape = RoundedCornerShape(20.dp),
        color = ADColors.Surface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(ADColors.SurfaceSubtle, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = ADColors.Ink, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.padding(start = 13.dp).weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = ADColors.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = ADColors.Muted,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

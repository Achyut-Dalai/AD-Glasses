package com.fersaiyan.cyanbridge.ui.adglasses

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
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
            contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp, 2.dp, 14.dp, 18.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
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
                                    .size(36.dp)
                                    .background(ADColors.SurfaceSubtle, RoundedCornerShape(11.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Outlined.Sync,
                                    contentDescription = null,
                                    tint = ADColors.Ink,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Column(Modifier.padding(start = 9.dp).weight(1f)) {
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
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(
                        "Browse",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 1.dp),
                    )
                    ADLibraryPrimaryDestination(
                        icon = Icons.Outlined.Image,
                        title = "Captures",
                        detail = "Photos and videos from your glasses",
                        onClick = onCaptures,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        ADLibraryCompactDestination(
                            icon = Icons.Outlined.GraphicEq,
                            title = "Recordings",
                            detail = "Audio and transcripts",
                            modifier = Modifier.weight(1f),
                            onClick = onRecordings,
                        )
                        ADLibraryCompactDestination(
                            icon = Icons.Outlined.Notes,
                            title = "Notes",
                            detail = "Saved notes and summaries",
                            modifier = Modifier.weight(1f),
                            onClick = onNotes,
                        )
                    }
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
        shape = RoundedCornerShape(21.dp),
        color = ADColors.Surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 1.dp,
    ) {
        Column(Modifier.padding(13.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = ADColors.Ink,
                    contentColor = ADColors.Surface,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.VideoLibrary, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                }
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(2.dp))
                    Text(detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
                }
                ADStatusChip(
                    if (transferActive) "SYNCING" else "LOCAL",
                    if (transferActive) ADStatusTone.INFO else ADStatusTone.NEUTRAL,
                )
            }
            Spacer(Modifier.height(9.dp))
            HorizontalDivider(color = ADColors.Separator)
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onOpenSync,
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ADColors.Ink),
            ) {
                Icon(Icons.Outlined.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(7.dp))
                Text(if (transferActive) "View sync" else "Sync from glasses")
            }
        }
    }
}

@Composable
private fun ADLibraryPrimaryDestination(
    icon: ImageVector,
    title: String,
    detail: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 102.dp),
        shape = RoundedCornerShape(23.dp),
        color = ADColors.Surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = RoundedCornerShape(18.dp),
                color = ADColors.Ink,
                contentColor = ADColors.Surface,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
                }
            }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
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
            Surface(shape = CircleShape, color = ADColors.SurfaceSubtle) {
                Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = ADColors.Ink,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ADLibraryCompactDestination(
    icon: ImageVector,
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 126.dp),
        shape = RoundedCornerShape(18.dp),
        color = ADColors.Surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(11.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(ADColors.SurfaceSubtle, RoundedCornerShape(13.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = ADColors.Ink, modifier = Modifier.size(19.dp))
                }
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = ADColors.Muted,
                    modifier = Modifier.size(18.dp),
                )
            }

            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
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
        }
    }
}

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
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.rounded.ChevronRight
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

@Composable
internal fun ADExpressiveLibraryHome(
    transferActive: Boolean,
    onOpenSync: () -> Unit,
    onCaptures: () -> Unit,
    onRecordings: () -> Unit,
    onNotes: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = 10.dp,
            bottom = 18.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ADScreenIntro(
                eyebrow = "Library",
                title = "Your memory",
                detail = "What your glasses captured, recorded and turned into something useful.",
            )
        }

        item {
            ADLibrarySyncCard(transferActive = transferActive, onClick = onOpenSync)
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ADSectionTitle("Collections")
                ADLibraryFeatureCollection(
                    title = "Captures",
                    detail = "Photos and video from your glasses",
                    onClick = onCaptures,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ADLibraryCollection(
                        icon = Icons.Outlined.Mic,
                        title = "Recordings",
                        detail = "Audio + transcripts",
                        modifier = Modifier.weight(1f),
                        onClick = onRecordings,
                    )
                    ADLibraryCollection(
                        icon = Icons.Outlined.Description,
                        title = "Notes",
                        detail = "Summaries + ideas",
                        modifier = Modifier.weight(1f),
                        onClick = onNotes,
                    )
                }
            }
        }
    }
}

@Composable
private fun ADLibrarySyncCard(transferActive: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        color = ADColors.Surface,
        contentColor = ADColors.Ink,
        border = BorderStroke(1.dp, ADColors.Outline),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(36.dp).background(ADColors.SurfaceSubtle, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Sync,
                    contentDescription = null,
                    tint = ADColors.Ink,
                    modifier = Modifier.size(21.dp),
                )
            }
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text(
                    if (transferActive) "Syncing from glasses" else "Sync from glasses",
                    style = MaterialTheme.typography.titleMedium,
                    color = ADColors.Ink,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (transferActive) "New media is being copied into your library" else "Bring the latest captures onto this phone",
                    style = MaterialTheme.typography.bodySmall,
                    color = ADColors.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (transferActive) {
                Box(Modifier.size(6.dp).background(ADColors.Red, CircleShape))
            } else {
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = ADColors.Muted, modifier = Modifier.size(17.dp))
            }
        }
    }
}

@Composable
private fun ADLibraryFeatureCollection(
    title: String,
    detail: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 126.dp),
        shape = RoundedCornerShape(17.dp),
        color = ADColors.Surface,
        contentColor = ADColors.Ink,
        border = BorderStroke(1.dp, ADColors.Outline),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ADLibraryPreviewStage(modifier = Modifier.weight(.92f).height(98.dp))
            Column(Modifier.padding(start = 12.dp).weight(1.08f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = ADColors.Ink)
                Spacer(Modifier.height(5.dp))
                Text(detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Open collection", style = MaterialTheme.typography.labelMedium, color = ADColors.InkSoft)
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = ADColors.Muted, modifier = Modifier.size(17.dp))
                }
            }
        }
    }
}

@Composable
private fun ADLibraryCollection(
    icon: ImageVector,
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 122.dp),
        shape = RoundedCornerShape(15.dp),
        color = ADColors.Surface,
        contentColor = ADColors.Ink,
        border = BorderStroke(1.dp, ADColors.Outline),
    ) {
        Column(
            modifier = Modifier.padding(11.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier.size(38.dp).background(ADColors.SurfaceSubtle, RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = ADColors.Ink, modifier = Modifier.size(22.dp))
            }
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, color = ADColors.Ink, fontWeight = FontWeight.SemiBold)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ADLibraryPreviewStage(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(13.dp),
        color = ADColors.SurfaceSubtle,
        border = BorderStroke(1.dp, ADColors.Separator),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = ADColors.Surface,
                    border = BorderStroke(1.dp, ADColors.Outline),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, tint = ADColors.Ink, modifier = Modifier.size(24.dp))
                    }
                }
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = ADColors.Surface,
                    border = BorderStroke(1.dp, ADColors.Outline),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Videocam, contentDescription = null, tint = ADColors.Ink, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}

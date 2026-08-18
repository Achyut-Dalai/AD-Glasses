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
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Restored Library landing design from the earlier UI pass; detail viewers remain unchanged. */
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
            verticalArrangement = Arrangement.spacedBy(18.dp),
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
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "ON THIS PHONE",
                        style = MaterialTheme.typography.labelSmall,
                        color = ADColors.Muted,
                        letterSpacing = 1.15.sp,
                        modifier = Modifier.padding(start = 2.dp),
                    )
                    ADLibraryDestinationCard(
                        icon = Icons.Outlined.Image,
                        title = "Captures",
                        detail = "Photos and videos synced from the glasses.",
                        meta = "VISUAL",
                        onClick = onCaptures,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ADLibraryDestinationCard(
                            icon = Icons.Outlined.GraphicEq,
                            title = "Recordings",
                            detail = "Audio sessions and transcripts.",
                            meta = "AUDIO",
                            modifier = Modifier.weight(1f),
                            compact = true,
                            onClick = onRecordings,
                        )
                        ADLibraryDestinationCard(
                            icon = Icons.Outlined.Notes,
                            title = "Notes",
                            detail = "Summaries worth keeping.",
                            meta = "TEXT",
                            modifier = Modifier.weight(1f),
                            compact = true,
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
        shape = RoundedCornerShape(26.dp),
        color = ADColors.Surface,
    ) {
        Column(Modifier.padding(19.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .background(ADColors.Ink, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.VideoLibrary,
                        contentDescription = null,
                        tint = ADColors.Surface,
                        modifier = Modifier.size(25.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    if (transferActive) "SYNCING" else "LOCAL",
                    style = MaterialTheme.typography.labelSmall,
                    color = ADColors.Muted,
                    letterSpacing = 1.05.sp,
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = ADColors.Muted)
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onOpenSync,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ADColors.Ink),
                shape = RoundedCornerShape(15.dp),
            ) {
                Icon(Icons.Outlined.Sync, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(if (transferActive) "View sync" else "Sync from glasses")
            }
        }
    }
}

@Composable
private fun ADLibraryDestinationCard(
    icon: ImageVector,
    title: String,
    detail: String,
    meta: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = if (compact) 154.dp else 132.dp),
        shape = RoundedCornerShape(22.dp),
        color = ADColors.Surface,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(if (compact) 42.dp else 46.dp)
                        .background(ADColors.SurfaceSubtle, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = ADColors.Ink, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.weight(1f))
                Text(meta, style = MaterialTheme.typography.labelSmall, color = ADColors.Muted)
            }
            Spacer(Modifier.height(if (compact) 18.dp else 14.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(3.dp))
            Text(detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
        }
    }
}

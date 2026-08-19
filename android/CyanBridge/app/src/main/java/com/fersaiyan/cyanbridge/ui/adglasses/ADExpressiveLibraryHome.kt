package com.fersaiyan.cyanbridge.ui.adglasses

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Library landing surface; collections should read as content, not settings destinations. */
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
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 4.dp, 16.dp, 30.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                ADLibraryHero(transferActive = transferActive, onOpenSync = onOpenSync)
            }

            if (transferActive) {
                item {
                    ADCard(onClick = onOpenSync) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(40.dp).background(ADColors.CyanSoft, RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Outlined.Sync, contentDescription = null, tint = ADColors.CyanDeep)
                            }
                            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                Text("New media is arriving", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Open Sync for live transfer progress",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ADColors.Muted,
                                )
                            }
                            ADStatusChip("LIVE", ADStatusTone.INFO)
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    ADSectionTitle("Collections")
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        ADLibraryCollectionCard(
                            icon = Icons.Outlined.Image,
                            title = "Captures",
                            detail = "Photos & video",
                            modifier = Modifier.weight(1f),
                            onClick = onCaptures,
                        )
                        ADLibraryCollectionCard(
                            icon = Icons.Outlined.GraphicEq,
                            title = "Recordings",
                            detail = "Audio & text",
                            modifier = Modifier.weight(1f),
                            onClick = onRecordings,
                        )
                    }
                    ADLibraryNotesCard(onClick = onNotes)
                }
            }
        }
    }
}

@Composable
private fun ADLibraryHero(
    transferActive: Boolean,
    onOpenSync: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ADColors.Graphite, shape)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(Color.White.copy(alpha = 0.10f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.VideoLibrary,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(23.dp),
                )
            }
            Column(Modifier.padding(start = 13.dp).weight(1f)) {
                Text(
                    "Your glasses, kept here",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    "Captures, recordings and notes stay organized on this phone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.68f),
                )
            }
            ADStatusChip(
                if (transferActive) "SYNCING" else "LOCAL",
                if (transferActive) ADStatusTone.INFO else ADStatusTone.NEUTRAL,
            )
        }
        Spacer(Modifier.height(15.dp))
        HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
        Spacer(Modifier.height(13.dp))
        Button(
            onClick = onOpenSync,
            modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (transferActive) ADColors.Cyan else Color.White,
                contentColor = if (transferActive) Color.White else ADColors.Ink,
            ),
        ) {
            Icon(Icons.Outlined.Sync, contentDescription = null, modifier = Modifier.size(19.dp))
            Spacer(Modifier.size(7.dp))
            Text(if (transferActive) "View sync" else "Sync from glasses")
        }
    }
}

@Composable
private fun ADLibraryCollectionCard(
    icon: ImageVector,
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .heightIn(min = 126.dp)
            .background(ADColors.Surface, shape)
            .border(1.dp, ADColors.Outline, shape)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            modifier = Modifier.size(42.dp).background(ADColors.CyanSoft, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = ADColors.CyanDeep, modifier = Modifier.size(22.dp))
        }
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = ADColors.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ADLibraryNotesCard(onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 78.dp)
            .background(ADColors.Surface, shape)
            .border(1.dp, ADColors.Outline, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(42.dp).background(ADColors.SurfaceSubtle, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Notes, contentDescription = null, tint = ADColors.Ink, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text("Notes", style = MaterialTheme.typography.titleMedium)
            Text("Summaries and saved ideas", style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
        }
        Box(
            modifier = Modifier.size(34.dp).background(ADColors.SurfaceSubtle, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = ADColors.Ink,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

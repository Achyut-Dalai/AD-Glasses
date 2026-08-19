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
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 16.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            ADScreenIntro(
                eyebrow = "Library",
                title = "Everything your glasses kept",
                detail = "Captures, recordings and notes live together here. Sync only when you want the latest from your glasses.",
            )
        }

        item {
            Surface(
                onClick = onOpenSync,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                color = ADColors.Ink,
                contentColor = ADColors.Surface,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = ADColors.Surface.copy(alpha = 0.13f),
                        contentColor = ADColors.Surface,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Sync, contentDescription = null, modifier = Modifier.size(22.dp))
                        }
                    }
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(
                            if (transferActive) "Sync in progress" else "Sync from glasses",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            if (transferActive) "Open transfer details" else "Bring new photos, videos and audio onto this phone",
                            style = MaterialTheme.typography.bodySmall,
                            color = ADColors.Surface.copy(alpha = 0.68f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Surface(shape = CircleShape, color = ADColors.Surface.copy(alpha = 0.13f)) {
                        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                ADSectionTitle("Browse")
                ADLibraryPrimaryDestination(
                    icon = Icons.Outlined.Image,
                    title = "Captures",
                    detail = "Photos and videos from your glasses",
                    onClick = onCaptures,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
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

@Composable
private fun ADLibraryPrimaryDestination(
    icon: ImageVector,
    title: String,
    detail: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 148.dp),
        shape = RoundedCornerShape(28.dp),
        color = ADColors.Surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 118.dp, height = 116.dp)
                    .background(ADColors.SurfaceSubtle, RoundedCornerShape(22.dp)),
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 62.dp, height = 76.dp)
                        .align(Alignment.CenterStart)
                        .padding(start = 10.dp)
                        .background(ADColors.Ink, RoundedCornerShape(17.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = ADColors.Surface, modifier = Modifier.size(25.dp))
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .align(Alignment.TopEnd)
                        .padding(top = 10.dp, end = 8.dp)
                        .background(ADColors.Surface, RoundedCornerShape(15.dp)),
                )
                Box(
                    modifier = Modifier
                        .size(width = 52.dp, height = 38.dp)
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 10.dp, end = 8.dp)
                        .background(ADColors.Surface, RoundedCornerShape(13.dp)),
                )
            }

            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = ADColors.Muted,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Open library", style = MaterialTheme.typography.labelMedium, color = ADColors.Ink)
                    Spacer(Modifier.size(4.dp))
                    Icon(
                        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = ADColors.Ink,
                        modifier = Modifier.size(17.dp),
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
        modifier = modifier.heightIn(min = 150.dp),
        shape = RoundedCornerShape(23.dp),
        color = ADColors.Surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(13.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(ADColors.SurfaceSubtle, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = ADColors.Ink, modifier = Modifier.size(21.dp))
                }
                Spacer(Modifier.weight(1f))
                Surface(shape = CircleShape, color = ADColors.SurfaceSubtle) {
                    Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = ADColors.Muted,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                }
            }

            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Spacer(Modifier.height(3.dp))
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

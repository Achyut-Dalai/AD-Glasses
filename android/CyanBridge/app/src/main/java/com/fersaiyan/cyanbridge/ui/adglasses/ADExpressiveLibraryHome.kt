package com.fersaiyan.cyanbridge.ui.adglasses

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        item {
            ADScreenIntro(
                eyebrow = "LIBRARY",
                title = "Your captures",
                detail = "Photos, recordings and notes from your glasses.",
            )
        }

        item {
            Surface(
                onClick = onOpenSync,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = ADColors.Surface.copy(alpha = 0.90f),
                border = BorderStroke(1.dp, ADColors.Outline),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ADGlyphIcon(
                        glyph = ADGlyph.SYNC,
                        tint = ADColors.Ink,
                        modifier = Modifier.size(20.dp),
                        accent = if (transferActive) ADColors.Red else null,
                    )
                    Column(Modifier.padding(start = 9.dp).weight(1f)) {
                        Text(
                            if (transferActive) "Sync in progress" else "Sync from glasses",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            if (transferActive) "Open transfer details" else "Bring new media onto this phone",
                            style = MaterialTheme.typography.bodySmall,
                            color = ADColors.Muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (transferActive) {
                        Box(Modifier.size(6.dp), contentAlignment = Alignment.Center) {
                            Surface(modifier = Modifier.size(6.dp), shape = CircleShape, color = ADColors.Red) {}
                        }
                    } else {
                        ADGlyphIcon(ADGlyph.NEXT, ADColors.Muted, Modifier.size(16.dp))
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                ADSectionTitle("Browse")
                ADLibraryPrimaryDestination(
                    glyph = ADGlyph.LIBRARY,
                    title = "Captures",
                    detail = "Photos and videos",
                    onClick = onCaptures,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    ADLibraryCompactDestination(
                        glyph = ADGlyph.AUDIO,
                        title = "Recordings",
                        detail = "Audio & transcript",
                        modifier = Modifier.weight(1f),
                        onClick = onRecordings,
                    )
                    ADLibraryCompactDestination(
                        glyph = ADGlyph.PROMPT,
                        title = "Notes",
                        detail = "Saved summaries",
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
    glyph: ADGlyph,
    title: String,
    detail: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 84.dp),
        shape = RoundedCornerShape(14.dp),
        color = ADColors.Surface.copy(alpha = 0.88f),
        border = BorderStroke(1.dp, ADColors.Outline),
    ) {
        Row(
            modifier = Modifier.padding(11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(42.dp),
                contentAlignment = Alignment.Center,
            ) {
                ADGlyphIcon(glyph, ADColors.Ink, Modifier.size(28.dp))
            }
            Column(Modifier.padding(start = 9.dp).weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
            }
            ADGlyphIcon(ADGlyph.NEXT, ADColors.Muted, Modifier.size(16.dp))
        }
    }
}

@Composable
private fun ADLibraryCompactDestination(
    glyph: ADGlyph,
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 96.dp),
        shape = RoundedCornerShape(13.dp),
        color = ADColors.Surface.copy(alpha = 0.88f),
        border = BorderStroke(1.dp, ADColors.Outline),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            ADGlyphIcon(
                glyph = glyph,
                tint = ADColors.Ink,
                modifier = Modifier.size(21.dp),
                accent = if (glyph == ADGlyph.AUDIO) ADColors.Red else null,
            )
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted, maxLines = 1)
            }
        }
    }
}

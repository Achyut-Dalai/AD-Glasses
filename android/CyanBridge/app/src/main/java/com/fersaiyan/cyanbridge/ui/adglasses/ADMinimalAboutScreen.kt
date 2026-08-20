package com.fersaiyan.cyanbridge.ui.adglasses

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fersaiyan.cyanbridge.BuildConfig

@Composable
internal fun ADMinimalAboutScreen(onBack: () -> Unit) {
    ADPageLayout("About", onBack) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(17.dp),
            color = Color.Black.copy(alpha = 0.40f),
            border = BorderStroke(1.dp, ADColors.Outline),
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ADGlassesMark(Modifier.size(48.dp))
                    Column(Modifier.padding(start = 10.dp).weight(1f)) {
                        Text("AD GLASSES", style = MaterialTheme.typography.labelSmall, color = ADColors.Muted)
                        Text("AI eyewear companion", style = MaterialTheme.typography.titleLarge)
                    }
                    Box(Modifier.size(5.dp), contentAlignment = Alignment.Center) {
                        Surface(modifier = Modifier.size(5.dp), shape = CircleShape, color = ADColors.Red) {}
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text("VERSION", style = MaterialTheme.typography.labelSmall, color = ADColors.Muted)
                        Text(BuildConfig.VERSION_NAME, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Text("DARK / MONO", style = MaterialTheme.typography.labelSmall, color = ADColors.Muted)
                }
            }
        }

        Text(
            "The glasses are the interface. The phone stays the quiet engine behind them.",
            style = MaterialTheme.typography.bodyMedium,
            color = ADColors.Muted,
        )

        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            ADSectionTitle("Principles")
            ADAboutPrinciple(
                glyph = ADGlyph.ASK,
                title = "Voice first",
                detail = "Ask, capture and control without living on the phone.",
            )
            ADAboutPrinciple(
                glyph = ADGlyph.LENS,
                title = "Vision when useful",
                detail = "Bring the camera in only when seeing adds context.",
            )
            ADAboutPrinciple(
                glyph = ADGlyph.AI,
                title = "AI with boundaries",
                detail = "Local where possible, relay only when it earns the trip.",
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = ADColors.Surface.copy(alpha = 0.82f),
            border = BorderStroke(1.dp, ADColors.Outline),
        ) {
            Text(
                "AD Glasses includes open-source components and device SDK integrations. Required license notices remain part of the distribution.",
                modifier = Modifier.padding(11.dp),
                style = MaterialTheme.typography.bodySmall,
                color = ADColors.Muted,
            )
        }
    }
}

@Composable
private fun ADAboutPrinciple(
    glyph: ADGlyph,
    title: String,
    detail: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = ADColors.Surface.copy(alpha = 0.86f),
        border = BorderStroke(1.dp, ADColors.Outline),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ADGlyphIcon(
                glyph = glyph,
                tint = ADColors.Ink,
                modifier = Modifier.size(20.dp),
                accent = if (glyph == ADGlyph.AI) ADColors.Red else null,
            )
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
            }
        }
    }
}

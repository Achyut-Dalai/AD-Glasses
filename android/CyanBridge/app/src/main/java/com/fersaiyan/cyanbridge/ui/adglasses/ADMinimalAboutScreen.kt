package com.fersaiyan.cyanbridge.ui.adglasses

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fersaiyan.cyanbridge.BuildConfig

@Composable
internal fun ADMinimalAboutScreen(onBack: () -> Unit) {
    ADPageLayout("About", onBack) {
        ADScreenIntro(
            eyebrow = "AD Glasses",
            title = "Glasses first",
            detail = "The phone is the quiet engine. The glasses are the interface.",
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = ADColors.Surface,
            border = BorderStroke(1.dp, ADColors.Outline),
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ADGlassesMark(Modifier.size(54.dp))
                    Column(Modifier.padding(start = 11.dp).weight(1f)) {
                        Text("AD GLASSES", style = ADMetaTextStyle, color = ADColors.InkSoft)
                        Spacer(Modifier.height(3.dp))
                        Text("AI eyewear companion", style = MaterialTheme.typography.titleLarge, color = ADColors.Ink)
                    }
                    Box(Modifier.size(6.dp).background(ADColors.Red, CircleShape))
                }
                Spacer(Modifier.height(13.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text("VERSION", style = ADMetaTextStyle, color = ADColors.InkSoft)
                        Text(
                            BuildConfig.VERSION_NAME,
                            style = MaterialTheme.typography.titleMedium,
                            color = ADColors.Ink,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Text("DARK · MATRIX", style = ADMetaTextStyle, color = ADColors.Muted)
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            ADSectionTitle("Principles")
            ADAboutPrinciple(ADMatrixGlyph.MIC, "Voice first", "Ask, capture and control without living on the phone.")
            ADAboutPrinciple(ADMatrixGlyph.LENS, "Vision when useful", "Bring the camera in only when seeing adds context.")
            ADAboutPrinciple(ADMatrixGlyph.AI, "AI with boundaries", "Local where possible, relay only when it earns the trip.")
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(13.dp),
            color = ADColors.Surface,
            border = BorderStroke(1.dp, ADColors.Outline),
        ) {
            Row(
                modifier = Modifier.padding(11.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier.size(30.dp).background(ADColors.SurfaceSubtle, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    ADMatrixGlyphIcon(ADMatrixGlyph.INFO, ADColors.InkSoft, Modifier.size(17.dp))
                }
                Text(
                    "AD Glasses includes open-source components and device SDK integrations. Required license notices remain part of the distribution.",
                    modifier = Modifier.padding(start = 9.dp).weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = ADColors.Muted,
                )
            }
        }
    }
}

@Composable
private fun ADAboutPrinciple(glyph: ADMatrixGlyph, title: String, detail: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(13.dp),
        color = ADColors.Surface,
        border = BorderStroke(1.dp, ADColors.Outline),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(34.dp).background(ADColors.SurfaceSubtle, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                ADMatrixGlyphIcon(
                    glyph = glyph,
                    tint = ADColors.Ink,
                    modifier = Modifier.size(20.dp),
                    accent = if (glyph == ADMatrixGlyph.AI) ADColors.Red else null,
                )
            }
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = ADColors.Ink, fontWeight = FontWeight.Medium)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
            }
        }
    }
}

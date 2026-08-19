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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
        Text(
            "AD Glasses is designed around a simple idea: the glasses are the interface and the phone is the quiet engine behind them.",
            style = MaterialTheme.typography.bodyMedium,
            color = ADColors.Muted,
        )

        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(min = 188.dp),
            shape = RoundedCornerShape(30.dp),
            color = ADColors.Ink,
            contentColor = ADColors.Surface,
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(58.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = ADColors.Surface.copy(alpha = 0.13f),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            ADGlassesMark(Modifier.size(width = 42.dp, height = 28.dp))
                        }
                    }
                    Column(Modifier.padding(start = 13.dp)) {
                        Text(
                            "AD GLASSES",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = ADTechFontFamily),
                            color = ADColors.Surface.copy(alpha = 0.60f),
                        )
                        Text(
                            "See more. Remember more.",
                            style = MaterialTheme.typography.titleMedium,
                            color = ADColors.Surface,
                        )
                    }
                }

                Column {
                    Text(
                        "VERSION",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = ADTechFontFamily),
                        color = ADColors.Surface.copy(alpha = 0.56f),
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        BuildConfig.VERSION_NAME.uppercase(),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = ADColors.Surface,
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ADSectionTitle("Design principles")
            ADAboutPrinciple(
                glyph = ADGlyph.ASK,
                title = "Voice first",
                detail = "Ask, capture and control without making the phone the main interface.",
            )
            ADAboutPrinciple(
                glyph = ADGlyph.LENS,
                title = "Vision when useful",
                detail = "Use the glasses camera when seeing something adds context to the question.",
            )
            ADAboutPrinciple(
                glyph = ADGlyph.AI,
                title = "Intelligence where it belongs",
                detail = "Local when possible, current through the relay when freshness matters.",
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = ADColors.SurfaceSubtle,
        ) {
            Text(
                "AD Glasses includes open-source components and device SDK integrations. Required license notices remain part of the distribution.",
                modifier = Modifier.padding(14.dp),
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
        shape = RoundedCornerShape(22.dp),
        color = ADColors.Surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(46.dp).background(ADColors.SurfaceSubtle, RoundedCornerShape(15.dp)),
                contentAlignment = Alignment.Center,
            ) {
                ADGlyphIcon(glyph, ADColors.Ink, Modifier.size(25.dp))
            }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(2.dp))
                Text(detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
            }
        }
    }
}

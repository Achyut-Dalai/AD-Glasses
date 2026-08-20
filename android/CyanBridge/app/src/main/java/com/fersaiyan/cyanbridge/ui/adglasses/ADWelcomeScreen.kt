package com.fersaiyan.cyanbridge.ui.adglasses

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** First-run product surface; intentionally uses the same dark product language as the app. */
@Composable
fun ADWelcomeScreen(
    onStartSetup: () -> Unit,
    onExplore: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ADGlassesMark(Modifier.size(32.dp))
            Column(Modifier.padding(start = 8.dp)) {
                Text("AD GLASSES", style = MaterialTheme.typography.labelSmall, color = ADColors.Muted)
                Text("AI eyewear", style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(6.dp).background(ADColors.Red, CircleShape))
        }

        Spacer(Modifier.size(24.dp))

        Column {
            Text("YOUR GLASSES", style = MaterialTheme.typography.displaySmall, color = ADColors.Ink)
            Text("YOUR AI", style = MaterialTheme.typography.displaySmall, color = ADColors.Ink)
            Text("YOUR DATA", style = MaterialTheme.typography.displaySmall, color = ADColors.Ink)
            Spacer(Modifier.size(8.dp))
            Text(
                "See, ask and remember without turning your phone into the main interface.",
                style = MaterialTheme.typography.bodyLarge,
                color = ADColors.Muted,
            )
        }

        Spacer(Modifier.size(18.dp))

        Surface(
            modifier = Modifier.fillMaxWidth().weight(1f).heightIn(min = 160.dp, max = 250.dp),
            shape = RoundedCornerShape(18.dp),
            color = Color.Black.copy(alpha = 0.34f),
            border = BorderStroke(1.dp, ADColors.Outline),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ADGlyphIcon(
                    glyph = ADGlyph.DEVICE,
                    tint = ADColors.Ink,
                    modifier = Modifier.size(112.dp),
                    accent = ADColors.Red,
                )
                Row(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ADWelcomeCapability(ADGlyph.ASK, "ASK")
                    ADWelcomeCapability(ADGlyph.PHOTO, "SEE")
                    ADWelcomeCapability(ADGlyph.AI, "REMEMBER")
                }
            }
        }

        Spacer(Modifier.size(14.dp))
        Surface(
            onClick = onStartSetup,
            modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp),
            shape = RoundedCornerShape(12.dp),
            color = ADColors.Red,
            contentColor = Color.White,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("Connect glasses", style = MaterialTheme.typography.labelLarge)
            }
        }
        Spacer(Modifier.size(7.dp))
        OutlinedButton(
            onClick = onExplore,
            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
            shape = RoundedCornerShape(11.dp),
            border = BorderStroke(1.dp, ADColors.Outline),
        ) {
            Text("Explore without glasses", style = MaterialTheme.typography.labelLarge, color = ADColors.Ink)
        }
        Spacer(Modifier.size(12.dp))
    }
}

@Composable
private fun ADWelcomeCapability(glyph: ADGlyph, label: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = ADColors.Surface.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, ADColors.Outline),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ADGlyphIcon(glyph, ADColors.Ink, Modifier.size(13.dp), accent = if (glyph == ADGlyph.AI) ADColors.Red else null)
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

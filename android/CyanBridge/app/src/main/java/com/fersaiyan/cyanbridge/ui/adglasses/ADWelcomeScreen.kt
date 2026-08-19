package com.fersaiyan.cyanbridge.ui.adglasses

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fersaiyan.cyanbridge.R

/** First-run product surface. Display only when onboarding state asks for it. */
@Composable
fun ADWelcomeScreen(
    onStartSetup: () -> Unit,
    onExplore: () -> Unit,
) {
    ADGlassesTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFFFBFBFC), ADColors.Background, Color(0xFFF0F0F2)),
                    ),
                )
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(15.dp),
                    color = ADColors.Surface,
                    border = BorderStroke(1.dp, ADColors.Outline.copy(alpha = 0.45f)),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Image(
                            painter = painterResource(R.drawable.ad_glasses_icon_source),
                            contentDescription = "AD Glasses",
                            modifier = Modifier.size(31.dp),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
                Column(Modifier.padding(start = 10.dp)) {
                    Text("AD GLASSES", style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = ADColors.Muted)
                    Text("See more. Remember more.", style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
                }
            }

            Spacer(Modifier.size(24.dp))

            Column {
                Text(
                    text = "YOUR GLASSES",
                    style = androidx.compose.material3.MaterialTheme.typography.displayMedium,
                    color = ADColors.Ink,
                )
                Text(
                    text = "YOUR AI",
                    style = androidx.compose.material3.MaterialTheme.typography.displayMedium,
                    color = ADColors.Ink,
                )
                Text(
                    text = "YOUR DATA",
                    style = androidx.compose.material3.MaterialTheme.typography.displayMedium,
                    color = ADColors.Ink,
                )
                Spacer(Modifier.size(9.dp))
                Text(
                    "A private companion that starts with what your glasses can see and hear, then keeps the useful parts close on your phone.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                    color = ADColors.Muted,
                )
            }

            Spacer(Modifier.size(20.dp))

            Surface(
                modifier = Modifier.fillMaxWidth().weight(1f).heightIn(min = 190.dp, max = 300.dp),
                shape = RoundedCornerShape(32.dp),
                color = ADColors.Surface,
                border = BorderStroke(1.dp, ADColors.Outline.copy(alpha = 0.42f)),
                shadowElevation = 2.dp,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(listOf(Color.White, Color(0xFFE8E9ED))),
                        )
                        .padding(10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ad_glasses_hero_v4),
                        contentDescription = "Smart glasses",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                    Row(
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 13.dp),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        ADWelcomeCapability(ADGlyph.ASK, "ASK")
                        ADWelcomeCapability(ADGlyph.PHOTO, "SEE")
                        ADWelcomeCapability(ADGlyph.AI, "REMEMBER")
                    }
                }
            }

            Spacer(Modifier.size(18.dp))
            ADPrimaryButton(text = "Connect glasses", onClick = onStartSetup)
            Spacer(Modifier.size(9.dp))
            OutlinedButton(
                onClick = onExplore,
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, ADColors.Outline.copy(alpha = 0.65f)),
            ) {
                Text("Explore without glasses", style = androidx.compose.material3.MaterialTheme.typography.labelLarge, color = ADColors.Ink)
            }
            Spacer(Modifier.size(16.dp))
        }
    }
}

@Composable
private fun ADWelcomeCapability(glyph: ADGlyph, label: String) {
    Surface(
        shape = CircleShape,
        color = ADColors.Surface.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, ADColors.Outline.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            ADGlyphIcon(glyph, ADColors.Ink, Modifier.size(15.dp))
            Text(label, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

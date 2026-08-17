package com.fersaiyan.cyanbridge.ui.adglasses

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                .background(ADColors.Background)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            ADTopBar(showBrand = true)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(22.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(224.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    Color(0xFFFAFBFD),
                                    ADColors.BlueSoft,
                                    Color(0xFFE7E9ED),
                                ),
                            ),
                            RoundedCornerShape(26.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ad_glasses_hero_v4),
                        contentDescription = "Smart glasses",
                        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp),
                        contentScale = ContentScale.Fit,
                    )
                }

                Spacer(Modifier.height(24.dp))
                Text(
                    text = "YOUR GLASSES · YOUR AI · YOUR DATA",
                    style = MaterialTheme.typography.labelMedium,
                    color = ADColors.Blue,
                    letterSpacing = 1.55.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(9.dp))
                Text(
                    text = "A private brain for the glasses you wear.",
                    style = MaterialTheme.typography.headlineMedium,
                    color = ADColors.Ink,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Connect when you are ready. Your prompts, captures and memories stay centered around you.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ADColors.Muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )

                Spacer(Modifier.weight(1f))
                Button(
                    onClick = onStartSetup,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ADColors.Blue),
                    shape = RoundedCornerShape(14.dp),
                ) { Text("Connect glasses") }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onExplore,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                    shape = RoundedCornerShape(14.dp),
                ) { Text("Continue without glasses") }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

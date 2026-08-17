package com.fersaiyan.cyanbridge.ui.adglasses

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontWeight
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
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFFFAFAFB),
                            ADColors.Background,
                            Color(0xFFF0F0F2),
                        ),
                    ),
                )
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 15.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(R.drawable.ad_glasses_icon_source),
                    contentDescription = "AD Glasses",
                    modifier = Modifier.size(32.dp),
                    contentScale = ContentScale.Fit,
                )
                Text(
                    text = "AD GLASSES",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 2.5.sp,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 28.dp),
            ) {
                Text(
                    text = "YOUR GLASSES",
                    color = ADColors.Ink,
                    fontSize = 42.sp,
                    lineHeight = 44.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-1.15).sp,
                )
                Text(
                    text = "YOUR AI",
                    color = ADColors.Ink,
                    fontSize = 42.sp,
                    lineHeight = 44.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-1.15).sp,
                )
                Text(
                    text = "YOUR DATA",
                    color = ADColors.Ink,
                    fontSize = 42.sp,
                    lineHeight = 44.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-1.15).sp,
                )
            }

            Spacer(Modifier.size(22.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .heightIn(min = 176.dp, max = 278.dp)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color.White.copy(alpha = 0.92f),
                                Color(0xFFE9E9EC),
                            ),
                        ),
                        RoundedCornerShape(28.dp),
                    )
                    .border(
                        width = 1.dp,
                        color = ADColors.Outline.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(28.dp),
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ad_glasses_hero_v4),
                    contentDescription = "Smart glasses",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }

            Spacer(Modifier.size(22.dp))
            Button(
                onClick = onStartSetup,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ADColors.Ink),
                shape = RoundedCornerShape(15.dp),
            ) {
                Text("Connect glasses")
            }
            Spacer(Modifier.size(10.dp))
            OutlinedButton(
                onClick = onExplore,
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                shape = RoundedCornerShape(15.dp),
            ) {
                Text("Continue without glasses")
            }
            Spacer(Modifier.size(22.dp))
        }
    }
}

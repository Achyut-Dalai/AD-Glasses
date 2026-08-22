package com.ad_glasses.ui.adglasses

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.ad_glasses.R

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
                .padding(horizontal = 17.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 22.dp),
            ) {
                Text(
                    text = "YOUR GLASSES",
                    color = ADColors.Ink,
                    fontSize = 33.sp,
                    lineHeight = 35.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-0.7).sp,
                )
                Text(
                    text = "YOUR AI",
                    color = ADColors.Ink,
                    fontSize = 33.sp,
                    lineHeight = 35.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-0.7).sp,
                )
                Text(
                    text = "YOUR DATA",
                    color = ADColors.Ink,
                    fontSize = 33.sp,
                    lineHeight = 35.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-0.7).sp,
                )
            }

            Spacer(Modifier.size(17.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .heightIn(min = 160.dp, max = 240.dp)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color.White.copy(alpha = 0.94f),
                                Color(0xFFE9E9EC),
                            ),
                        ),
                        RoundedCornerShape(24.dp),
                    )
                    .border(
                        width = 1.dp,
                        color = ADColors.Outline.copy(alpha = 0.50f),
                        shape = RoundedCornerShape(24.dp),
                    )
                    .padding(horizontal = 8.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ad_glasses_hero_v4),
                    contentDescription = "Smart glasses",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }

            Spacer(Modifier.size(16.dp))
            Button(
                onClick = onStartSetup,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ADColors.Ink),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text("Connect glasses")
            }
            Spacer(Modifier.size(8.dp))
            OutlinedButton(
                onClick = onExplore,
                modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text("Continue without glasses")
            }
            Spacer(Modifier.size(15.dp))
        }
    }
}

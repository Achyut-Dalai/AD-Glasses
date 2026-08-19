package com.fersaiyan.cyanbridge.ui.adglasses

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
                        listOf(Color(0xFFFCFEFE), ADColors.Background, Color(0xFFF0F5F6)),
                    ),
                )
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ADGlassesMark(Modifier.size(width = 44.dp, height = 24.dp))
                Spacer(Modifier.size(9.dp))
                Text(
                    "AD GLASSES",
                    color = ADColors.Ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.1.sp,
                )
            }

            Column(Modifier.fillMaxWidth()) {
                Text(
                    text = "YOUR GLASSES",
                    color = ADColors.Ink,
                    fontSize = 39.sp,
                    lineHeight = 41.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-1.0).sp,
                )
                Text(
                    text = "YOUR AI",
                    color = ADColors.Ink,
                    fontSize = 39.sp,
                    lineHeight = 41.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-1.0).sp,
                )
                Text(
                    text = "YOUR DATA",
                    color = ADColors.CyanDeep,
                    fontSize = 39.sp,
                    lineHeight = 41.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-1.0).sp,
                )
            }

            Spacer(Modifier.size(22.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .heightIn(min = 178.dp, max = 272.dp)
                    .background(
                        Brush.linearGradient(listOf(Color.White, ADColors.CyanMist, Color(0xFFE5ECEE))),
                        RoundedCornerShape(24.dp),
                    )
                    .border(1.dp, ADColors.Outline, RoundedCornerShape(24.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ad_glasses_hero_v4),
                    contentDescription = "Smart glasses",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }

            Spacer(Modifier.size(20.dp))
            Button(
                onClick = onStartSetup,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ADColors.Graphite),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text("Connect glasses")
            }
            Spacer(Modifier.size(9.dp))
            OutlinedButton(
                onClick = onExplore,
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text("Continue without glasses")
            }
            Spacer(Modifier.size(20.dp))
        }
    }
}

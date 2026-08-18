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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fersaiyan.cyanbridge.R

/** First-run product surface. ADGlassesApp owns the persisted onboarding gate. */
@Composable
fun ADWelcomeScreen(
    onStartSetup: () -> Unit,
    onExplore: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        ADColors.HeroStart,
                        ADColors.Background,
                        ADColors.HeroEnd,
                    ),
                ),
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(9.dp).background(ADColors.Ink, CircleShape))
            Text(
                text = "AD GLASSES",
                modifier = Modifier.padding(start = 9.dp),
                style = MaterialTheme.typography.labelLarge,
                letterSpacing = 1.1.sp,
            )
        }

        Column(modifier = Modifier.fillMaxWidth().padding(top = 34.dp)) {
            Text(
                text = "Your glasses.",
                color = ADColors.Ink,
                fontSize = 38.sp,
                lineHeight = 41.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-1.0).sp,
            )
            Text(
                text = "Your AI.",
                color = ADColors.Ink,
                fontSize = 38.sp,
                lineHeight = 41.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-1.0).sp,
            )
            Text(
                text = "Your data.",
                color = ADColors.Ink,
                fontSize = 38.sp,
                lineHeight = 41.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-1.0).sp,
            )
            Spacer(Modifier.size(12.dp))
            Text(
                text = "Connect once, then use voice, vision and AI from the glasses while the phone quietly does the work.",
                style = MaterialTheme.typography.bodyLarge,
                color = ADColors.Muted,
            )
        }

        Spacer(Modifier.size(24.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .heightIn(min = 190.dp, max = 292.dp)
                .background(
                    Brush.radialGradient(
                        listOf(
                            ADColors.HeroStart,
                            ADColors.HeroMiddle,
                            ADColors.HeroEnd,
                        ),
                    ),
                    RoundedCornerShape(28.dp),
                )
                .border(
                    width = 1.dp,
                    color = ADColors.Outline.copy(alpha = 0.38f),
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
                colorFilter = if (ADColors.IsDark) ColorFilter.tint(ADColors.Ink.copy(alpha = 0.88f)) else null,
            )
        }

        Spacer(Modifier.size(22.dp))
        Button(
            onClick = onStartSetup,
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ADColors.Ink,
                contentColor = ADColors.Surface,
            ),
            shape = RoundedCornerShape(18.dp),
        ) {
            Text("Connect glasses")
        }
        Spacer(Modifier.size(10.dp))
        OutlinedButton(
            onClick = onExplore,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = RoundedCornerShape(18.dp),
        ) {
            Text("Continue without glasses")
        }
        Spacer(Modifier.size(22.dp))
    }
}

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
import androidx.compose.ui.unit.sp
import com.fersaiyan.cyanbridge.BuildConfig

@Composable
internal fun ADMinimalAboutScreen(onBack: () -> Unit) {
    ADPageLayout("About", onBack) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 420.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color.Black,
            contentColor = ADColors.Ink,
            border = BorderStroke(1.dp, ADColors.Outline),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Box(
                        Modifier
                            .widthFraction(0.18f)
                            .height(2.dp)
                            .background(ADColors.Red, CircleShape),
                    )
                    Spacer(Modifier.height(26.dp))
                    Text(
                        "YOUR GLASSES",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.25).sp,
                        ),
                        color = ADColors.Ink,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "YOUR AI",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.25).sp,
                        ),
                        color = ADColors.InkSoft,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "YOUR DATA",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.25).sp,
                        ),
                        color = ADColors.Muted,
                    )
                }

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).background(ADColors.Red, CircleShape))
                        Text(
                            "ALPHA",
                            modifier = Modifier.padding(start = 8.dp),
                            style = ADMetaTextStyle,
                            color = ADColors.InkSoft,
                        )
                    }
                    Spacer(Modifier.height(7.dp))
                    Text(
                        "VERSION ${BuildConfig.VERSION_NAME}",
                        style = ADMetaTextStyle,
                        color = ADColors.Muted,
                    )
                }
            }
        }
    }
}

private fun Modifier.widthFraction(fraction: Float): Modifier = fillMaxWidth(fraction.coerceIn(0f, 1f))

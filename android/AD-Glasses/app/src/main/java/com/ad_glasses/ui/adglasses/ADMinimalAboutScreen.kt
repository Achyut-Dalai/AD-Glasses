package com.ad_glasses.ui.adglasses

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ad_glasses.BuildConfig

@Composable
internal fun ADMinimalAboutScreen(onBack: () -> Unit) {
    ADPageLayout("About", onBack) {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(min = 138.dp),
            shape = RoundedCornerShape(24.dp),
            color = ADColors.Ink,
            contentColor = ADColors.Surface,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 17.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "VERSION",
                    style = MaterialTheme.typography.labelSmall,
                    color = ADColors.Surface.copy(alpha = 0.58f),
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    BuildConfig.VERSION_NAME.uppercase(),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = ADColors.Surface,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Built around the glasses",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                "The phone is the engine, not the destination. Voice, camera and lightweight actions stay centered on what the glasses can do naturally.",
                style = MaterialTheme.typography.bodyMedium,
                color = ADColors.Muted,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            ADAboutPrinciple(
                icon = Icons.Outlined.Mic,
                title = "Voice first",
                detail = "Ask, capture and control without turning the phone into the main interface.",
            )
            ADAboutPrinciple(
                icon = Icons.Outlined.Visibility,
                title = "Vision when useful",
                detail = "Use the glasses camera for capture and visual questions when the connected hardware supports it.",
            )
            ADAboutPrinciple(
                icon = Icons.Outlined.Public,
                title = "Current when needed",
                detail = "Gemini Live can ground current questions with Google Search. Standard Cloud chat does not browse unless its selected provider route explicitly supports web search.",
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = ADColors.SurfaceSubtle,
        ) {
            Text(
                "AD Glasses includes open-source components and device SDK integrations. Required license notices remain part of the distribution.",
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = ADColors.Muted,
            )
        }
    }
}

@Composable
private fun ADAboutPrinciple(
    icon: ImageVector,
    title: String,
    detail: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = ADColors.Surface,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(ADColors.SurfaceSubtle, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = ADColors.Ink, modifier = Modifier.size(19.dp))
            }
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(1.dp))
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = ADColors.Muted,
                )
            }
        }
    }
}

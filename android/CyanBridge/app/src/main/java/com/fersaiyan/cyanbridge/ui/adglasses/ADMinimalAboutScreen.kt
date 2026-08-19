package com.fersaiyan.cyanbridge.ui.adglasses

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fersaiyan.cyanbridge.BuildConfig

@Composable
internal fun ADMinimalAboutScreen(onBack: () -> Unit) {
    ADPageLayout("About", onBack) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(ADColors.Graphite, RoundedCornerShape(20.dp))
                .padding(20.dp),
        ) {
            Box(
                modifier = Modifier.size(width = 66.dp, height = 42.dp).background(
                    Color.White.copy(alpha = 0.10f),
                    RoundedCornerShape(13.dp),
                ),
                contentAlignment = Alignment.Center,
            ) {
                ADGlassesMark(Modifier.size(width = 48.dp, height = 28.dp))
            }
            Spacer(Modifier.height(18.dp))
            Text(
                "AD Glasses",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "A quiet interface for a capable pair of glasses.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.68f),
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.background(Color.White.copy(alpha = 0.09f), RoundedCornerShape(10.dp)).padding(9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Version", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.58f))
                Spacer(Modifier.size(8.dp))
                Text(BuildConfig.VERSION_NAME, style = MaterialTheme.typography.labelLarge, color = Color.White)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("Built around the glasses", style = MaterialTheme.typography.headlineSmall)
            Text(
                "The phone is the engine, not the destination. Voice, camera and lightweight actions stay centered on what the glasses can do naturally.",
                style = MaterialTheme.typography.bodyMedium,
                color = ADColors.Muted,
            )
        }

        ADCard {
            ADAboutPrinciple(
                icon = Icons.Outlined.Mic,
                title = "Voice first",
                detail = "Ask, capture and control without making the phone the main interface.",
            )
            androidx.compose.material3.HorizontalDivider(Modifier.padding(start = 50.dp), color = ADColors.Separator)
            ADAboutPrinciple(
                icon = Icons.Outlined.Visibility,
                title = "Vision when useful",
                detail = "Use the glasses camera for capture and visual questions when supported.",
            )
            androidx.compose.material3.HorizontalDivider(Modifier.padding(start = 50.dp), color = ADColors.Separator)
            ADAboutPrinciple(
                icon = Icons.Outlined.Public,
                title = "Current when needed",
                detail = "Fresh information can use Web Search through the relay you choose.",
            )
        }

        Text(
            "AD Glasses includes open-source components and device SDK integrations. Required license notices remain part of the distribution.",
            style = MaterialTheme.typography.bodySmall,
            color = ADColors.Muted,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}

@Composable
private fun ADAboutPrinciple(
    icon: ImageVector,
    title: String,
    detail: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(40.dp).background(ADColors.SurfaceSubtle, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = ADColors.Ink, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(1.dp))
            Text(detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
        }
    }
}

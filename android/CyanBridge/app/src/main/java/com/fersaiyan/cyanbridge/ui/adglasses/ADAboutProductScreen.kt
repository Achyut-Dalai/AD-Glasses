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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.fersaiyan.cyanbridge.BuildConfig

@Composable
internal fun ADAboutProductScreen(onBack: () -> Unit) {
    ADPageLayout("About", onBack) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(ADColors.Ink, CircleShape))
                Text(
                    "AD Glasses",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
            Text(
                "A glasses-first AI companion. The phone does the heavy work without becoming the destination.",
                style = MaterialTheme.typography.bodyLarge,
                color = ADColors.Muted,
            )
            HorizontalDivider(color = ADColors.Separator)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Version", style = MaterialTheme.typography.bodyMedium, color = ADColors.Muted)
                Spacer(Modifier.weight(1f))
                Text(BuildConfig.VERSION_NAME, style = MaterialTheme.typography.labelLarge)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Built around the glasses", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Voice, vision and lightweight actions stay centered on what the glasses can do naturally. The phone remains the engine for AI, storage and integrations.",
                style = MaterialTheme.typography.bodyLarge,
                color = ADColors.Muted,
            )
        }

        ADAboutPrincipleCard(
            icon = Icons.Outlined.Mic,
            title = "Voice first",
            detail = "Ask, capture and control without turning the phone into the main interface.",
        )
        ADAboutPrincipleCard(
            icon = Icons.Outlined.Visibility,
            title = "Vision when useful",
            detail = "Use the glasses camera for capture and visual questions when the connected hardware supports it.",
        )
        ADAboutPrincipleCard(
            icon = Icons.Outlined.Public,
            title = "Current when needed",
            detail = "Fresh information can use Web through the assistant; local features stay local where possible.",
        )

        Text(
            "AD Glasses includes open-source components and device SDK integrations. Required license notices remain part of the distribution.",
            style = MaterialTheme.typography.bodyMedium,
            color = ADColors.Muted,
        )
    }
}

@Composable
private fun ADAboutPrincipleCard(
    icon: ImageVector,
    title: String,
    detail: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ADColors.Surface, RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier.size(42.dp).background(ADColors.SurfaceSubtle, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = ADColors.Ink, modifier = Modifier.size(21.dp))
        }
        Column(Modifier.padding(start = 13.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(3.dp))
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = ADColors.Muted)
        }
    }
}

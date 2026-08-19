package com.fersaiyan.cyanbridge.ui.adglasses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fersaiyan.cyanbridge.BuildConfig
import java.util.Locale

@Composable
internal fun ADMinimalAboutScreen(onBack: () -> Unit) {
    ADPageLayout("About", onBack) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Column(Modifier.padding(22.dp)) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                        ADGlassesMark(Modifier.size(width = 48.dp, height = 32.dp))
                    }
                }
                Spacer(Modifier.height(22.dp))
                Text(
                    "AD Glasses",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "A quiet interface for a very capable pair of glasses.",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.76f),
                )
                Spacer(Modifier.height(18.dp))
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Text(
                        "VERSION ${BuildConfig.VERSION_NAME.uppercase(Locale.US)}",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Built around the glasses",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                "The phone is the engine, not the destination. Voice, camera and lightweight actions stay centered on what the glasses can do naturally.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        ADAboutPrinciple(
            icon = Icons.Outlined.Mic,
            title = "Voice first",
            detail = "Ask, capture and control without turning the phone into the main interface.",
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        ADAboutPrinciple(
            icon = Icons.Outlined.Visibility,
            title = "Vision when useful",
            detail = "Use the glasses camera for capture and visual questions when the connected hardware supports it.",
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        )
        ADAboutPrinciple(
            icon = Icons.Outlined.Public,
            title = "Current when needed",
            detail = "Fresh information can use Web Search through the relay you choose; local features stay local where possible.",
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
            "AD Glasses includes open-source components and device SDK integrations. Required license notices remain part of the distribution.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ADAboutPrinciple(
    icon: ImageVector,
    title: String,
    detail: String,
    containerColor: Color,
    contentColor: Color,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = contentColor.copy(alpha = 0.10f),
                contentColor = contentColor,
            ) {
                Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
                }
            }
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = contentColor.copy(alpha = 0.78f))
            }
        }
    }
}

package com.fersaiyan.cyanbridge.ui.adglasses

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeveloperMode
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fersaiyan.cyanbridge.R
import com.fersaiyan.cyanbridge.devices.DeviceProfileStore
import com.fersaiyan.cyanbridge.shared.glasses.GlassesDashboardUiState

@Composable
internal fun ADNativeSettingsHubScreen(
    state: GlassesDashboardUiState,
    themeStyle: ADThemeStyle,
    onThemeStyleChanged: (ADThemeStyle) -> Unit,
    onBack: () -> Unit,
    onDevice: () -> Unit,
    onPrivacy: () -> Unit,
    onStorage: () -> Unit,
    onLanguage: () -> Unit,
    onPermissions: () -> Unit,
    onAdvanced: () -> Unit,
    onAbout: () -> Unit,
) {
    val context = LocalContext.current
    val presentation = buildADDevicePresentation(
        state = state,
        profile = DeviceProfileStore.loadLastSelected(context),
    )

    ADPageLayout("Settings", onBack) {
        Surface(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onDevice),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ad_glasses_hero_v4),
                        contentDescription = "Glasses",
                        modifier = Modifier.size(width = 86.dp, height = 48.dp).padding(6.dp),
                        contentScale = ContentScale.Fit,
                    )
                }
                Column(Modifier.padding(start = 14.dp).weight(1f)) {
                    Text(
                        presentation.identityLabel ?: "Glasses",
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        presentation.statusLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
                    )
                }
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.65f),
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Palette,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "Appearance",
                    modifier = Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                "Same AD, different energy. No gradients, no AI wallpaper.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ADThemeChoice(
                    title = "Mono",
                    detail = "Quiet · graphite",
                    selected = themeStyle == ADThemeStyle.MONO,
                    modifier = Modifier.weight(1f),
                ) { onThemeStyleChanged(ADThemeStyle.MONO) }
                ADThemeChoice(
                    title = "Vibe",
                    detail = "Editorial · warm",
                    selected = themeStyle == ADThemeStyle.VIBE,
                    modifier = Modifier.weight(1f),
                ) { onThemeStyleChanged(ADThemeStyle.VIBE) }
            }
        }

        ADExpressiveSettingsGroup("Privacy & data") {
            ADExpressiveSettingsRow(Icons.Outlined.PrivacyTip, "Privacy", "Transcripts, redaction and exports", onPrivacy)
            ADExpressiveSettingsDivider()
            ADExpressiveSettingsRow(Icons.Outlined.Storage, "Storage", "App data and synced glasses media", onStorage)
        }

        ADExpressiveSettingsGroup("General") {
            ADExpressiveSettingsRow(Icons.Outlined.Language, "Language", "App language and system locale", onLanguage)
            ADExpressiveSettingsDivider()
            ADExpressiveSettingsRow(
                Icons.Outlined.Security,
                "Permissions",
                "Camera, microphone, Bluetooth and nearby devices",
                onPermissions,
            )
        }

        ADExpressiveSettingsGroup("AD Glasses") {
            ADExpressiveSettingsRow(Icons.Outlined.DeveloperMode, "Advanced", "Diagnostics and system controls", onAdvanced)
            ADExpressiveSettingsDivider()
            ADExpressiveSettingsRow(Icons.Outlined.Info, "About AD Glasses", "Version, principles and product information", onAbout)
        }
    }
}

@Composable
private fun ADThemeChoice(
    title: String,
    detail: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val container = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
    val content = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface

    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = container,
        contentColor = content,
        tonalElevation = if (selected) 2.dp else 0.dp,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (selected) {
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
                    ) {
                        Icon(
                            Icons.Outlined.Check,
                            contentDescription = "$title selected",
                            modifier = Modifier.padding(6.dp).size(16.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                detail,
                style = MaterialTheme.typography.bodyMedium,
                color = content.copy(alpha = 0.72f),
            )
        }
    }
}

@Composable
private fun ADExpressiveSettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
        ) {
            Column(Modifier.padding(horizontal = 16.dp), content = content)
        }
    }
}

@Composable
private fun ADExpressiveSettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(21.dp))
            }
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ADExpressiveSettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 54.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

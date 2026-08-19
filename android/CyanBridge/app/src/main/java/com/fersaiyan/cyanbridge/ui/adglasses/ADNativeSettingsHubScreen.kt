package com.fersaiyan.cyanbridge.ui.adglasses

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.DeveloperMode
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fersaiyan.cyanbridge.shared.glasses.GlassesDashboardUiState

@Composable
internal fun ADNativeSettingsHubScreen(
    state: GlassesDashboardUiState,
    onBack: () -> Unit,
    onDevice: () -> Unit,
    onPrivacy: () -> Unit,
    onStorage: () -> Unit,
    onLanguage: () -> Unit,
    onPermissions: () -> Unit,
    onAdvanced: () -> Unit,
    onAbout: () -> Unit,
) {
    ADPageLayout("Settings", onBack) {
        ADSettingsDeviceSummary(state = state, onClick = onDevice)

        ADExpressiveSettingsGroup("Privacy & data") {
            ADExpressiveSettingsRow(Icons.Outlined.PrivacyTip, "Privacy", "Transcripts, redaction and exports", onPrivacy)
            ADExpressiveSettingsDivider()
            ADExpressiveSettingsRow(Icons.Outlined.Storage, "Storage", "App data and synced glasses media", onStorage)
        }

        ADExpressiveSettingsGroup("App") {
            ADExpressiveSettingsRow(Icons.Outlined.Language, "Language", "App language and system locale", onLanguage)
            ADExpressiveSettingsDivider()
            ADExpressiveSettingsRow(
                Icons.Outlined.Security,
                "Permissions",
                "Camera, microphone, Bluetooth and nearby devices",
                onPermissions,
            )
        }

        ADExpressiveSettingsGroup("System") {
            ADExpressiveSettingsRow(Icons.Outlined.DeveloperMode, "Advanced", "Diagnostics and Android controls", onAdvanced)
            ADExpressiveSettingsDivider()
            ADExpressiveSettingsRow(Icons.Outlined.Info, "About AD Glasses", "Version and product information", onAbout)
        }
    }
}

@Composable
private fun ADSettingsDeviceSummary(
    state: GlassesDashboardUiState,
    onClick: () -> Unit,
) {
    ADCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(ADColors.CyanSoft, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                ADGlassesMark(Modifier.size(width = 38.dp, height = 22.dp))
            }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text("Your glasses", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(2.dp))
                Text(
                    state.connectionLabel.ifBlank { "Open Device Center" },
                    style = MaterialTheme.typography.bodySmall,
                    color = ADColors.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = ADColors.Muted,
                modifier = Modifier.size(22.dp),
            )
        }

        if ((state.showBattery && state.batteryPercent != null) || (state.showStorage && state.storageLabel != "--")) {
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = ADColors.Separator)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                if (state.showBattery && state.batteryPercent != null) {
                    ADSettingsDeviceMetric(Icons.Outlined.BatteryFull, "Battery", "${state.batteryPercent}%")
                }
                if (state.showStorage && state.storageLabel != "--") {
                    ADSettingsDeviceMetric(Icons.Outlined.Storage, "Storage", state.storageLabel)
                }
            }
        }
    }
}

@Composable
private fun ADSettingsDeviceMetric(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, contentDescription = null, tint = ADColors.Muted, modifier = Modifier.size(17.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = ADColors.Muted)
            Text(value, style = MaterialTheme.typography.labelLarge, color = ADColors.Ink)
        }
    }
}

@Composable
private fun ADExpressiveSettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = ADColors.Muted,
            modifier = Modifier.padding(start = 2.dp),
        )
        ADCard {
            Column(content = content)
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
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(40.dp).background(ADColors.SurfaceSubtle, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = ADColors.Ink, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(1.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = ADColors.Muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = ADColors.Muted,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun ADExpressiveSettingsDivider() {
    HorizontalDivider(modifier = Modifier.padding(start = 52.dp), color = ADColors.Separator)
}

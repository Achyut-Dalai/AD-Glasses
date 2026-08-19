package com.fersaiyan.cyanbridge.ui.adglasses

import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fersaiyan.cyanbridge.devices.DeviceProfileStore
import com.fersaiyan.cyanbridge.shared.glasses.GlassesDashboardUiState

private enum class ADSettingsArtwork {
    PRIVACY,
    STORAGE,
    LANGUAGE,
    PERMISSIONS,
}

@Composable
internal fun ADNativeSettingsHubScreen(
    state: GlassesDashboardUiState,
    onBack: () -> Unit,
    onDevice: () -> Unit,
    onPrivacy: () -> Unit,
    onStorage: () -> Unit,
    onLanguage: () -> Unit,
    onPermissions: () -> Unit,
    onAbout: () -> Unit,
) {
    val context = LocalContext.current
    val presentation = buildADDevicePresentation(
        state = state,
        profile = DeviceProfileStore.loadLastSelected(context),
    )

    ADPageLayout("Settings", onBack) {
        ADSettingsDeviceOverview(
            state = state,
            presentation = presentation,
            onClick = onDevice,
        )

        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            ADSettingsSectionTitle("Essentials")
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                ADSettingsTile(
                    artwork = ADSettingsArtwork.PRIVACY,
                    title = "Privacy",
                    modifier = Modifier.weight(1f),
                    onClick = onPrivacy,
                )
                ADSettingsTile(
                    artwork = ADSettingsArtwork.STORAGE,
                    title = "Storage",
                    modifier = Modifier.weight(1f),
                    onClick = onStorage,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                ADSettingsTile(
                    artwork = ADSettingsArtwork.LANGUAGE,
                    title = "Language",
                    modifier = Modifier.weight(1f),
                    onClick = onLanguage,
                )
                ADSettingsTile(
                    artwork = ADSettingsArtwork.PERMISSIONS,
                    title = "Permissions",
                    modifier = Modifier.weight(1f),
                    onClick = onPermissions,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            ADSettingsSectionTitle("Advanced")
            ADSettingsWideAction(
                icon = Icons.Outlined.Settings,
                title = "Android app settings",
                subtitle = "Permissions, battery and system controls",
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(
                                AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${context.packageName}"),
                            ),
                        )
                    }
                },
            )
            ADSettingsWideAction(
                icon = Icons.Outlined.Info,
                title = "About AD Glasses",
                subtitle = "Version and product information",
                onClick = onAbout,
            )
        }
    }
}

@Composable
private fun ADSettingsDeviceOverview(
    state: GlassesDashboardUiState,
    presentation: ADDevicePresentation,
    onClick: () -> Unit,
) {
    val showBattery = presentation.connected && state.showBattery && state.batteryPercent != null
    val showStorage = presentation.connected && state.showStorage && state.storageLabel != "--"
    val status = when {
        presentation.connected -> "Connected"
        presentation.connecting -> "Connecting…"
        presentation.shouldOpenSetup -> "Not connected"
        else -> "Reconnect available"
    }

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(21.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 0.dp,
    ) {
        Column(Modifier.padding(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = RoundedCornerShape(13.dp),
                    color = ADColors.Ink,
                    contentColor = ADColors.Surface,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.Bluetooth,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Text(
                        presentation.identityLabel ?: "Your glasses",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(6.dp)
                                .background(
                                    if (presentation.connected) ADColors.Ink else MaterialTheme.colorScheme.outline,
                                    CircleShape,
                                ),
                        )
                        Text(
                            status,
                            modifier = Modifier.padding(start = 6.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                        )
                    }
                }
            }

            when {
                showBattery || showStorage -> {
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(9.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        if (showBattery) {
                            ADDeviceMetric(
                                icon = Icons.Outlined.BatteryFull,
                                label = "Battery",
                                value = "${state.batteryPercent}%",
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (showStorage) {
                            ADDeviceMetric(
                                icon = Icons.Outlined.Storage,
                                label = "Storage",
                                value = state.storageLabel,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                !presentation.connected -> {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Connect your glasses to feel the power of AI.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ADDeviceMetric(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.heightIn(min = 54.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = ADColors.Ink,
                modifier = Modifier.size(18.dp),
            )
            Column(Modifier.padding(start = 7.dp).weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                )
                Text(
                    value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ADSettingsSectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Medium,
        color = ADColors.Ink,
        modifier = Modifier.padding(start = 1.dp),
    )
}

@Composable
private fun ADSettingsTile(
    artwork: ADSettingsArtwork,
    title: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 96.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 11.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            ADSettingsArtworkIcon(artwork)
            Spacer(Modifier.height(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ADSettingsArtworkIcon(artwork: ADSettingsArtwork) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(13.dp)),
        contentAlignment = Alignment.Center,
    ) {
        when (artwork) {
            ADSettingsArtwork.PRIVACY -> Icon(
                Icons.Outlined.Lock,
                contentDescription = null,
                tint = ADColors.Ink,
                modifier = Modifier.size(21.dp),
            )

            ADSettingsArtwork.STORAGE -> Icon(
                Icons.Outlined.Storage,
                contentDescription = null,
                tint = ADColors.Ink,
                modifier = Modifier.size(22.dp),
            )

            ADSettingsArtwork.LANGUAGE -> Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("A", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = ADColors.Ink)
                Box(
                    Modifier
                        .width(2.dp)
                        .height(19.dp)
                        .background(ADColors.Ink.copy(alpha = 0.20f), RoundedCornerShape(2.dp)),
                )
                Text("文", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium, color = ADColors.Ink)
            }

            ADSettingsArtwork.PERMISSIONS -> Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(2) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        repeat(2) { column ->
                            Box(
                                Modifier
                                    .size(9.dp)
                                    .background(
                                        if (row == column) ADColors.Ink else ADColors.Ink.copy(alpha = 0.20f),
                                        RoundedCornerShape(3.dp),
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ADSettingsWideAction(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = RoundedCornerShape(11.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = ADColors.Ink,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(1.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

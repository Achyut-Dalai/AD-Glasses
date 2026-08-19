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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.DeveloperMode
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fersaiyan.cyanbridge.devices.DeviceProfileStore
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

        ADSettingsGroup(
            title = "Privacy & data",
            containerColor = ADSettingsPalette.WarmGroup,
        ) {
            ADSettingsRow(
                icon = Icons.Outlined.PrivacyTip,
                title = "Privacy",
                subtitle = "Transcripts, redaction and exports",
                accent = ADSettingsPalette.Privacy,
                onClick = onPrivacy,
            )
            ADSettingsDivider()
            ADSettingsRow(
                icon = Icons.Outlined.Storage,
                title = "Storage",
                subtitle = "App data and synced glasses media",
                accent = ADSettingsPalette.Storage,
                onClick = onStorage,
            )
        }

        ADSettingsGroup(
            title = "General",
            containerColor = ADSettingsPalette.CoolGroup,
        ) {
            ADSettingsRow(
                icon = Icons.Outlined.Language,
                title = "Language",
                subtitle = "App language and system locale",
                accent = ADSettingsPalette.Language,
                onClick = onLanguage,
            )
            ADSettingsDivider()
            ADSettingsRow(
                icon = Icons.Outlined.Security,
                title = "Permissions",
                subtitle = "Camera, microphone, Bluetooth and nearby devices",
                accent = ADSettingsPalette.Permissions,
                onClick = onPermissions,
            )
        }

        ADSettingsGroup(
            title = "AD Glasses",
            containerColor = ADSettingsPalette.ProductGroup,
        ) {
            ADSettingsRow(
                icon = Icons.Outlined.DeveloperMode,
                title = "Advanced",
                subtitle = "Diagnostics and system controls",
                accent = ADSettingsPalette.Advanced,
                onClick = onAdvanced,
            )
            ADSettingsDivider()
            ADSettingsRow(
                icon = Icons.Outlined.Info,
                title = "About AD Glasses",
                subtitle = "Version and product information",
                accent = ADSettingsPalette.About,
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
    val battery = if (state.showBattery && state.batteryPercent != null) {
        "${state.batteryPercent}%"
    } else {
        "—"
    }
    val storage = if (state.showStorage && state.storageLabel != "--") {
        state.storageLabel
    } else {
        "—"
    }
    val status = when {
        presentation.connected -> "Connected"
        presentation.connecting -> "Connecting…"
        presentation.shouldOpenSetup -> "Not connected"
        else -> "Reconnect"
    }

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = ADSettingsPalette.DeviceCard,
        contentColor = ADSettingsPalette.DeviceInk,
        tonalElevation = 0.dp,
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = ADSettingsPalette.DeviceIcon,
                    contentColor = ADSettingsPalette.DeviceInk,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.Bluetooth,
                            contentDescription = null,
                            modifier = Modifier.size(23.dp),
                        )
                    }
                }

                Column(Modifier.padding(start = 13.dp).weight(1f)) {
                    Text(
                        presentation.identityLabel ?: "Your glasses",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(7.dp)
                                .background(
                                    if (presentation.connected) ADColors.Success else ADSettingsPalette.DeviceMuted,
                                    CircleShape,
                                ),
                        )
                        Text(
                            status,
                            modifier = Modifier.padding(start = 7.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = ADSettingsPalette.DeviceMuted,
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Manage",
                        style = MaterialTheme.typography.labelLarge,
                        color = ADSettingsPalette.DeviceInk,
                    )
                    Icon(
                        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(21.dp),
                        tint = ADSettingsPalette.DeviceInk,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ADDeviceMetric(
                    icon = Icons.Outlined.BatteryFull,
                    label = "Battery",
                    value = battery,
                    modifier = Modifier.weight(1f),
                )
                ADDeviceMetric(
                    icon = Icons.Outlined.Storage,
                    label = "Storage",
                    value = storage,
                    modifier = Modifier.weight(1f),
                )
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
        modifier = modifier.heightIn(min = 76.dp),
        shape = RoundedCornerShape(20.dp),
        color = ADSettingsPalette.DeviceMetric,
        contentColor = ADSettingsPalette.DeviceInk,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(21.dp))
            Column(Modifier.padding(start = 9.dp).weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = ADSettingsPalette.DeviceMuted,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ADSettingsGroup(
    title: String,
    containerColor: Color,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 5.dp),
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = containerColor,
            tonalElevation = 0.dp,
        ) {
            Column(Modifier.padding(horizontal = 15.dp), content = content)
        }
    }
}

@Composable
private fun ADSettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: ADSettingsAccent,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(44.dp),
            shape = RoundedCornerShape(14.dp),
            color = accent.container,
            contentColor = accent.content,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            }
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
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
            tint = accent.content.copy(alpha = 0.78f),
        )
    }
}

@Composable
private fun ADSettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 56.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f),
    )
}

private data class ADSettingsAccent(
    val container: Color,
    val content: Color,
)

private object ADSettingsPalette {
    val DeviceCard = Color(0xFFEAF2FF)
    val DeviceMetric = Color(0xFFF5F8FF)
    val DeviceIcon = Color(0xFFD7E6FF)
    val DeviceInk = Color(0xFF23456F)
    val DeviceMuted = Color(0xFF627894)

    val WarmGroup = Color(0xFFFFFBF5)
    val CoolGroup = Color(0xFFF7FAFF)
    val ProductGroup = Color(0xFFFBF8FF)

    val Privacy = ADSettingsAccent(Color(0xFFFFE8ED), Color(0xFF9C4052))
    val Storage = ADSettingsAccent(Color(0xFFFFEEDC), Color(0xFF9A5A1E))
    val Language = ADSettingsAccent(Color(0xFFE7EEFF), Color(0xFF385A9B))
    val Permissions = ADSettingsAccent(Color(0xFFE2F5F1), Color(0xFF2D7167))
    val Advanced = ADSettingsAccent(Color(0xFFEDE6FF), Color(0xFF6748A5))
    val About = ADSettingsAccent(Color(0xFFF3E8FF), Color(0xFF78479A))
}

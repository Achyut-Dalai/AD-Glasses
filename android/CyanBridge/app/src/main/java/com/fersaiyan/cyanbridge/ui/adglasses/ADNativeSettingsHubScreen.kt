package com.fersaiyan.cyanbridge.ui.adglasses

import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Info
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
        ADScreenIntro(
            eyebrow = "Control room",
            title = "Make AD Glasses yours",
            detail = "Privacy, storage and phone access stay close. Android-level controls are kept separate so the page stays easy to scan.",
        )

        ADSettingsDeviceOverview(
            state = state,
            presentation = presentation,
            onClick = onDevice,
        )

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ADSectionTitle("Essentials")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ADSettingsTile(
                    glyph = ADGlyph.PRIVACY,
                    title = "Privacy",
                    detail = "Data & safety",
                    modifier = Modifier.weight(1f),
                    onClick = onPrivacy,
                )
                ADSettingsTile(
                    glyph = ADGlyph.STORAGE,
                    title = "Storage",
                    detail = "Space on phone",
                    modifier = Modifier.weight(1f),
                    onClick = onStorage,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ADSettingsTile(
                    glyph = ADGlyph.LANGUAGE,
                    title = "Language",
                    detail = "App language",
                    modifier = Modifier.weight(1f),
                    onClick = onLanguage,
                )
                ADSettingsTile(
                    glyph = ADGlyph.PERMISSIONS,
                    title = "Permissions",
                    detail = "Access & services",
                    modifier = Modifier.weight(1f),
                    onClick = onPermissions,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ADSectionTitle("System")
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = ADColors.Surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
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
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ADSettingsWideAction(
                        icon = Icons.Outlined.Info,
                        title = "About AD Glasses",
                        subtitle = "Version and product information",
                        onClick = onAbout,
                    )
                }
            }
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
        else -> "Ready to reconnect"
    }

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        color = ADColors.Ink,
        contentColor = ADColors.Surface,
    ) {
        Column(Modifier.padding(17.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(50.dp),
                    shape = RoundedCornerShape(17.dp),
                    color = ADColors.Surface.copy(alpha = 0.13f),
                    contentColor = ADColors.Surface,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        ADGlyphIcon(ADGlyph.DEVICE, ADColors.Surface, Modifier.size(27.dp))
                    }
                }
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
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
                            Modifier.size(7.dp).background(
                                if (presentation.connected) ADColors.Success else ADColors.Surface.copy(alpha = 0.45f),
                                CircleShape,
                            ),
                        )
                        Text(
                            status,
                            modifier = Modifier.padding(start = 7.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = ADColors.Surface.copy(alpha = 0.68f),
                        )
                    }
                }
                Surface(shape = CircleShape, color = ADColors.Surface.copy(alpha = 0.13f)) {
                    Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            if (showBattery || showStorage) {
                Spacer(Modifier.height(15.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
        modifier = modifier.heightIn(min = 60.dp),
        shape = RoundedCornerShape(17.dp),
        color = ADColors.Surface.copy(alpha = 0.11f),
        contentColor = ADColors.Surface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Column(Modifier.padding(start = 8.dp).weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = ADColors.Surface.copy(alpha = 0.58f))
                Text(value, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ADSettingsTile(
    glyph: ADGlyph,
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 140.dp),
        shape = RoundedCornerShape(26.dp),
        color = ADColors.Surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(15.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(48.dp).background(ADColors.SurfaceSubtle, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    ADGlyphIcon(glyph, ADColors.Ink, Modifier.size(27.dp))
                }
                Spacer(Modifier.weight(1f))
                Surface(shape = CircleShape, color = ADColors.SurfaceSubtle) {
                    Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = ADColors.Muted,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                }
            }
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Spacer(Modifier.height(3.dp))
                Text(detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted, maxLines = 1)
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 74.dp)
            .background(ADColors.Surface, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(42.dp).background(ADColors.SurfaceSubtle, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = ADColors.Ink, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.padding(start = 11.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
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
            modifier = Modifier.size(19.dp),
        )
    }
}

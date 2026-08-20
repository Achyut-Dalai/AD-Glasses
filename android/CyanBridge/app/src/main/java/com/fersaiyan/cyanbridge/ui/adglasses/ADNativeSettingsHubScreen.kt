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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fersaiyan.cyanbridge.devices.DeviceProfileStore
import com.fersaiyan.cyanbridge.shared.glasses.GlassesDashboardUiState

@Composable
internal fun ADNativeSettingsHubScreen(
    state: GlassesDashboardUiState,
    wallpaper: ADWallpaperStyle,
    onWallpaperChange: (ADWallpaperStyle) -> Unit,
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
            eyebrow = "SYSTEM",
            title = "Settings",
            detail = "Glasses, privacy, storage and appearance.",
        )

        ADSettingsDeviceOverview(
            state = state,
            presentation = presentation,
            onClick = onDevice,
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ADSectionTitle("Wallpaper")
            ADWallpaperPicker(wallpaper, onWallpaperChange)
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ADSectionTitle("Essentials")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ADSettingsTile(ADGlyph.PRIVACY, "Privacy", "Data & safety", Modifier.weight(1f), onPrivacy)
                ADSettingsTile(ADGlyph.STORAGE, "Storage", "Phone space", Modifier.weight(1f), onStorage)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ADSettingsTile(ADGlyph.LANGUAGE, "Language", "App language", Modifier.weight(1f), onLanguage)
                ADSettingsTile(ADGlyph.PERMISSIONS, "Permissions", "Access", Modifier.weight(1f), onPermissions)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ADSectionTitle("System")
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = ADColors.Surface.copy(alpha = 0.88f),
                border = BorderStroke(1.dp, ADColors.Outline),
            ) {
                Column(Modifier.padding(horizontal = 12.dp)) {
                    ADSettingsWideAction(
                        icon = Icons.Outlined.Settings,
                        title = "Android app settings",
                        subtitle = "Battery, permissions and system controls",
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
                    HorizontalDivider(color = ADColors.Separator)
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
        shape = RoundedCornerShape(18.dp),
        color = Color.Black.copy(alpha = 0.42f),
        border = BorderStroke(1.dp, ADColors.Outline),
        contentColor = ADColors.Ink,
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .background(ADColors.SurfaceSubtle, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    ADGlyphIcon(
                        glyph = ADGlyph.DEVICE,
                        tint = ADColors.Ink,
                        modifier = Modifier.size(40.dp),
                        accent = ADColors.Red,
                    )
                }
                Column(Modifier.padding(start = 11.dp).weight(1f)) {
                    Text(
                        presentation.identityLabel ?: "Your glasses",
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(6.dp).background(
                                when {
                                    presentation.connected -> ADColors.Success
                                    presentation.connecting -> ADColors.Warning
                                    else -> ADColors.Red
                                },
                                CircleShape,
                            ),
                        )
                        Text(
                            status,
                            modifier = Modifier.padding(start = 6.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = ADColors.Muted,
                        )
                    }
                }
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = ADColors.Muted,
                    modifier = Modifier.size(18.dp),
                )
            }

            if (showBattery || showStorage) {
                Spacer(Modifier.height(11.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    if (showBattery) {
                        ADDeviceMetric("BATTERY", "${state.batteryPercent}%", Modifier.weight(1f))
                    }
                    if (showStorage) {
                        ADDeviceMetric("STORAGE", state.storageLabel, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ADDeviceMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.heightIn(min = 45.dp),
        shape = RoundedCornerShape(11.dp),
        color = ADColors.SurfaceSubtle.copy(alpha = 0.78f),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = ADColors.Muted)
            Text(value, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ADWallpaperPicker(
    selected: ADWallpaperStyle,
    onSelected: (ADWallpaperStyle) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        ADWallpaperStyle.entries.forEach { style ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelected(style) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(ADColors.Background)
                        .then(
                            if (selected == style) {
                                Modifier.background(ADColors.Red.copy(alpha = 0.10f))
                            } else Modifier
                        ),
                ) {
                    ADWallpaperCanvas(style, Modifier.fillMaxSize())
                    if (selected == style) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(6.dp)
                                .background(ADColors.Red, CircleShape),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(style.label, style = MaterialTheme.typography.labelSmall, color = if (selected == style) ADColors.Ink else ADColors.Muted)
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
        modifier = modifier.heightIn(min = 94.dp),
        shape = RoundedCornerShape(14.dp),
        color = ADColors.Surface.copy(alpha = 0.88f),
        border = BorderStroke(1.dp, ADColors.Outline),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            ADGlyphIcon(glyph, ADColors.Ink, Modifier.size(23.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ADSettingsWideAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(34.dp).background(ADColors.SurfaceSubtle, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = ADColors.Ink, modifier = Modifier.size(17.dp))
        }
        Column(Modifier.padding(start = 10.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
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
            modifier = Modifier.size(17.dp),
        )
    }
}

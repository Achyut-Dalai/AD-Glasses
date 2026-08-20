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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Settings
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
    onPrivacy: () -> Unit,
    onStorage: () -> Unit,
    onLanguage: () -> Unit,
    onPermissions: () -> Unit,
    onFirmware: () -> Unit,
    onAbout: () -> Unit,
) {
    val context = LocalContext.current
    val presentation = buildADDevicePresentation(
        state = state,
        profile = DeviceProfileStore.loadLastSelected(context),
    )

    ADPageLayout(onBack = onBack) {
        ADSettingsDeviceOverview(state, presentation)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ADSectionTitle("Essentials")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ADSettingsTile(ADGlyph.PRIVACY, "Privacy", Modifier.weight(1f), onClick = onPrivacy)
                ADSettingsTile(ADGlyph.STORAGE, "Storage", Modifier.weight(1f), onClick = onStorage)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ADSettingsIconTile(Icons.Outlined.Language, "Language", Modifier.weight(1f), onClick = onLanguage)
                ADSettingsTile(ADGlyph.PERMISSIONS, "Permissions", Modifier.weight(1f), onClick = onPermissions)
            }
        }

        ADWallpaperPicker()

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ADSectionTitle("System")
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = ADColors.Surface,
                contentColor = ADColors.Ink,
                border = BorderStroke(1.dp, ADColors.Outline),
            ) {
                Column(Modifier.padding(horizontal = 12.dp)) {
                    ADSettingsGlyphWideAction(
                        glyph = ADGlyph.FIRMWARE,
                        title = "Firmware",
                        onClick = onFirmware,
                    )
                    HorizontalDivider(color = ADColors.Separator)
                    ADSettingsWideAction(
                        icon = Icons.Outlined.Settings,
                        title = "Android app settings",
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
) {
    val showBattery = presentation.connected && state.showBattery && state.batteryPercent != null
    val showStorage = presentation.connected && state.showStorage && state.storageLabel != "--"
    val status = when {
        presentation.connected -> "Connected"
        presentation.connecting -> "Connecting…"
        presentation.shouldOpenSetup -> "Not connected"
        else -> "Ready"
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = ADColors.Surface,
        border = BorderStroke(1.dp, ADColors.Outline),
        contentColor = ADColors.Ink,
    ) {
        Column(Modifier.padding(13.dp)) {
            Text(
                presentation.identityLabel ?: "Your glasses",
                style = MaterialTheme.typography.titleLarge,
                color = ADColors.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.size(3.dp))
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
                    style = MaterialTheme.typography.labelSmall,
                    color = ADColors.Muted,
                )
            }

            if (showBattery || showStorage) {
                Spacer(Modifier.size(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    if (showBattery) ADDeviceMetric("BATTERY", "${state.batteryPercent}%", Modifier.weight(1f))
                    if (showStorage) ADDeviceMetric("STORAGE", state.storageLabel, Modifier.weight(1f))
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
        color = ADColors.SurfaceSubtle,
        contentColor = ADColors.Ink,
        border = BorderStroke(1.dp, ADColors.Separator),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = ADColors.InkSoft)
            Text(value, style = MaterialTheme.typography.labelLarge, color = ADColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ADSettingsTile(
    glyph: ADGlyph,
    title: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 86.dp),
        shape = RoundedCornerShape(14.dp),
        color = ADColors.Surface,
        contentColor = ADColors.Ink,
        border = BorderStroke(1.dp, ADColors.Outline),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            ADGlyphIcon(glyph, ADColors.Ink, Modifier.size(24.dp))
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelLarge.copy(fontFamily = ADTechFontFamily),
                color = ADColors.Ink,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ADSettingsIconTile(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 86.dp),
        shape = RoundedCornerShape(14.dp),
        color = ADColors.Surface,
        contentColor = ADColors.Ink,
        border = BorderStroke(1.dp, ADColors.Outline),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Surface(
                modifier = Modifier.size(32.dp),
                shape = RoundedCornerShape(9.dp),
                color = ADColors.SurfaceSubtle,
                contentColor = ADColors.Ink,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp))
                }
            }
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = ADColors.Ink,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ADSettingsGlyphWideAction(
    glyph: ADGlyph,
    title: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(32.dp).background(ADColors.SurfaceSubtle, RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center,
        ) {
            ADGlyphIcon(glyph, ADColors.Ink, Modifier.size(17.dp))
        }
        Text(
            title,
            modifier = Modifier.padding(start = 10.dp).weight(1f),
            style = MaterialTheme.typography.titleMedium,
            color = ADColors.Ink,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        ADGlyphIcon(ADGlyph.NEXT, ADColors.Muted, Modifier.size(17.dp))
    }
}

@Composable
private fun ADSettingsWideAction(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(32.dp).background(ADColors.SurfaceSubtle, RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = ADColors.Ink, modifier = Modifier.size(16.dp))
        }
        Text(
            title,
            modifier = Modifier.padding(start = 10.dp).weight(1f),
            style = MaterialTheme.typography.titleMedium,
            color = ADColors.Ink,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        ADGlyphIcon(ADGlyph.NEXT, ADColors.Muted, Modifier.size(17.dp))
    }
}

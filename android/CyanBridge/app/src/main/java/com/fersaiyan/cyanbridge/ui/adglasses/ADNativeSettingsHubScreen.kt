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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
        ADScreenIntro(
            eyebrow = "Control",
            title = "Settings",
            detail = "Your glasses, privacy, storage and system controls in one place.",
        )

        ADSettingsDeviceOverview(state, presentation)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ADSectionTitle("Essentials")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ADSettingsTile(ADMatrixGlyph.PRIVACY, "Privacy", Modifier.weight(1f), onClick = onPrivacy)
                ADSettingsTile(ADMatrixGlyph.STORAGE, "Storage", Modifier.weight(1f), onClick = onStorage)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ADSettingsTile(ADMatrixGlyph.LANGUAGE, "Language", Modifier.weight(1f), onClick = onLanguage)
                ADSettingsTile(ADMatrixGlyph.PERMISSIONS, "Permissions", Modifier.weight(1f), onClick = onPermissions)
            }
        }

        ADWallpaperPicker()

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ADSectionTitle("System")
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(15.dp),
                color = ADColors.Surface,
                contentColor = ADColors.Ink,
                border = BorderStroke(1.dp, ADColors.Outline),
            ) {
                Column(Modifier.padding(horizontal = 12.dp)) {
                    ADSettingsWideAction(
                        glyph = ADMatrixGlyph.FIRMWARE,
                        title = "Firmware",
                        detail = "Updates and recovery",
                        onClick = onFirmware,
                    )
                    HorizontalDivider(color = ADColors.Separator)
                    ADSettingsWideAction(
                        glyph = ADMatrixGlyph.SETTINGS,
                        title = "Android app settings",
                        detail = "Notifications, permissions and system controls",
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
                        glyph = ADMatrixGlyph.INFO,
                        title = "About AD Glasses",
                        detail = "Version, product and open-source information",
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
    val statusColor = when {
        presentation.connected -> ADColors.Success
        presentation.connecting -> ADColors.Warning
        else -> ADColors.Red
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = ADColors.Surface,
        border = BorderStroke(1.dp, ADColors.Outline),
        contentColor = ADColors.Ink,
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(ADColors.SurfaceSubtle, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    ADMatrixGlyphIcon(
                        ADMatrixGlyph.LENS,
                        ADColors.Ink,
                        Modifier.size(23.dp),
                        accent = if (presentation.connected) ADColors.Red else null,
                    )
                }
                Column(Modifier.padding(start = 11.dp).weight(1f)) {
                    Text(
                        presentation.identityLabel ?: "Your glasses",
                        style = MaterialTheme.typography.titleLarge,
                        color = ADColors.Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.size(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).background(statusColor, CircleShape))
                        Text(
                            status,
                            modifier = Modifier.padding(start = 6.dp),
                            style = ADMetaTextStyle,
                            color = ADColors.Muted,
                        )
                    }
                }
            }

            if (showBattery || showStorage) {
                Spacer(Modifier.size(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    if (showBattery) ADDeviceMetric("Battery", "${state.batteryPercent}%", Modifier.weight(1f))
                    if (showStorage) ADDeviceMetric("Storage", state.storageLabel, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ADDeviceMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.heightIn(min = 49.dp),
        shape = RoundedCornerShape(11.dp),
        color = ADColors.SurfaceSubtle,
        contentColor = ADColors.Ink,
        border = BorderStroke(1.dp, ADColors.Separator),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(label.uppercase(), style = ADMetaTextStyle, color = ADColors.InkSoft)
            Spacer(Modifier.size(2.dp))
            Text(
                value,
                style = MaterialTheme.typography.labelLarge,
                color = ADColors.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ADSettingsTile(
    glyph: ADMatrixGlyph,
    title: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 92.dp),
        shape = RoundedCornerShape(15.dp),
        color = ADColors.Surface,
        contentColor = ADColors.Ink,
        border = BorderStroke(1.dp, ADColors.Outline),
    ) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier.size(34.dp).background(ADColors.SurfaceSubtle, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                ADMatrixGlyphIcon(glyph, ADColors.Ink, Modifier.size(20.dp))
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
private fun ADSettingsWideAction(
    glyph: ADMatrixGlyph,
    title: String,
    detail: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 62.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(34.dp).background(ADColors.SurfaceSubtle, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            ADMatrixGlyphIcon(glyph, ADColors.Ink, Modifier.size(19.dp))
        }
        Column(Modifier.padding(start = 10.dp).weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = ADColors.Ink,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = ADColors.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        ADMatrixGlyphIcon(ADMatrixGlyph.NEXT, ADColors.Muted, Modifier.size(17.dp))
    }
}

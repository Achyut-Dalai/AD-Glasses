package com.fersaiyan.cyanbridge.ui.adglasses

import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
            detail = "Glasses, privacy, storage and system controls.",
        )

        ADSettingsDeviceOverview(state, presentation, onDevice)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ADSectionTitle("Essentials")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ADSettingsTile(ADGlyph.PRIVACY, "Privacy", "Data & safety", Modifier.weight(1f), onClick = onPrivacy)
                ADSettingsTile(ADGlyph.STORAGE, "Storage", "Phone space", Modifier.weight(1f), onClick = onStorage)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ADSettingsTile(ADGlyph.LANGUAGE, "Language", "App language", Modifier.weight(1f), onClick = onLanguage)
                ADSettingsTile(ADGlyph.PERMISSIONS, "Permissions", "Access", Modifier.weight(1f), onClick = onPermissions)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ADSectionTitle("System")
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = ADColors.Surface,
                border = BorderStroke(1.dp, ADColors.Outline),
            ) {
                Column(Modifier.padding(horizontal = 12.dp)) {
                    ADSettingsWideAction(
                        glyph = ADGlyph.SETTINGS,
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
                        glyph = ADGlyph.INFO,
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
        color = ADColors.Surface,
        border = BorderStroke(1.dp, ADColors.Outline),
        contentColor = ADColors.Ink,
    ) {
        Column(Modifier.padding(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.width(86.dp).height(56.dp).background(Color.Black, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ad_glasses_hero_v4),
                        contentDescription = "AD Glasses",
                        modifier = Modifier.fillMaxSize().padding(3.dp),
                        contentScale = ContentScale.Fit,
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
                ADGlyphIcon(ADGlyph.NEXT, ADColors.Muted, Modifier.size(18.dp))
            }

            if (showBattery || showStorage) {
                Spacer(Modifier.height(10.dp))
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
    detail: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 94.dp),
        shape = RoundedCornerShape(14.dp),
        color = ADColors.Surface,
        border = BorderStroke(1.dp, ADColors.Outline),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            ADGlyphIcon(glyph, ADColors.Ink, Modifier.size(25.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, color = ADColors.Ink, maxLines = 1)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ADSettingsWideAction(
    glyph: ADGlyph,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(onClick = onClick).padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(34.dp).background(ADColors.SurfaceSubtle, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            ADGlyphIcon(glyph, ADColors.Ink, Modifier.size(18.dp))
        }
        Column(Modifier.padding(start = 10.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = ADColors.Ink, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        ADGlyphIcon(ADGlyph.NEXT, ADColors.Muted, Modifier.size(17.dp))
    }
}

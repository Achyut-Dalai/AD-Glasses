package com.fersaiyan.cyanbridge.ui.adglasses

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeveloperMode
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
    onAdvanced: () -> Unit,
    onAbout: () -> Unit,
) {
    val context = LocalContext.current
    val presentation = buildADDevicePresentation(
        state = state,
        profile = DeviceProfileStore.loadLastSelected(context),
    )

    ADPageLayout("Settings", onBack) {
        ADCard(onClick = onDevice) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.ad_glasses_hero_v4),
                    contentDescription = "Glasses",
                    modifier = Modifier.size(width = 72.dp, height = 38.dp),
                    contentScale = ContentScale.Fit,
                )
                Column(Modifier.padding(start = 13.dp).weight(1f)) {
                    Text(
                        presentation.identityLabel ?: "Glasses",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        presentation.statusLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = ADColors.Muted,
                    )
                }
            }
        }

        ADNativeSettingsGroup("Privacy and data") {
            ADSettingsRow(Icons.Outlined.PrivacyTip, "Privacy", "Transcripts, redaction and exports", onPrivacy)
            ADNativeSettingsDivider()
            ADSettingsRow(Icons.Outlined.Storage, "Storage", "App data and media synced from the glasses", onStorage)
        }

        ADNativeSettingsGroup("General") {
            ADSettingsRow(Icons.Outlined.Language, "Language", "App language and system locale", onLanguage)
            ADNativeSettingsDivider()
            ADSettingsRow(
                Icons.Outlined.Security,
                "Permissions",
                "Camera, microphone, Bluetooth and nearby devices",
                onPermissions,
            )
        }

        ADNativeSettingsGroup("AD Glasses") {
            ADSettingsRow(Icons.Outlined.DeveloperMode, "Advanced", "Diagnostics and system controls", onAdvanced)
            ADNativeSettingsDivider()
            ADSettingsRow(Icons.Outlined.Info, "About AD Glasses", "Version and product information", onAbout)
        }
    }
}

@Composable
private fun ADNativeSettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = ADColors.Muted,
            modifier = Modifier.padding(start = 4.dp, bottom = 7.dp),
        )
        ADCard {
            Column(Modifier.fillMaxWidth(), content = content)
        }
    }
}

@Composable
private fun ADNativeSettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 48.dp),
        color = ADColors.Separator,
    )
}

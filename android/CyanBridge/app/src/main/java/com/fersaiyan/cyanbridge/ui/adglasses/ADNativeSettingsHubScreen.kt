package com.fersaiyan.cyanbridge.ui.adglasses

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeveloperMode
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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

    ADProductPage("Settings", onBack) {
        ADCard(onClick = onDevice) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ADGlassesMark(Modifier.size(width = 42.dp, height = 28.dp))
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

        ADProductSettingsGroup("Privacy and data") {
            ADSettingsRow(Icons.Outlined.PrivacyTip, "Privacy", "Transcripts, redaction and exports", onPrivacy)
            ADProductDivider()
            ADSettingsRow(Icons.Outlined.Storage, "Storage", "App data and media synced from the glasses", onStorage)
        }

        ADProductSettingsGroup("General") {
            ADSettingsRow(Icons.Outlined.Language, "Language", "App language and system locale", onLanguage)
            ADProductDivider()
            ADSettingsRow(
                Icons.Outlined.Security,
                "Permissions",
                "Camera, microphone, Bluetooth and nearby devices",
                onPermissions,
            )
        }

        ADProductSettingsGroup("AD Glasses") {
            ADSettingsRow(Icons.Outlined.DeveloperMode, "Advanced", "Diagnostics and system controls", onAdvanced)
            ADProductDivider()
            ADSettingsRow(Icons.Outlined.Info, "About AD Glasses", "Version and product information", onAbout)
        }
    }
}

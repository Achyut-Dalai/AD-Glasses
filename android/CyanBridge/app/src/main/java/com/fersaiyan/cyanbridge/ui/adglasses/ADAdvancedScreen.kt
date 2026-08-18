package com.fersaiyan.cyanbridge.ui.adglasses

import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeveloperMode
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Owner-facing diagnostics entry points. Experimental product surfaces stay out of normal UI. */
@Composable
internal fun ADAdvancedScreen(
    themeStyle: ADThemeStyle,
    onThemeStyleChanged: (ADThemeStyle) -> Unit,
    onBack: () -> Unit,
    onDevice: () -> Unit,
) {
    val context = LocalContext.current
    val dark = themeStyle == ADThemeStyle.DARK_MONOCHROME

    fun openSystemSettings() {
        runCatching {
            context.startActivity(
                Intent(
                    AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}"),
                ),
            )
        }
    }

    ADPageLayout("Advanced", onBack) {
        ADPageHero(
            icon = Icons.Outlined.DeveloperMode,
            title = "Owner tools",
            detail = "Appearance, deep device diagnostics and Android system controls live here so the everyday app can stay simple.",
            status = "ADVANCED",
        )

        Column {
            ADSectionEyebrow("Appearance")
            Spacer(Modifier.height(9.dp))
            ADCard {
                ADSettingsRow(
                    icon = Icons.Outlined.DarkMode,
                    title = "Dark Monochrome",
                    subtitle = if (dark) "Dark surfaces with frost-white product accents" else "Use the dark inverse of Monochrome",
                    onClick = {
                        onThemeStyleChanged(
                            if (dark) ADThemeStyle.MONOCHROME else ADThemeStyle.DARK_MONOCHROME,
                        )
                    },
                    trailing = {
                        Switch(
                            checked = dark,
                            onCheckedChange = { enabled ->
                                onThemeStyleChanged(
                                    if (enabled) ADThemeStyle.DARK_MONOCHROME else ADThemeStyle.MONOCHROME,
                                )
                            },
                        )
                    },
                )
            }
        }

        Column {
            ADSectionEyebrow("Diagnostics")
            Spacer(Modifier.height(9.dp))
            ADCard {
                ADSettingsRow(
                    icon = Icons.Outlined.Bluetooth,
                    title = "Device diagnostics",
                    subtitle = "Connection state, sync, firmware and recovery tools",
                    onClick = onDevice,
                )
            }
        }

        Column {
            ADSectionEyebrow("Android")
            Spacer(Modifier.height(9.dp))
            ADCard {
                ADSettingsRow(
                    icon = Icons.Outlined.Settings,
                    title = "App system settings",
                    subtitle = "Permissions, battery behavior and Android-level controls",
                    onClick = ::openSystemSettings,
                )
            }
        }

        Text(
            "Theme changes affect AD Glasses only. Advanced Android controls can affect background behavior, but they do not change glasses firmware or device protocol by themselves.",
            style = MaterialTheme.typography.bodyMedium,
            color = ADColors.Muted,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}

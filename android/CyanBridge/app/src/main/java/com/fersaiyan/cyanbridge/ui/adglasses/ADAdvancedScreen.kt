package com.fersaiyan.cyanbridge.ui.adglasses

import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.DeveloperMode
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Owner-facing diagnostics entry points. Experimental product surfaces stay out of normal UI. */
@Composable
internal fun ADAdvancedScreen(
    onBack: () -> Unit,
    onDevice: () -> Unit,
) {
    val context = LocalContext.current

    ADPageLayout("Advanced", onBack) {
        ADPageHero(
            icon = Icons.Outlined.DeveloperMode,
            title = "Owner tools",
            detail = "Deep device diagnostics and Android system controls live here so the everyday app can stay simple.",
            status = "ADVANCED",
        )

        Column {
            ADSectionEyebrow("Diagnostics")
            Spacer(androidx.compose.ui.Modifier.height(9.dp))
            ADCard(onClick = onDevice) {
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
            Spacer(androidx.compose.ui.Modifier.height(9.dp))
            ADCard(
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
            ) {
                ADSettingsRow(
                    icon = Icons.Outlined.Settings,
                    title = "App system settings",
                    subtitle = "Permissions, battery behavior and Android-level controls",
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
            }
        }

        Text(
            "These controls can affect how AD behaves in the background, but they do not change glasses firmware or device protocol by themselves.",
            style = MaterialTheme.typography.bodyMedium,
            color = ADColors.Muted,
        )
    }
}

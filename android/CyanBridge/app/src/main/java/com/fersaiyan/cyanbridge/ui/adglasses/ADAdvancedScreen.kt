package com.fersaiyan.cyanbridge.ui.adglasses

import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
        Text(
            "Technical controls for connection troubleshooting and Android-level app behavior.",
            style = MaterialTheme.typography.bodyMedium,
            color = ADColors.Muted,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
        ADCard {
            ADSettingsRow(
                icon = Icons.Outlined.Bluetooth,
                title = "Device diagnostics",
                subtitle = "Connection, sync, firmware and recovery tools",
                onClick = onDevice,
            )
            HorizontalDivider(Modifier.padding(start = 52.dp), color = ADColors.Separator)
            ADSettingsRow(
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
        }
    }
}

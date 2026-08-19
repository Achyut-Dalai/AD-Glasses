package com.fersaiyan.cyanbridge.ui.adglasses

import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/** Owner-facing Android controls. Product diagnostics live in Device Center. */
@Composable
internal fun ADAdvancedScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current

    ADPageLayout("Advanced", onBack) {
        ADCard {
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

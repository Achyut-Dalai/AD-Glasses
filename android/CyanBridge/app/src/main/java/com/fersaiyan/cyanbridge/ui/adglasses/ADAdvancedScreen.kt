package com.fersaiyan.cyanbridge.ui.adglasses

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
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

    Column(Modifier.fillMaxSize()) {
        ADTopBar(title = "Advanced", showBack = true, onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            ADCard {
                ADSettingsRow(
                    icon = Icons.Outlined.Bluetooth,
                    title = "Device diagnostics",
                    subtitle = "Connection, sync, firmware and recovery tools",
                    onClick = onDevice,
                )
                HorizontalDivider(Modifier.padding(start = 48.dp), color = ADColors.Separator)
                ADSettingsRow(
                    icon = Icons.Outlined.Settings,
                    title = "Android app settings",
                    subtitle = "Permissions, battery and system controls",
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:${context.packageName}"),
                                ),
                            )
                        }
                    },
                )
            }
        }
    }
}

package com.fersaiyan.cyanbridge.ui.adglasses

import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Owner-facing Android controls. Product diagnostics live in Device Center. */
@Composable
internal fun ADAdvancedScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    ADPageLayout("Advanced", onBack) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(13.dp),
            color = ADColors.Surface.copy(alpha = 0.88f),
            border = BorderStroke(1.dp, ADColors.Outline),
        ) {
            Column(Modifier.padding(horizontal = 10.dp)) {
                ADSettingsRow(
                    icon = Icons.Outlined.Settings,
                    title = "Android app settings",
                    subtitle = "Permissions, battery, notifications and system controls",
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
            "Device health and firmware stay in Device Center. This page is only for Android-owned controls.",
            style = MaterialTheme.typography.bodySmall,
            color = ADColors.Muted,
        )
    }
}

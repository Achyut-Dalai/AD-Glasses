package com.fersaiyan.cyanbridge.ui.adglasses

import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Owner-facing Android controls. Product diagnostics live in Device Center. */
@Composable
internal fun ADAdvancedScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current

    ADPageLayout("Advanced", onBack) {
        Text(
            "System-level controls that live outside AD Glasses stay here, clearly separated from everyday product settings.",
            style = MaterialTheme.typography.bodyMedium,
            color = ADColors.Muted,
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(30.dp),
            color = ADColors.Ink,
            contentColor = ADColors.Surface,
        ) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(56.dp),
                        shape = RoundedCornerShape(19.dp),
                        color = ADColors.Surface.copy(alpha = 0.13f),
                        contentColor = ADColors.Surface,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Outlined.Settings,
                                contentDescription = null,
                                modifier = Modifier.size(27.dp),
                            )
                        }
                    }
                    Column(Modifier.padding(start = 13.dp).weight(1f)) {
                        Text(
                            "ANDROID CONTROL",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = ADTechFontFamily),
                            color = ADColors.Surface.copy(alpha = 0.58f),
                        )
                        Spacer(Modifier.size(2.dp))
                        Text(
                            "System settings",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Spacer(Modifier.size(16.dp))
                Text(
                    "Battery rules, notifications, permissions and other Android-owned controls can be managed without mixing them into glasses features.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ADColors.Surface.copy(alpha = 0.68f),
                )
            }
        }

        Column {
            ADSectionTitle("System")
            Spacer(Modifier.size(9.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = ADColors.Surface,
            ) {
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

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = ADColors.SurfaceSubtle,
        ) {
            Text(
                "Device health and firmware tools stay in Device Center so this page remains reserved for Android-owned controls.",
                modifier = Modifier.padding(14.dp),
                style = MaterialTheme.typography.bodySmall,
                color = ADColors.Muted,
            )
        }
    }
}

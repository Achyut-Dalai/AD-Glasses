package com.fersaiyan.cyanbridge.ui.adglasses

import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Owner-facing Android controls. Product diagnostics live in Device Center. */
@Composable
internal fun ADAdvancedScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    ADPageLayout("Advanced", onBack) {
        ADScreenIntro(
            eyebrow = "System",
            title = "Advanced",
            detail = "Android-owned controls that sit outside the normal AD Glasses product surface.",
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = ADColors.Surface,
            border = BorderStroke(1.dp, ADColors.Outline),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .clickable {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:${context.packageName}"),
                                ),
                            )
                        }
                    }
                    .padding(horizontal = 11.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(34.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = ADColors.SurfaceSubtle,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Android, contentDescription = null, tint = ADColors.Ink, modifier = Modifier.size(19.dp))
                    }
                }
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Text("Android app settings", style = MaterialTheme.typography.titleMedium, color = ADColors.Ink)
                    Text(
                        "Permissions, battery, notifications and system controls",
                        style = MaterialTheme.typography.bodySmall,
                        color = ADColors.Muted,
                    )
                }
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = ADColors.Muted, modifier = Modifier.size(17.dp))
            }
        }

        Text(
            "Device health and firmware stay in the AD Glasses product pages. Advanced is intentionally small.",
            style = MaterialTheme.typography.bodySmall,
            color = ADColors.Muted,
        )
    }
}

package com.fersaiyan.cyanbridge.ui.adglasses

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.util.Locale

/** Compatibility aliases retained for internal callers; routed product UI uses the refined surfaces. */
@Composable
internal fun ADPrivacyCenterScreen(onBack: () -> Unit) = ADPrivacyCenterScreenRefined(onBack)

@Composable
internal fun ADStorageScreen(onBack: () -> Unit) = ADStorageScreenRefined(onBack)

@Composable
internal fun ADLanguageScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val currentLanguage = Locale.getDefault().displayLanguage

    ADPageLayout("Language", onBack) {
        Text(
            "AD Glasses follows Android’s app-language setting so the choice stays familiar and consistent with the rest of your phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = ADColors.Muted,
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(30.dp),
            color = ADColors.Ink,
            contentColor = ADColors.Surface,
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = RoundedCornerShape(19.dp),
                    color = ADColors.Surface.copy(alpha = 0.13f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        ADGlyphIcon(ADGlyph.LANGUAGE, ADColors.Surface, Modifier.size(31.dp))
                    }
                }
                Column(Modifier.padding(start = 13.dp).weight(1f)) {
                    Text("App language", style = MaterialTheme.typography.labelSmall.copy(fontFamily = ADTechFontFamily), color = ADColors.Surface.copy(alpha = 0.58f))
                    Text(currentLanguage, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = ADColors.Surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(Modifier.padding(15.dp)) {
                Text("Managed by Android", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Spacer(Modifier.size(3.dp))
                Text(
                    if (Build.VERSION.SDK_INT >= 33) {
                        "Android can set a language specifically for AD Glasses without changing the rest of your phone."
                    } else {
                        "This Android version uses the phone’s system language for AD Glasses."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = ADColors.Muted,
                )
            }
        }

        ADPrimaryButton(
            text = if (Build.VERSION.SDK_INT >= 33) "Open app languages" else "Open language settings",
            onClick = {
                val intent = if (Build.VERSION.SDK_INT >= 33) {
                    Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.parse("package:${context.packageName}"))
                } else {
                    Intent(Settings.ACTION_LOCALE_SETTINGS)
                }
                runCatching { context.startActivity(intent) }
            },
        )
    }
}

@Composable
internal fun ADPermissionsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val permissions = buildList {
        add(ADPermissionItem("Microphone", Icons.Outlined.Mic, Manifest.permission.RECORD_AUDIO))
        add(ADPermissionItem("Camera", Icons.Outlined.CameraAlt, Manifest.permission.CAMERA))
        if (Build.VERSION.SDK_INT >= 31) {
            add(ADPermissionItem("Bluetooth", Icons.Outlined.Bluetooth, Manifest.permission.BLUETOOTH_CONNECT))
        }
        if (Build.VERSION.SDK_INT >= 33) {
            add(ADPermissionItem("Nearby devices", Icons.Outlined.Wifi, Manifest.permission.NEARBY_WIFI_DEVICES))
            add(ADPermissionItem("Notifications", Icons.Outlined.Notifications, Manifest.permission.POST_NOTIFICATIONS))
        }
    }
    val grantedCount = permissions.count {
        ContextCompat.checkSelfPermission(context, it.permission) == PackageManager.PERMISSION_GRANTED
    }

    ADPageLayout("Permissions", onBack) {
        Text(
            "Only the access needed for glasses features is shown here. Android remains the source of truth for granting or removing permission.",
            style = MaterialTheme.typography.bodyMedium,
            color = ADColors.Muted,
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(30.dp),
            color = ADColors.Ink,
            contentColor = ADColors.Surface,
        ) {
            Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = RoundedCornerShape(19.dp),
                    color = ADColors.Surface.copy(alpha = 0.13f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        ADGlyphIcon(ADGlyph.PERMISSIONS, ADColors.Surface, Modifier.size(31.dp))
                    }
                }
                Column(Modifier.padding(start = 13.dp).weight(1f)) {
                    Text("ACCESS", style = MaterialTheme.typography.labelSmall.copy(fontFamily = ADTechFontFamily), color = ADColors.Surface.copy(alpha = 0.58f))
                    Text("$grantedCount of ${permissions.size} ready", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ADSectionTitle("Feature access")
            permissions.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { item ->
                        val granted = ContextCompat.checkSelfPermission(context, item.permission) == PackageManager.PERMISSION_GRANTED
                        ADPermissionTile(item = item, granted = granted, modifier = Modifier.weight(1f))
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        ADPrimaryButton(
            text = "Manage permissions",
            onClick = { openAppSettings(context.packageName, context::startActivity) },
        )
    }
}

@Composable
private fun ADPermissionTile(
    item: ADPermissionItem,
    granted: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.heightIn(min = 128.dp),
        shape = RoundedCornerShape(22.dp),
        color = ADColors.Surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(13.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = if (granted) ADColors.Ink else ADColors.SurfaceSubtle,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            item.icon,
                            contentDescription = null,
                            tint = if (granted) ADColors.Surface else ADColors.Ink,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(8.dp).background(if (granted) ADColors.Success else ADColors.Muted.copy(alpha = 0.45f), CircleShape),
                )
            }
            Column {
                Text(item.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(if (granted) "Ready" else "Not granted", style = MaterialTheme.typography.labelSmall, color = ADColors.Muted)
            }
        }
    }
}

@Composable
internal fun ADAboutScreen(onBack: () -> Unit) = ADMinimalAboutScreen(onBack)

private data class ADPermissionItem(
    val title: String,
    val icon: ImageVector,
    val permission: String,
)

private fun openAppSettings(packageName: String, start: (Intent) -> Unit) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
    runCatching { start(intent) }
}

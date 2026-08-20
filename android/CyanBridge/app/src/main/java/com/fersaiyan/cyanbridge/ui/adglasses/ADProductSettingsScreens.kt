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
import com.fersaiyan.cyanbridge.R
import java.util.Locale

@Composable
internal fun ADPrivacyCenterScreen(onBack: () -> Unit) = ADPrivacyCenterScreenRefined(onBack)

@Composable
internal fun ADStorageScreen(onBack: () -> Unit) = ADStorageScreenRefined(onBack)

@Composable
internal fun ADLanguageScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val currentLanguage = Locale.getDefault().displayLanguage

    ADPageLayout("Language", onBack) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = ADColors.Surface,
            border = BorderStroke(1.dp, ADColors.Outline),
        ) {
            Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                ADAssetIcon(R.drawable.ad_codex_language, Modifier.size(36.dp), "App language")
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Text("APP LANGUAGE", style = MaterialTheme.typography.labelSmall, color = ADColors.InkSoft)
                    Text(
                        currentLanguage,
                        style = MaterialTheme.typography.titleLarge,
                        color = ADColors.Ink,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        Text(
            if (Build.VERSION.SDK_INT >= 33) {
                "Android can set a language for AD Glasses without changing the rest of your phone."
            } else {
                "This Android version uses the phone’s system language for AD Glasses."
            },
            style = MaterialTheme.typography.bodySmall,
            color = ADColors.Muted,
        )

        ADPrimaryButton(
            text = if (Build.VERSION.SDK_INT >= 33) "Open app languages" else "Open language settings",
            onClick = {
                val intent = if (Build.VERSION.SDK_INT >= 33) {
                    Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.parse("package:${context.packageName}"))
                } else Intent(Settings.ACTION_LOCALE_SETTINGS)
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
        if (Build.VERSION.SDK_INT >= 31) add(ADPermissionItem("Bluetooth", Icons.Outlined.Bluetooth, Manifest.permission.BLUETOOTH_CONNECT))
        if (Build.VERSION.SDK_INT >= 33) {
            add(ADPermissionItem("Nearby devices", Icons.Outlined.Wifi, Manifest.permission.NEARBY_WIFI_DEVICES))
            add(ADPermissionItem("Notifications", Icons.Outlined.Notifications, Manifest.permission.POST_NOTIFICATIONS))
        }
    }
    val grantedCount = permissions.count {
        ContextCompat.checkSelfPermission(context, it.permission) == PackageManager.PERMISSION_GRANTED
    }

    ADPageLayout("Permissions", onBack) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = ADColors.Surface,
            border = BorderStroke(1.dp, ADColors.Outline),
        ) {
            Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                ADGlyphIcon(ADGlyph.PERMISSIONS, ADColors.Ink, Modifier.size(22.dp), accent = if (grantedCount < permissions.size) ADColors.Red else null)
                Column(Modifier.padding(start = 9.dp).weight(1f)) {
                    Text("ACCESS", style = MaterialTheme.typography.labelSmall, color = ADColors.InkSoft)
                    Text(
                        "$grantedCount of ${permissions.size} ready",
                        style = MaterialTheme.typography.titleLarge,
                        color = ADColors.Ink,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            ADSectionTitle("Feature access")
            permissions.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    pair.forEach { item ->
                        val granted = ContextCompat.checkSelfPermission(context, item.permission) == PackageManager.PERMISSION_GRANTED
                        ADPermissionTile(item, granted, Modifier.weight(1f))
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
private fun ADPermissionTile(item: ADPermissionItem, granted: Boolean, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.heightIn(min = 80.dp),
        shape = RoundedCornerShape(12.dp),
        color = ADColors.Surface,
        border = BorderStroke(1.dp, ADColors.Outline),
    ) {
        Column(modifier = Modifier.padding(9.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(item.icon, null, tint = ADColors.Ink, modifier = Modifier.size(16.dp))
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(5.dp).background(if (granted) ADColors.Ink else ADColors.Red, CircleShape))
            }
            Column {
                Text(item.title, style = MaterialTheme.typography.labelLarge, color = ADColors.Ink, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (granted) "Ready" else "Not granted", style = MaterialTheme.typography.labelSmall, color = ADColors.Muted)
            }
        }
    }
}

@Composable
internal fun ADAboutScreen(onBack: () -> Unit) = ADMinimalAboutScreen(onBack)

private data class ADPermissionItem(val title: String, val icon: ImageVector, val permission: String)

private fun openAppSettings(packageName: String, start: (Intent) -> Unit) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
    runCatching { start(intent) }
}

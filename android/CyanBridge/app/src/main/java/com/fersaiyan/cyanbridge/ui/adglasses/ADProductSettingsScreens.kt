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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Translate
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

@Composable
internal fun ADPrivacyCenterScreen(onBack: () -> Unit) = ADPrivacyCenterScreenRefined(onBack)

@Composable
internal fun ADStorageScreen(onBack: () -> Unit) = ADStorageScreenRefined(onBack)

@Composable
internal fun ADLanguageScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val currentLanguage = Locale.getDefault().displayLanguage

    ADPageLayout("Language", onBack) {
        ADScreenIntro(
            eyebrow = "App language",
            title = currentLanguage,
            detail = if (Build.VERSION.SDK_INT >= 33) {
                "Choose a language for AD Glasses without changing the rest of your phone."
            } else {
                "AD Glasses follows your phone's system language on this Android version."
            },
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(17.dp),
            color = ADColors.Surface,
            contentColor = ADColors.Ink,
            border = BorderStroke(1.dp, ADColors.Outline),
        ) {
            Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(44.dp).background(ADColors.SurfaceSubtle, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Translate, contentDescription = null, tint = ADColors.Ink, modifier = Modifier.size(24.dp))
                }
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Text("CURRENT", style = ADMetaTextStyle, color = ADColors.InkSoft)
                    Spacer(Modifier.size(2.dp))
                    Text(
                        currentLanguage,
                        style = MaterialTheme.typography.titleLarge,
                        color = ADColors.Ink,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = ADColors.Success, modifier = Modifier.size(19.dp))
            }
        }

        ADPrimaryButton(
            text = if (Build.VERSION.SDK_INT >= 33) "Choose app language" else "Open language settings",
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
        add(ADPermissionItem("Microphone", Icons.Outlined.Mic, Manifest.permission.RECORD_AUDIO, "Voice and audio features"))
        add(ADPermissionItem("Camera", Icons.Outlined.CameraAlt, Manifest.permission.CAMERA, "Capture and visual questions"))
        if (Build.VERSION.SDK_INT >= 31) {
            add(ADPermissionItem("Bluetooth", Icons.Outlined.Bluetooth, Manifest.permission.BLUETOOTH_CONNECT, "Glasses connection"))
        }
        if (Build.VERSION.SDK_INT >= 33) {
            add(ADPermissionItem("Nearby devices", Icons.Outlined.Wifi, Manifest.permission.NEARBY_WIFI_DEVICES, "Local media transfer"))
            add(ADPermissionItem("Notifications", Icons.Outlined.Notifications, Manifest.permission.POST_NOTIFICATIONS, "Background activity status"))
        }
    }
    val grantedCount = permissions.count {
        ContextCompat.checkSelfPermission(context, it.permission) == PackageManager.PERMISSION_GRANTED
    }
    val allReady = grantedCount == permissions.size

    ADPageLayout("Permissions", onBack) {
        ADScreenIntro(
            eyebrow = "Access",
            title = if (allReady) "Everything is ready" else "$grantedCount of ${permissions.size} ready",
            detail = "Permissions are requested only for features that need hardware, audio or background access.",
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(17.dp),
            color = ADColors.Surface,
            border = BorderStroke(1.dp, ADColors.Outline),
        ) {
            Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(44.dp).background(ADColors.SurfaceSubtle, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Security,
                        contentDescription = null,
                        tint = ADColors.Ink,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Text("FEATURE ACCESS", style = ADMetaTextStyle, color = ADColors.InkSoft)
                    Text(
                        if (allReady) "Ready for supported features" else "Some features need access",
                        style = MaterialTheme.typography.titleMedium,
                        color = ADColors.Ink,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Box(Modifier.size(7.dp).background(if (allReady) ADColors.Success else ADColors.Red, CircleShape))
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
        modifier = modifier.heightIn(min = 104.dp),
        shape = RoundedCornerShape(13.dp),
        color = ADColors.Surface,
        border = BorderStroke(1.dp, ADColors.Outline),
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(32.dp).background(ADColors.SurfaceSubtle, RoundedCornerShape(9.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(item.icon, contentDescription = null, tint = ADColors.Ink, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(6.dp).background(if (granted) ADColors.Success else ADColors.Red, CircleShape))
            }
            Column {
                Text(
                    item.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = ADColors.Ink,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    item.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = ADColors.Muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
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
    val detail: String,
)

private fun openAppSettings(packageName: String, start: (Intent) -> Unit) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
    runCatching { start(intent) }
}

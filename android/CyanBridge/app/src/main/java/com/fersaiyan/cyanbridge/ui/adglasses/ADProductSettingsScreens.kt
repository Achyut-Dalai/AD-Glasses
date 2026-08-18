package com.fersaiyan.cyanbridge.ui.adglasses

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.fersaiyan.cyanbridge.BuildConfig
import com.fersaiyan.cyanbridge.agent.LocalAgentPrefs
import com.fersaiyan.cyanbridge.media.SyncedMediaFolder
import com.fersaiyan.cyanbridge.privacy.PrivacyPrefs
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun ADPrivacyCenterScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var storeTranscripts by remember { mutableStateOf(PrivacyPrefs.isTranscriptStorageEnabled(context)) }
    var redactNames by remember { mutableStateOf(PrivacyPrefs.isRedactNamesEnabled(context)) }
    var fullExports by remember { mutableStateOf(PrivacyPrefs.isIncludeFullTranscriptionInExportsEnabled(context)) }
    var confirmations by remember { mutableStateOf(LocalAgentPrefs.isRequireConfirmationEnabled(context)) }

    ADPageLayout("Privacy", onBack) {
        ADSettingsDetailGroup("Conversation data") {
            ADToggleRow(Icons.Outlined.Description, "Save transcripts", "Keep supported transcripts on the phone", storeTranscripts) {
                storeTranscripts = it
                PrivacyPrefs.setTranscriptStorageEnabled(context, it)
            }
            ADSettingsDetailDivider()
            ADToggleRow(Icons.Outlined.Lock, "Redact names", "Best-effort name redaction in saved text", redactNames) {
                redactNames = it
                PrivacyPrefs.setRedactNamesEnabled(context, it)
            }
            ADSettingsDetailDivider()
            ADToggleRow(Icons.Outlined.Description, "Full transcript in exports", "Include complete transcription when exporting", fullExports) {
                fullExports = it
                PrivacyPrefs.setIncludeFullTranscriptionInExportsEnabled(context, it)
            }
        }
        ADSettingsDetailGroup("Automation") {
            ADToggleRow(Icons.Outlined.Security, "Confirm sensitive actions", "Ask before protected automation actions run", confirmations) {
                confirmations = it
                LocalAgentPrefs.setRequireConfirmationEnabled(context, it)
            }
        }
        Text(
            "The glasses are the interface. Data stays on the phone unless a configured capability needs a remote service.",
            style = MaterialTheme.typography.bodyMedium,
            color = ADColors.Muted,
        )
    }
}

@Composable
internal fun ADStorageScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var cacheBytes by remember { mutableStateOf<Long?>(null) }
    var filesBytes by remember { mutableStateOf<Long?>(null) }
    var synced by remember { mutableStateOf<ADSyncedMediaStats?>(null) }

    LaunchedEffect(Unit) {
        val stats = withContext(Dispatchers.IO) {
            Triple(
                folderBytes(context.cacheDir),
                folderBytes(context.filesDir),
                querySyncedMedia(context),
            )
        }
        cacheBytes = stats.first
        filesBytes = stats.second
        synced = stats.third
    }

    ADPageLayout("Storage", onBack) {
        ADCard {
            Text("On this phone", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            ADStorageMetric("App data", filesBytes?.let(::formatBytes) ?: "Calculating…")
            ADSettingsDetailDivider(0.dp)
            ADStorageMetric("Cache", cacheBytes?.let(::formatBytes) ?: "Calculating…")
            ADSettingsDetailDivider(0.dp)
            ADStorageMetric(
                "Synced glasses media",
                when (val current = synced) {
                    null -> "Calculating…"
                    else -> if (current.count == 0) "None yet" else "${current.count} items · ${formatBytes(current.bytes)}"
                },
            )
        }
        ADCard {
            Text("Glasses media", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(5.dp))
            Text(
                "Synced captures are kept in ${SyncedMediaFolder.relativePath}. Library is the normal place to review them.",
                style = MaterialTheme.typography.bodyMedium,
                color = ADColors.Muted,
            )
        }
        OutlinedButton(
            onClick = {
                runCatching {
                    context.cacheDir.deleteRecursively()
                    context.cacheDir.mkdirs()
                }
                cacheBytes = folderBytes(context.cacheDir)
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Clear app cache") }
    }
}

@Composable
internal fun ADLanguageScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val currentLanguage = Locale.getDefault().displayLanguage
    ADPageLayout("Language", onBack) {
        ADCard {
            Text("App language", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(5.dp))
            Text(currentLanguage, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(5.dp))
            Text("AD Glasses supports the languages packaged with the app. Android manages the active app locale.", color = ADColors.Muted)
        }
        Button(
            onClick = {
                val intent = if (Build.VERSION.SDK_INT >= 33) {
                    Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.parse("package:${context.packageName}"))
                } else {
                    Intent(Settings.ACTION_LOCALE_SETTINGS)
                }
                runCatching { context.startActivity(intent) }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) { Text("Choose language") }
    }
}

@Composable
internal fun ADPermissionsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val permissions = buildList {
        add(ADPermissionItem("Microphone", Icons.Outlined.Mic, Manifest.permission.RECORD_AUDIO, true))
        add(ADPermissionItem("Camera", Icons.Outlined.CameraAlt, Manifest.permission.CAMERA, true))
        if (Build.VERSION.SDK_INT >= 31) add(ADPermissionItem("Bluetooth", Icons.Outlined.Bluetooth, Manifest.permission.BLUETOOTH_CONNECT, true))
        if (Build.VERSION.SDK_INT >= 33) add(ADPermissionItem("Nearby devices", Icons.Outlined.Wifi, Manifest.permission.NEARBY_WIFI_DEVICES, true))
        if (Build.VERSION.SDK_INT >= 33) add(ADPermissionItem("Notifications", Icons.Outlined.Notifications, Manifest.permission.POST_NOTIFICATIONS, false))
    }
    ADPageLayout("Permissions", onBack) {
        ADCard {
            permissions.forEachIndexed { index, item ->
                val granted = ContextCompat.checkSelfPermission(context, item.permission) == PackageManager.PERMISSION_GRANTED
                Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(item.icon, null, tint = if (granted) ADColors.Success else ADColors.Muted, modifier = Modifier.size(21.dp))
                    Column(Modifier.padding(start = 11.dp).weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium)
                        Text(if (item.required) "Used by glasses features" else "Optional", color = ADColors.Muted)
                    }
                    ADStatusChip(if (granted) "ALLOWED" else "OFF", if (granted) ADStatusTone.SUCCESS else ADStatusTone.NEUTRAL)
                }
                if (index != permissions.lastIndex) ADSettingsDetailDivider(0.dp)
            }
        }
        Button(
            onClick = { openAppSettings(context.packageName, context::startActivity) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) { Text("Manage permissions") }
    }
}

@Composable
internal fun ADAboutScreen(onBack: () -> Unit) {
    ADPageLayout("About", onBack) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Column(Modifier.padding(22.dp)) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                        ADGlassesMark(Modifier.size(width = 48.dp, height = 32.dp))
                    }
                }
                Spacer(Modifier.height(22.dp))
                Text(
                    "AD Glasses",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "A quiet interface for a very capable pair of glasses.",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.76f),
                )
                Spacer(Modifier.height(18.dp))
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Text(
                        "VERSION ${BuildConfig.VERSION_NAME.uppercase(Locale.US)}",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Built around the glasses",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                "The phone is the engine, not the destination. Voice, camera and lightweight actions stay centered on what the glasses can do naturally.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        ADAboutPrinciple(
            icon = Icons.Outlined.Mic,
            title = "Voice first",
            detail = "Ask, capture and control without turning the phone into the main interface.",
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        ADAboutPrinciple(
            icon = Icons.Outlined.Visibility,
            title = "Vision when useful",
            detail = "Use the glasses camera for capture and visual questions when the connected hardware supports it.",
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        )
        ADAboutPrinciple(
            icon = Icons.Outlined.Public,
            title = "Current when needed",
            detail = "Fresh information can use Web Search through the relay you choose; local features stay local where possible.",
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
            "AD Glasses includes open-source components and device SDK integrations. Required license notices remain part of the distribution.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ADAboutPrinciple(
    icon: ImageVector,
    title: String,
    detail: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = contentColor.copy(alpha = 0.10f),
                contentColor = contentColor,
            ) {
                Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
                }
            }
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = contentColor.copy(alpha = 0.78f))
            }
        }
    }
}

@Composable
private fun ADSettingsDetailGroup(
    title: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = ADColors.Muted)
        ADCard(content = content)
    }
}

@Composable
private fun ADSettingsDetailDivider(start: androidx.compose.ui.unit.Dp = 48.dp) {
    HorizontalDivider(Modifier.padding(start = start), color = ADColors.Separator)
}

@Composable
private fun ADToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    ADSettingsRow(
        icon = icon,
        title = title,
        subtitle = subtitle,
        onClick = { onChecked(!checked) },
        trailing = { Switch(checked = checked, onCheckedChange = onChecked) },
    )
}

@Composable
private fun ADStorageMetric(label: String, value: String) {
    Row(Modifier.padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = ADColors.Muted)
    }
}

private data class ADSyncedMediaStats(val count: Int, val bytes: Long)

private fun querySyncedMedia(context: android.content.Context): ADSyncedMediaStats = runCatching {
    if (Build.VERSION.SDK_INT < 29) return@runCatching ADSyncedMediaStats(0, 0)
    var count = 0
    var bytes = 0L
    val projection = arrayOf(MediaStore.Files.FileColumns.SIZE)
    context.contentResolver.query(
        MediaStore.Files.getContentUri("external"),
        projection,
        "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
        arrayOf(SyncedMediaFolder.relativePathLikePattern()),
        null,
    )?.use { cursor ->
        val sizeIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns.SIZE)
        while (cursor.moveToNext()) {
            count++
            if (sizeIndex >= 0) bytes += cursor.getLong(sizeIndex).coerceAtLeast(0L)
        }
    }
    ADSyncedMediaStats(count, bytes)
}.getOrDefault(ADSyncedMediaStats(0, 0))

private fun folderBytes(root: File): Long = runCatching {
    root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
}.getOrDefault(0L)

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(Locale.US, kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(Locale.US, mb)
    return "%.1f GB".format(Locale.US, mb / 1024.0)
}

private data class ADPermissionItem(
    val title: String,
    val icon: ImageVector,
    val permission: String,
    val required: Boolean,
)

private fun openAppSettings(packageName: String, start: (Intent) -> Unit) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
    runCatching { start(intent) }
}

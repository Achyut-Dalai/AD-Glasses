package com.fersaiyan.cyanbridge.ui.adglasses

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
        ADCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(44.dp).background(ADColors.CyanSoft, RoundedCornerShape(13.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Lock, contentDescription = null, tint = ADColors.CyanDeep, modifier = Modifier.size(21.dp))
                }
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text("Local by default", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Data stays on this phone unless a configured capability needs a remote service.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ADColors.Muted,
                    )
                }
            }
        }

        ADSettingsDetailGroup("Conversation data") {
            ADToggleRow(
                Icons.Outlined.Description,
                "Save transcripts",
                "Keep supported transcripts on this phone",
                storeTranscripts,
            ) {
                storeTranscripts = it
                PrivacyPrefs.setTranscriptStorageEnabled(context, it)
            }
            ADSettingsDetailDivider()
            ADToggleRow(
                Icons.Outlined.Lock,
                "Redact names",
                "Apply best-effort name redaction to saved text",
                redactNames,
            ) {
                redactNames = it
                PrivacyPrefs.setRedactNamesEnabled(context, it)
            }
            ADSettingsDetailDivider()
            ADToggleRow(
                Icons.Outlined.Description,
                "Full transcript in exports",
                "Include complete transcription when exporting",
                fullExports,
            ) {
                fullExports = it
                PrivacyPrefs.setIncludeFullTranscriptionInExportsEnabled(context, it)
            }
        }

        ADSettingsDetailGroup("Automation") {
            ADToggleRow(
                Icons.Outlined.Security,
                "Confirm sensitive actions",
                "Ask before protected phone actions run",
                confirmations,
            ) {
                confirmations = it
                LocalAgentPrefs.setRequireConfirmationEnabled(context, it)
            }
        }
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(44.dp).background(ADColors.CyanSoft, RoundedCornerShape(13.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Storage, contentDescription = null, tint = ADColors.CyanDeep, modifier = Modifier.size(21.dp))
                }
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text("On this phone", style = MaterialTheme.typography.titleLarge)
                    Text("App data and synced glasses media", style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ADStorageStat(
                    label = "App data",
                    value = filesBytes?.let(::formatBytes) ?: "…",
                    modifier = Modifier.weight(1f),
                )
                ADStorageStat(
                    label = "Cache",
                    value = cacheBytes?.let(::formatBytes) ?: "…",
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            ADStorageStat(
                label = "Synced media",
                value = when (val current = synced) {
                    null -> "Calculating…"
                    else -> if (current.count == 0) "None yet" else "${current.count} items · ${formatBytes(current.bytes)}"
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(Icons.Outlined.Folder, contentDescription = null, tint = ADColors.Muted, modifier = Modifier.size(18.dp))
            Text(
                "Synced captures are stored in ${SyncedMediaFolder.relativePath}. Library is the normal place to review them.",
                modifier = Modifier.padding(start = 8.dp).weight(1f),
                style = MaterialTheme.typography.bodySmall,
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
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            shape = RoundedCornerShape(14.dp),
        ) { Text("Clear app cache") }
    }
}

@Composable
private fun ADStorageStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.background(ADColors.SurfaceSubtle, RoundedCornerShape(12.dp)).padding(11.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = ADColors.Muted)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.labelLarge, color = ADColors.Ink)
    }
}

@Composable
internal fun ADLanguageScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val currentLanguage = Locale.getDefault().displayLanguage

    ADPageLayout("Language", onBack) {
        ADCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(44.dp).background(ADColors.CyanSoft, RoundedCornerShape(13.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Language, contentDescription = null, tint = ADColors.CyanDeep, modifier = Modifier.size(21.dp))
                }
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(currentLanguage, style = MaterialTheme.typography.titleLarge)
                    Text("Current app language", style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
                }
                ADStatusChip("ANDROID", ADStatusTone.NEUTRAL)
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = ADColors.Separator)
            Spacer(Modifier.height(10.dp))
            Text(
                "Android manages the active app locale. Available languages depend on the languages packaged with AD Glasses.",
                style = MaterialTheme.typography.bodySmall,
                color = ADColors.Muted,
            )
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
            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ADColors.Graphite),
        ) { Text("Choose language") }
    }
}

@Composable
internal fun ADPermissionsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val permissions = buildList {
        add(ADPermissionItem("Microphone", Icons.Outlined.Mic, Manifest.permission.RECORD_AUDIO, "Voice questions and audio recording", true))
        add(ADPermissionItem("Camera", Icons.Outlined.CameraAlt, Manifest.permission.CAMERA, "Capture and visual questions", true))
        if (Build.VERSION.SDK_INT >= 31) {
            add(ADPermissionItem("Bluetooth", Icons.Outlined.Bluetooth, Manifest.permission.BLUETOOTH_CONNECT, "Connect to supported glasses", true))
        }
        if (Build.VERSION.SDK_INT >= 33) {
            add(ADPermissionItem("Nearby devices", Icons.Outlined.Wifi, Manifest.permission.NEARBY_WIFI_DEVICES, "Local media transfer from glasses", true))
            add(ADPermissionItem("Notifications", Icons.Outlined.Notifications, Manifest.permission.POST_NOTIFICATIONS, "Background status and completion alerts", false))
        }
    }

    ADPageLayout("Permissions", onBack) {
        ADCard {
            permissions.forEachIndexed { index, item ->
                val granted = ContextCompat.checkSelfPermission(context, item.permission) == PackageManager.PERMISSION_GRANTED
                Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                if (granted) ADColors.SuccessSoft else ADColors.SurfaceSubtle,
                                RoundedCornerShape(12.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            item.icon,
                            contentDescription = null,
                            tint = if (granted) ADColors.Success else ADColors.Ink,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium)
                        Text(item.detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
                    }
                    ADStatusChip(
                        if (granted) "ALLOWED" else if (item.required) "NEEDED" else "OPTIONAL",
                        if (granted) ADStatusTone.SUCCESS else ADStatusTone.NEUTRAL,
                    )
                }
                if (index != permissions.lastIndex) ADSettingsDetailDivider(52.dp)
            }
        }

        Button(
            onClick = { openAppSettings(context.packageName, context::startActivity) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ADColors.Graphite),
        ) { Text("Manage permissions") }
    }
}

/** Kept for compatibility with callers that still reference the older About entry point. */
@Composable
internal fun ADAboutScreen(onBack: () -> Unit) {
    ADMinimalAboutScreen(onBack)
}

@Composable
private fun ADSettingsDetailGroup(
    title: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = ADColors.Muted,
            modifier = Modifier.padding(start = 2.dp),
        )
        ADCard(content = content)
    }
}

@Composable
private fun ADSettingsDetailDivider(start: androidx.compose.ui.unit.Dp = 52.dp) {
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
    val detail: String,
    val required: Boolean,
)

private fun openAppSettings(packageName: String, start: (Intent) -> Unit) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
    runCatching { start(intent) }
}

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
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Description
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
        ADPageHero(
            icon = Icons.Outlined.Security,
            title = "Private by default",
            detail = "Choose what AD keeps on this phone and when automation should stop for confirmation.",
            status = "ON DEVICE",
        )

        ADSettingsDetailGroup("Saved conversation data") {
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
                "Best-effort name redaction in saved text",
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

        ADSettingsDetailGroup("Automation safety") {
            ADToggleRow(
                Icons.Outlined.Security,
                "Confirm sensitive actions",
                "Ask before protected Android actions run",
                confirmations,
            ) {
                confirmations = it
                LocalAgentPrefs.setRequireConfirmationEnabled(context, it)
            }
        }

        ADCard {
            ADSectionEyebrow("Boundary")
            Spacer(Modifier.height(7.dp))
            Text(
                "The glasses are the interface. Data stays on the phone unless a capability you configured needs a remote AI or web service.",
                style = MaterialTheme.typography.bodyLarge,
                color = ADColors.Muted,
            )
        }
    }
}

@Composable
internal fun ADStorageScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var cacheBytes by remember { mutableStateOf<Long?>(null) }
    var filesBytes by remember { mutableStateOf<Long?>(null) }
    var synced by remember { mutableStateOf<ADSyncedMediaStats?>(null) }

    fun refreshStorage() {
        cacheBytes = folderBytes(context.cacheDir)
        filesBytes = folderBytes(context.filesDir)
        synced = querySyncedMedia(context)
    }

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

    val syncedLabel = when (val current = synced) {
        null -> "Calculating…"
        else -> if (current.count == 0) "None yet" else "${current.count} items · ${formatBytes(current.bytes)}"
    }

    ADPageLayout("Storage", onBack) {
        ADPageHero(
            icon = Icons.Outlined.Storage,
            title = "Stored on this phone",
            detail = "Captures, transcripts and app data stay local unless you explicitly export or use a remote capability.",
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ADMetricBlock(
                    label = "App data",
                    value = filesBytes?.let(::formatBytes) ?: "…",
                    modifier = Modifier.weight(1f),
                )
                ADMetricBlock(
                    label = "Cache",
                    value = cacheBytes?.let(::formatBytes) ?: "…",
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(10.dp))
            ADMetricBlock(
                label = "Synced glasses media",
                value = syncedLabel,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        ADCard {
            ADSectionEyebrow("Glasses media")
            Spacer(Modifier.height(7.dp))
            Text("Library is the normal place to review synced captures.", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Android stores them under ${SyncedMediaFolder.relativePath} so they remain available outside AD Glasses too.",
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
                refreshStorage()
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) {
            Icon(Icons.Outlined.DeleteSweep, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("Clear app cache")
        }
    }
}

@Composable
internal fun ADLanguageScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val currentLanguage = Locale.getDefault().displayLanguage

    ADPageLayout("Language", onBack) {
        ADPageHero(
            icon = Icons.Outlined.Language,
            title = currentLanguage,
            detail = "AD follows the Android app language for its interface and uses your configured speech settings for voice features.",
            status = "APP LANGUAGE",
        )

        ADCard {
            ADSectionEyebrow("System managed")
            Spacer(Modifier.height(7.dp))
            Text("Choose the language in Android", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(5.dp))
            Text(
                "Keeping locale selection in Android means accessibility, formatting and system language behavior stay consistent with the rest of your phone.",
                style = MaterialTheme.typography.bodyMedium,
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
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ADColors.Ink),
        ) {
            Icon(Icons.Outlined.Language, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("Choose language")
        }
    }
}

@Composable
internal fun ADPermissionsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val permissions = buildList {
        add(ADPermissionItem("Microphone", Icons.Outlined.Mic, Manifest.permission.RECORD_AUDIO, true, "Voice, Translate and Soundbites"))
        add(ADPermissionItem("Camera", Icons.Outlined.CameraAlt, Manifest.permission.CAMERA, true, "Visual questions and phone camera paths"))
        if (Build.VERSION.SDK_INT >= 31) {
            add(ADPermissionItem("Bluetooth", Icons.Outlined.Bluetooth, Manifest.permission.BLUETOOTH_CONNECT, true, "Connect and control glasses"))
        }
        if (Build.VERSION.SDK_INT >= 33) {
            add(ADPermissionItem("Nearby devices", Icons.Outlined.Wifi, Manifest.permission.NEARBY_WIFI_DEVICES, true, "Local glasses media transfer"))
            add(ADPermissionItem("Notifications", Icons.Outlined.Notifications, Manifest.permission.POST_NOTIFICATIONS, false, "Background capability status"))
        }
    }
    val grantedCount = permissions.count {
        ContextCompat.checkSelfPermission(context, it.permission) == PackageManager.PERMISSION_GRANTED
    }

    ADPageLayout("Permissions", onBack) {
        ADPageHero(
            icon = Icons.Outlined.Security,
            title = "$grantedCount of ${permissions.size} allowed",
            detail = "AD asks for hardware access only when a glasses or AI feature needs it.",
            status = if (grantedCount == permissions.size) "READY" else "REVIEW",
            statusTone = if (grantedCount == permissions.size) ADStatusTone.SUCCESS else ADStatusTone.WARNING,
        )

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            permissions.forEach { item ->
                val granted = ContextCompat.checkSelfPermission(context, item.permission) == PackageManager.PERMISSION_GRANTED
                ADCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(ADColors.SurfaceSubtle, RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                item.icon,
                                contentDescription = null,
                                tint = if (granted) ADColors.Ink else ADColors.Muted,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                if (!item.required) {
                                    Text(
                                        "  OPTIONAL",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ADColors.Muted,
                                    )
                                }
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(item.detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
                        }
                        ADStatusChip(
                            if (granted) "ALLOWED" else "OFF",
                            if (granted) ADStatusTone.SUCCESS else ADStatusTone.NEUTRAL,
                            showCheck = granted,
                        )
                    }
                }
            }
        }

        Button(
            onClick = { openAppSettings(context.packageName, context::startActivity) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ADColors.Ink),
        ) {
            Icon(Icons.Outlined.Security, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("Manage permissions")
        }
    }
}

@Composable
private fun ADSettingsDetailGroup(
    title: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        ADSectionEyebrow(title)
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
    val required: Boolean,
    val detail: String,
)

private fun openAppSettings(packageName: String, start: (Intent) -> Unit) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
    runCatching { start(intent) }
}

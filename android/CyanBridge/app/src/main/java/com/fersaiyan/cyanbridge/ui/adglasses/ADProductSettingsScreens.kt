package com.fersaiyan.cyanbridge.ui.adglasses

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DeveloperMode
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Tune
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.fersaiyan.cyanbridge.BuildConfig
import com.fersaiyan.cyanbridge.agent.LocalAgentPrefs
import com.fersaiyan.cyanbridge.ai.router.AiProviderPrefs
import com.fersaiyan.cyanbridge.ai.router.AiProviderType
import com.fersaiyan.cyanbridge.ai.router.CliRelayBackend
import com.fersaiyan.cyanbridge.devices.DeviceProfileStore
import com.fersaiyan.cyanbridge.media.SyncedMediaFolder
import com.fersaiyan.cyanbridge.privacy.PrivacyPrefs
import com.fersaiyan.cyanbridge.shared.glasses.GlassesDashboardUiState
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun ADSettingsHubScreen(
    state: GlassesDashboardUiState,
    onBack: () -> Unit,
    onDevice: () -> Unit,
    onIntelligence: () -> Unit,
    onRouting: () -> Unit,
    onPrivacy: () -> Unit,
    onStorage: () -> Unit,
    onLanguage: () -> Unit,
    onPermissions: () -> Unit,
    onAdvanced: () -> Unit,
    onAbout: () -> Unit,
) {
    val context = LocalContext.current
    val presentation = buildADDevicePresentation(
        state = state,
        profile = DeviceProfileStore.loadLastSelected(context),
    )

    ADProductPage("Settings", onBack) {
        ADCard(onClick = onDevice) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ADGlassesMark(Modifier.size(width = 42.dp, height = 28.dp))
                Column(Modifier.padding(start = 13.dp).weight(1f)) {
                    Text(
                        presentation.identityLabel ?: "Glasses",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        presentation.statusLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = ADColors.Muted,
                    )
                }
            }
        }

        ADProductSettingsGroup("Intelligence") {
            ADSettingsRow(Icons.Outlined.AutoAwesome, "AI and web", "Models, relay and Web Search", onIntelligence)
            ADProductDivider()
            ADSettingsRow(Icons.Outlined.Tune, "Routing", "Choose where requests are processed", onRouting)
        }

        ADProductSettingsGroup("Privacy and data") {
            ADSettingsRow(Icons.Outlined.PrivacyTip, "Privacy", "Transcripts, redaction and exports", onPrivacy)
            ADProductDivider()
            ADSettingsRow(Icons.Outlined.Storage, "Storage", "App data and media synced from the glasses", onStorage)
        }

        ADProductSettingsGroup("General") {
            ADSettingsRow(Icons.Outlined.Language, "Language", "App language and system locale", onLanguage)
            ADProductDivider()
            ADSettingsRow(Icons.Outlined.Security, "Permissions", "Camera, microphone, Bluetooth and nearby devices", onPermissions)
        }

        ADProductSettingsGroup("AD Glasses") {
            ADSettingsRow(Icons.Outlined.DeveloperMode, "Advanced", "Diagnostics and system controls", onAdvanced)
            ADProductDivider()
            ADSettingsRow(Icons.Outlined.Info, "About AD Glasses", "Version and product information", onAbout)
        }
    }
}

@Composable
internal fun ADIntelligenceScreen(onBack: () -> Unit, onRouting: () -> Unit) {
    val context = LocalContext.current
    val provider = AiProviderPrefs.getProvider(context)
    val relayConfigured = AiProviderPrefs.isRelayConfigured(context)
    ADProductPage("AI and web", onBack) {
        ADCard {
            Text("For the glasses", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(5.dp))
            Text(
                "Answers, vision and Web Search run on the phone, then the useful part is spoken through the glasses. Longer results stay in Conversations.",
                style = MaterialTheme.typography.bodyLarge,
                color = ADColors.Muted,
            )
        }
        ADProductSettingsGroup("Current route") {
            ADProductStatusRow(
                Icons.Outlined.AutoAwesome,
                "Processing",
                when (provider) {
                    AiProviderType.LOCAL_MODELS -> "On device"
                    AiProviderType.CLI_RELAY -> "Relay"
                    AiProviderType.MOCK -> "Demo provider"
                    AiProviderType.COMPANY_BACKEND -> "Custom backend"
                },
            )
            ADProductDivider()
            ADProductStatusRow(
                Icons.Outlined.Public,
                "Web Search",
                if (relayConfigured) "Available when relay supports it" else "Relay setup required",
            )
        }
        Button(
            onClick = onRouting,
            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ADColors.Ink),
        ) { Text("Configure routing") }
    }
}

@Composable
internal fun ADRoutingScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var provider by remember { mutableStateOf(AiProviderPrefs.getProvider(context)) }
    var relayUrl by remember { mutableStateOf(AiProviderPrefs.getRelayBaseUrl(context)) }
    var backend by remember { mutableStateOf(AiProviderPrefs.getRelayBackend(context)) }
    var saved by remember { mutableStateOf(false) }

    ADProductPage("Routing", onBack) {
        ADProductSettingsGroup("Default processing") {
            ADChoiceRow(
                icon = Icons.Outlined.Memory,
                title = "On device",
                subtitle = "Use installed local models where supported",
                selected = provider == AiProviderType.LOCAL_MODELS,
            ) {
                provider = AiProviderType.LOCAL_MODELS
                AiProviderPrefs.setProvider(context, provider)
            }
            ADProductDivider()
            ADChoiceRow(
                icon = Icons.Outlined.Cloud,
                title = "Relay",
                subtitle = "Use your configured private relay",
                selected = provider == AiProviderType.CLI_RELAY,
            ) {
                provider = AiProviderType.CLI_RELAY
                AiProviderPrefs.setProvider(context, provider)
            }
        }

        ADProductSettingsGroup("Relay") {
            Text("Server address", style = MaterialTheme.typography.labelLarge, color = ADColors.Muted)
            Spacer(Modifier.height(8.dp))
            BasicTextField(
                value = relayUrl,
                onValueChange = { relayUrl = it; saved = false },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ADColors.SurfaceSubtle, RoundedCornerShape(12.dp))
                    .padding(horizontal = 13.dp, vertical = 13.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = ADColors.Ink),
                cursorBrush = SolidColor(ADColors.Ink),
                decorationBox = { field ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (relayUrl.isBlank()) Text("https://your-relay.example", color = ADColors.Muted)
                        field()
                    }
                },
            )
            Spacer(Modifier.height(14.dp))
            Text("Backend", style = MaterialTheme.typography.labelLarge, color = ADColors.Muted)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ADCompactChoice("Gemini", backend == CliRelayBackend.GEMINI, Modifier.weight(1f)) {
                    backend = CliRelayBackend.GEMINI
                    AiProviderPrefs.setRelayBackend(context, backend)
                }
                ADCompactChoice("Codex", backend == CliRelayBackend.CODEX, Modifier.weight(1f)) {
                    backend = CliRelayBackend.CODEX
                    AiProviderPrefs.setRelayBackend(context, backend)
                }
            }
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = {
                    AiProviderPrefs.setRelayBaseUrl(context, relayUrl)
                    saved = true
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = ADColors.Ink),
            ) { Text(if (saved) "Saved" else "Save relay") }
        }
        Text(
            "Web Search is used automatically for fresh/current questions when the configured relay advertises grounding support.",
            style = MaterialTheme.typography.bodyMedium,
            color = ADColors.Muted,
        )
    }
}

@Composable
internal fun ADPrivacyCenterScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var storeTranscripts by remember { mutableStateOf(PrivacyPrefs.isTranscriptStorageEnabled(context)) }
    var redactNames by remember { mutableStateOf(PrivacyPrefs.isRedactNamesEnabled(context)) }
    var fullExports by remember { mutableStateOf(PrivacyPrefs.isIncludeFullTranscriptionInExportsEnabled(context)) }
    var confirmations by remember { mutableStateOf(LocalAgentPrefs.isRequireConfirmationEnabled(context)) }

    ADProductPage("Privacy", onBack) {
        ADProductSettingsGroup("Conversation data") {
            ADToggleRow(Icons.Outlined.Description, "Save transcripts", "Keep supported transcripts on the phone", storeTranscripts) {
                storeTranscripts = it
                PrivacyPrefs.setTranscriptStorageEnabled(context, it)
            }
            ADProductDivider()
            ADToggleRow(Icons.Outlined.Lock, "Redact names", "Best-effort name redaction in saved text", redactNames) {
                redactNames = it
                PrivacyPrefs.setRedactNamesEnabled(context, it)
            }
            ADProductDivider()
            ADToggleRow(Icons.Outlined.Description, "Full transcript in exports", "Include complete transcription when exporting", fullExports) {
                fullExports = it
                PrivacyPrefs.setIncludeFullTranscriptionInExportsEnabled(context, it)
            }
        }
        ADProductSettingsGroup("Phone Control") {
            ADToggleRow(Icons.Outlined.Security, "Confirm sensitive actions", "Ask before protected phone actions run", confirmations) {
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

    ADProductPage("Storage", onBack) {
        ADCard {
            Text("On this phone", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            ADStorageMetric("App data", filesBytes?.let(::formatBytes) ?: "Calculating…")
            ADProductDivider(0.dp)
            ADStorageMetric("Cache", cacheBytes?.let(::formatBytes) ?: "Calculating…")
            ADProductDivider(0.dp)
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
    ADProductPage("Language", onBack) {
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
            colors = ButtonDefaults.buttonColors(containerColor = ADColors.Ink),
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
    ADProductPage("Permissions", onBack) {
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
                if (index != permissions.lastIndex) ADProductDivider(0.dp)
            }
        }
        Button(
            onClick = { openAppSettings(context.packageName, context::startActivity) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = ADColors.Ink),
        ) { Text("Manage permissions") }
    }
}

@Composable
internal fun ADAboutScreen(onBack: () -> Unit) {
    ADProductPage("About AD Glasses", onBack) {
        ADCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ADGlassesMark(Modifier.size(width = 58.dp, height = 38.dp))
                Column(Modifier.padding(start = 15.dp)) {
                    Text("AD Glasses", style = MaterialTheme.typography.headlineSmall)
                    Text("Version ${BuildConfig.VERSION_NAME}", color = ADColors.Muted)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "A personal companion for displayless smart glasses. The glasses handle the interaction; the phone handles intelligence, tools, memory and media behind the scenes.",
                style = MaterialTheme.typography.bodyLarge,
                color = ADColors.Muted,
            )
        }
        ADProductSettingsGroup("Product") {
            ADProductStatusRow(Icons.Outlined.Mic, "Primary interaction", "Voice through the glasses")
            ADProductDivider()
            ADProductStatusRow(Icons.Outlined.CameraAlt, "Vision", "Glasses camera when supported")
            ADProductDivider()
            ADProductStatusRow(Icons.Outlined.Public, "Current information", "Web Search through your configured relay")
        }
        Text(
            "AD Glasses includes open-source components and device SDK integrations. Required license notices remain part of the distribution.",
            style = MaterialTheme.typography.bodyMedium,
            color = ADColors.Muted,
        )
    }
}

@Composable
private fun ADProductPage(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ADTopBar(title = title, showBack = true, onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            content = content,
        )
    }
}

@Composable
private fun ADProductSettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = ADColors.Muted)
        ADCard(content = content)
    }
}

@Composable
private fun ADProductDivider(start: androidx.compose.ui.unit.Dp = 48.dp) {
    HorizontalDivider(Modifier.padding(start = start), color = ADColors.Separator)
}

@Composable
private fun ADProductStatusRow(icon: ImageVector, title: String, value: String) {
    Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = ADColors.Muted, modifier = Modifier.size(21.dp))
        Text(title, Modifier.padding(start = 11.dp).weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(value, Modifier.weight(1.2f), style = MaterialTheme.typography.bodyMedium, color = ADColors.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ADChoiceRow(icon: ImageVector, title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(38.dp).background(ADColors.SurfaceSubtle, RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = ADColors.Ink, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = ADColors.Muted)
        }
        if (selected) Icon(Icons.Outlined.CheckCircle, null, tint = ADColors.Success)
    }
}

@Composable
private fun ADCompactChoice(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .heightIn(min = 46.dp)
            .background(if (selected) ADColors.Ink else ADColors.SurfaceSubtle, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (selected) Color.White else ADColors.Ink, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun ADToggleRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
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

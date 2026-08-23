package com.ad_glasses.ui.adglasses

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ad_glasses.agent.CloudServerPrefs
import com.ad_glasses.agent.CloudSettingsActivity
import com.ad_glasses.ai.live.GeminiLiveActivity
import com.ad_glasses.ai.router.AiProviderPrefs
import com.ad_glasses.ai.router.ApiProvider
import com.ad_glasses.ai.router.ApiTokenClient
import com.ad_glasses.ai.router.CloudAiProfile
import com.ad_glasses.ai.router.CloudWebMode
import com.ad_glasses.ai.transcription.moonshine.MoonshineModelManager
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun ADNativeCloudAiSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var profiles by remember { mutableStateOf(AiProviderPrefs.listProfiles(context)) }
    var activeId by remember { mutableStateOf(AiProviderPrefs.getActiveProfile(context)?.id) }
    var editing by remember { mutableStateOf<CloudAiProfile?>(null) }
    var deleteTarget by remember { mutableStateOf<CloudAiProfile?>(null) }
    var status by remember { mutableStateOf<String?>(null) }

    val realtimeReady = AiProviderPrefs.isRelayConfigured(context) &&
        CloudServerPrefs.getApiToken(context).isNotBlank()

    fun refreshProfiles() {
        profiles = AiProviderPrefs.listProfiles(context)
        activeId = AiProviderPrefs.getActiveProfile(context)?.id
    }

    ADPageLayout("Cloud AI", onBack) {
        ADCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Cloud, null, tint = Color.Black, modifier = Modifier.size(20.dp))
                Column(Modifier.padding(start = 9.dp).weight(1f)) {
                    Text("Cloud profiles", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Keep separate accounts, endpoints and models. You can add more than one profile for the same provider.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ADColors.Muted,
                    )
                }
            }
        }

        ADCard {
            if (profiles.isEmpty()) {
                Text("No Cloud AI profile yet", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.size(4.dp))
                Text(
                    "Add a profile to use Ask, image questions, and Cloud-powered automation planning.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ADColors.Muted,
                )
            } else {
                profiles.forEachIndexed { index, profile ->
                    ADCloudProfileRow(
                        profile = profile,
                        active = profile.id == activeId,
                        configured = AiProviderPrefs.hasApiKey(context, profile.id),
                        onOpen = { editing = profile },
                        onSelect = {
                            AiProviderPrefs.setActiveProfile(context, profile.id)
                            activeId = profile.id
                            status = "${profile.name} is now active"
                        },
                        onDelete = { deleteTarget = profile },
                    )
                    if (index != profiles.lastIndex) HorizontalDivider(color = ADColors.Separator)
                }
            }
            Spacer(Modifier.size(9.dp))
            OutlinedButton(
                onClick = {
                    val provider = ApiProvider.GOOGLE
                    editing = AiProviderPrefs.newProfile(
                        provider = provider,
                        existingCount = profiles.count { it.provider == provider },
                    )
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
            ) {
                Icon(Icons.Outlined.Add, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text("Add Cloud profile")
            }
        }

        status?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
        }

        ADCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Mic, null, tint = Color.Black, modifier = Modifier.size(19.dp))
                Column(Modifier.padding(start = 8.dp).weight(1f)) {
                    Text("Realtime", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (realtimeReady) "Gemini Live session authorization is ready"
                        else "Configure the service used to authorize short-lived Gemini Live sessions",
                        style = MaterialTheme.typography.bodySmall,
                        color = ADColors.Muted,
                    )
                }
            }
            Spacer(Modifier.size(8.dp))
            Text(
                "Realtime is separate from REST profiles. API profile secrets never leave encrypted app storage except in requests to that profile's endpoint.",
                style = MaterialTheme.typography.bodySmall,
                color = ADColors.Muted,
            )
            Spacer(Modifier.size(9.dp))
            Button(
                onClick = { context.startActivity(Intent(context, CloudSettingsActivity::class.java)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ADColors.Ink),
            ) { Text("Configure Realtime service") }
            Spacer(Modifier.size(6.dp))
            OutlinedButton(
                onClick = { context.startActivity(Intent(context, GeminiLiveActivity::class.java)) },
                enabled = realtimeReady,
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
            ) { Text("Open Gemini Live preview") }
        }

        MoonshineVoiceInputCard()

        Text(
            "Profile metadata and API keys are stored in Android Keystore-backed encrypted preferences and excluded from Android backup/device transfer. Saved keys are never displayed again; enter a new key only when replacing one.",
            style = MaterialTheme.typography.bodySmall,
            color = ADColors.Muted,
        )
    }

    editing?.let { initial ->
        ADCloudProfileEditor(
            initial = initial,
            hasSavedKey = AiProviderPrefs.hasApiKey(context, initial.id),
            onDismiss = { editing = null },
            onSave = { draft, replacement ->
                runCatching {
                    AiProviderPrefs.saveProfile(
                        context = context,
                        profile = draft,
                        apiKeyReplacement = replacement.takeIf { it.isNotBlank() },
                        makeActive = profiles.isEmpty(),
                    )
                }.onSuccess { saved ->
                    editing = null
                    status = "${saved.name} saved"
                    refreshProfiles()
                }.onFailure { error ->
                    status = error.message ?: "Could not save profile"
                }
            },
            onDiscoverModels = { draft, replacement, onResult ->
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        ApiTokenClient.discoverModels(
                            context = context,
                            provider = draft.provider,
                            baseUrl = draft.baseUrl,
                            profileId = draft.id,
                            apiKeyReplacement = replacement.takeIf { it.isNotBlank() },
                        )
                    }
                    onResult(result)
                }
            },
        )
    }

    deleteTarget?.let { profile ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete ${profile.name}?") },
            text = {
                Text("The profile and its encrypted API key will be permanently removed from this device.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        AiProviderPrefs.deleteProfile(context, profile.id)
                        deleteTarget = null
                        status = "${profile.name} deleted"
                        refreshProfiles()
                    },
                ) { Text("Delete", color = ADColors.Error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ADCloudProfileRow(
    profile: CloudAiProfile,
    active: Boolean,
    configured: Boolean,
    onOpen: () -> Unit,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(profile.name, style = MaterialTheme.typography.titleMedium)
            Text(
                buildString {
                    append(profile.provider.label)
                    if (profile.model.isNotBlank()) append(" · ${profile.model}")
                    if (!configured) append(" · key needed")
                },
                style = MaterialTheme.typography.bodySmall,
                color = ADColors.Muted,
                maxLines = 2,
            )
        }
        if (!active && configured) {
            TextButton(onClick = onSelect) { Text("Use") }
        }
        if (active) {
            Icon(Icons.Outlined.CheckCircle, "Active profile", tint = ADColors.Blue, modifier = Modifier.size(19.dp))
            Spacer(Modifier.size(4.dp))
        }
        androidx.compose.material3.IconButton(onClick = onDelete) {
            Icon(Icons.Outlined.Delete, "Delete ${profile.name}", tint = ADColors.Muted, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun ADCloudProfileEditor(
    initial: CloudAiProfile,
    hasSavedKey: Boolean,
    onDismiss: () -> Unit,
    onSave: (CloudAiProfile, String) -> Unit,
    onDiscoverModels: (CloudAiProfile, String, (Result<List<String>>) -> Unit) -> Unit,
) {
    var provider by remember(initial.id) { mutableStateOf(initial.provider) }
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var baseUrl by remember(initial.id) { mutableStateOf(initial.baseUrl) }
    var model by remember(initial.id) { mutableStateOf(initial.model) }
    var replacementKey by remember(initial.id) { mutableStateOf("") }
    var discoveredModels by remember(initial.id) { mutableStateOf<List<String>>(emptyList()) }
    var modelMenuOpen by remember(initial.id) { mutableStateOf(false) }
    var discoveryRunning by remember(initial.id) { mutableStateOf(false) }
    var discoveryError by remember(initial.id) { mutableStateOf<String?>(null) }

    fun draft(): CloudAiProfile = initial.copy(
        name = name,
        provider = provider,
        baseUrl = baseUrl,
        model = model,
        webMode = if (provider.nativeWebCapable) CloudWebMode.AUTO else CloudWebMode.OFF,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (hasSavedKey) "Edit Cloud profile" else "Add Cloud profile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("Provider", style = MaterialTheme.typography.labelLarge)
                ApiProvider.entries.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            if (provider != item) {
                                provider = item
                                baseUrl = item.defaultBaseUrl
                                model = item.defaultModel
                                discoveredModels = emptyList()
                                discoveryError = null
                                if (name.isBlank() || name == initial.provider.label) name = item.label
                            }
                        }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(item.label, modifier = Modifier.weight(1f))
                        if (provider == item) Icon(Icons.Outlined.CheckCircle, null, tint = ADColors.Blue, modifier = Modifier.size(17.dp))
                    }
                }

                ADCloudTextField(name, { name = it }, "Profile name")
                ADCloudTextField(baseUrl, { baseUrl = it }, "API base URL")

                Column {
                    ADCloudTextField(
                        value = replacementKey,
                        onValueChange = { replacementKey = it },
                        placeholder = if (hasSavedKey) "API key saved · enter only to replace" else "API key",
                        secret = true,
                    )
                    Text(
                        if (hasSavedKey) "The saved key cannot be revealed in AD Glasses." else "The key is encrypted before it is stored.",
                        style = MaterialTheme.typography.labelSmall,
                        color = ADColors.Muted,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        enabled = !discoveryRunning && baseUrl.isNotBlank() && (hasSavedKey || replacementKey.isNotBlank()),
                        onClick = {
                            discoveryRunning = true
                            discoveryError = null
                            onDiscoverModels(draft(), replacementKey) { result ->
                                result.onSuccess { models ->
                                    discoveredModels = models
                                    if (model.isBlank() && models.isNotEmpty()) model = models.first()
                                    modelMenuOpen = models.isNotEmpty()
                                }.onFailure { error ->
                                    discoveryError = error.message ?: "Could not fetch models"
                                }
                                discoveryRunning = false
                            }
                        },
                    ) {
                        Icon(Icons.Outlined.Refresh, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(5.dp))
                        Text(if (discoveryRunning) "Fetching…" else "Fetch models")
                    }
                    Box(Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { modelMenuOpen = true },
                            enabled = discoveredModels.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (discoveredModels.isEmpty()) "Model list" else "Choose model") }
                        DropdownMenu(
                            expanded = modelMenuOpen,
                            onDismissRequest = { modelMenuOpen = false },
                        ) {
                            discoveredModels.forEach { item ->
                                DropdownMenuItem(
                                    text = { Text(item) },
                                    onClick = {
                                        model = item
                                        modelMenuOpen = false
                                    },
                                )
                            }
                        }
                    }
                }
                discoveryError?.let { Text(it, color = ADColors.Error, style = MaterialTheme.typography.bodySmall) }
                ADCloudTextField(model, { model = it }, provider.defaultModel.ifBlank { "Model ID" })

                Text(
                    if (provider.nativeWebCapable) {
                        "Web search is available for this provider. Use the globe in Ask to enable it for a specific turn."
                    } else {
                        "This provider has no AD-integrated native web-search tool; Ask still works normally."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = ADColors.Muted,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && baseUrl.isNotBlank() && model.isNotBlank() && (hasSavedKey || replacementKey.isNotBlank()),
                onClick = { onSave(draft(), replacementKey) },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun MoonshineVoiceInputCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val modelKind = remember { MoonshineModelManager.chooseDefault() }
    var installed by remember { mutableStateOf(MoonshineModelManager.isInstalled(context, modelKind)) }
    var status by remember { mutableStateOf<String?>(null) }
    var installing by remember { mutableStateOf(false) }

    ADCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Mic, null, tint = Color.Black, modifier = Modifier.size(19.dp))
            Column(Modifier.padding(start = 7.dp).weight(1f)) {
                Text("Offline English voice input", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (installed) "Moonshine speech-to-text is ready"
                    else "Optional speech-to-text model; this is not a Local LLM",
                    style = MaterialTheme.typography.bodySmall,
                    color = ADColors.Muted,
                )
            }
        }
        if (!installed) {
            Spacer(Modifier.size(9.dp))
            Button(
                enabled = !installing,
                onClick = {
                    installing = true
                    status = "Starting download…"
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                MoonshineModelManager.installIfNeeded(context, modelKind) { progress ->
                                    scope.launch { status = "${progress.percent}% — ${progress.message}" }
                                }
                            }
                        }.onSuccess {
                            installed = true
                            status = "Moonshine is ready"
                        }.onFailure { error ->
                            status = error.message ?: "Moonshine installation failed"
                        }
                        installing = false
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ADColors.Ink),
            ) { Text(if (installing) "Downloading…" else "Download voice model") }
        }
        status?.let {
            Spacer(Modifier.size(5.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
        }
    }
}

@Composable
private fun ADCloudTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    secret: Boolean = false,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .background(ADColors.SurfaceSubtle, RoundedCornerShape(11.dp))
            .padding(horizontal = 11.dp, vertical = 10.dp),
        singleLine = true,
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = ADColors.Ink),
        cursorBrush = SolidColor(ADColors.Ink),
        decorationBox = { field ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isBlank()) Text(placeholder, color = ADColors.Muted, style = MaterialTheme.typography.bodyMedium)
                field()
            }
        },
    )
}

private fun formatAiBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return "%.1f KB".format(Locale.US, kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return "%.1f MB".format(Locale.US, mb)
    return "%.1f GB".format(Locale.US, mb / 1024.0)
}

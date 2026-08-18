package com.fersaiyan.cyanbridge.ui.adglasses

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.fersaiyan.cyanbridge.ai.router.AiProviderPrefs
import com.fersaiyan.cyanbridge.ai.router.AiProviderType
import com.fersaiyan.cyanbridge.ai.router.CliRelayBackend
import com.fersaiyan.cyanbridge.localmodels.remote.RemoteOpenAiPrefs
import com.fersaiyan.cyanbridge.localmodels.storage.InstalledLocalModel
import com.fersaiyan.cyanbridge.localmodels.storage.LocalModelStorageRepository
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun ADNativeRelaySettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var relayUrl by remember { mutableStateOf(AiProviderPrefs.getRelayBaseUrl(context)) }
    var backend by remember { mutableStateOf(AiProviderPrefs.getRelayBackend(context)) }
    var saved by remember { mutableStateOf(false) }
    val configured = relayUrl.startsWith("http://") || relayUrl.startsWith("https://")

    ADPageLayout("Relay", onBack) {
        ADPageHero(
            icon = Icons.Outlined.Cloud,
            title = "Remote AI relay",
            detail = "Use your own relay for Gemini or OpenAI/Codex routes and capabilities that need server-side web grounding.",
            status = if (configured) "CONFIGURED" else "SETUP",
            statusTone = if (configured) ADStatusTone.SUCCESS else ADStatusTone.NEUTRAL,
        )

        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            ADSectionEyebrow("Server")
            ADCard {
                Text("Relay address", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "AD sends only the requests that are routed to this relay.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ADColors.Muted,
                )
                Spacer(Modifier.height(12.dp))
                ADAiTextField(
                    value = relayUrl,
                    onValueChange = { relayUrl = it; saved = false },
                    placeholder = "https://your-relay.example",
                    label = "Base URL",
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            ADSectionEyebrow("Backend")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ADAiBackendCard(
                    title = "Gemini",
                    detail = "Gemini CLI",
                    selected = backend == CliRelayBackend.GEMINI,
                    modifier = Modifier.weight(1f),
                ) {
                    backend = CliRelayBackend.GEMINI
                    saved = false
                }
                ADAiBackendCard(
                    title = "OpenAI / Codex",
                    detail = "Codex CLI",
                    selected = backend == CliRelayBackend.CODEX,
                    modifier = Modifier.weight(1f),
                ) {
                    backend = CliRelayBackend.CODEX
                    saved = false
                }
            }
        }

        Button(
            onClick = {
                AiProviderPrefs.setRelayBaseUrl(context, relayUrl)
                AiProviderPrefs.setRelayBackend(context, backend)
                AiProviderPrefs.setProvider(context, AiProviderType.CLI_RELAY)
                saved = true
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ADColors.Ink),
        ) {
            if (saved) Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(19.dp))
            if (saved) Spacer(Modifier.size(8.dp))
            Text(if (saved) "Relay saved" else "Save relay")
        }

        ADCard {
            ADSectionEyebrow("Used for")
            Spacer(Modifier.height(7.dp))
            Text(
                "Remote answers, web-grounded questions and supported remote vision routes use this connection only when the selected AI path calls for it.",
                style = MaterialTheme.typography.bodyMedium,
                color = ADColors.Muted,
            )
        }
    }
}

@Composable
internal fun ADNativeLocalAiSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var installed by remember { mutableStateOf(LocalModelStorageRepository.listInstalled(context)) }
    var selectedId by remember { mutableStateOf(LocalModelStorageRepository.getSelectedModelId(context)) }
    var importStatus by remember { mutableStateOf<String?>(null) }

    var remoteEnabled by remember { mutableStateOf(RemoteOpenAiPrefs.isEnabled(context)) }
    var remoteUrl by remember { mutableStateOf(RemoteOpenAiPrefs.getBaseUrl(context)) }
    var remoteModel by remember { mutableStateOf(RemoteOpenAiPrefs.getModel(context)) }
    var remoteApiKey by remember { mutableStateOf(RemoteOpenAiPrefs.getApiKey(context)) }
    var remoteSaved by remember { mutableStateOf(false) }

    val selectedModel = installed.firstOrNull { it.id == selectedId }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        importStatus = "Importing…"
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val sourceName = uri.lastPathSegment
                        ?.substringAfterLast('/')
                        ?.takeIf { it.isNotBlank() }
                        ?: "local-model.bin"
                    val managedFile = LocalModelStorageRepository.copyUriToManagedModelFile(
                        context = context,
                        uri = uri,
                        preferredName = sourceName,
                    )
                    LocalModelStorageRepository.registerImportedModel(
                        context = context,
                        displayName = sourceName.substringBeforeLast('.').ifBlank { sourceName },
                        file = managedFile,
                    )
                }
            }
            result.onSuccess { model ->
                installed = LocalModelStorageRepository.listInstalled(context)
                selectedId = model.id
                importStatus = "${model.displayName} is ready"
            }.onFailure { error ->
                importStatus = error.message ?: "Couldn’t import that model"
            }
        }
    }

    ADPageLayout("Local AI", onBack) {
        ADPageHero(
            icon = Icons.Outlined.Memory,
            title = selectedModel?.displayName ?: "AI on this phone",
            detail = if (selectedModel != null) {
                "${formatAiBytes(selectedModel.sizeBytes)} selected for supported on-device inference."
            } else {
                "Import a compatible model to run supported AI work directly on this phone."
            },
            status = if (selectedModel != null) "LOCAL READY" else "NO MODEL",
            statusTone = if (selectedModel != null) ADStatusTone.SUCCESS else ADStatusTone.NEUTRAL,
        )

        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            ADSectionEyebrow("Models on this phone")
            if (installed.isEmpty()) {
                ADCard {
                    Text("No local model installed", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "AD keeps imported model files in managed app storage and lets you choose which one is active.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ADColors.Muted,
                    )
                }
            } else {
                installed.forEach { model ->
                    ADInstalledModelCard(
                        model = model,
                        selected = selectedId == model.id,
                        onClick = {
                            LocalModelStorageRepository.setSelectedModelId(context, model.id)
                            selectedId = model.id
                        },
                    )
                }
            }

            Button(
                onClick = { importLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ADColors.Ink),
            ) {
                Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(19.dp))
                Spacer(Modifier.size(8.dp))
                Text("Import model file")
            }
            importStatus?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted, modifier = Modifier.padding(horizontal = 3.dp))
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            ADSectionEyebrow("Compatible server")
            ADCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(ADColors.SurfaceSubtle, RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.Cloud, contentDescription = null, tint = ADColors.Ink, modifier = Modifier.size(22.dp))
                    }
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text("OpenAI-compatible endpoint", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Ollama, llama.cpp, vLLM or another compatible server",
                            style = MaterialTheme.typography.bodySmall,
                            color = ADColors.Muted,
                        )
                    }
                    Switch(
                        checked = remoteEnabled,
                        onCheckedChange = {
                            remoteEnabled = it
                            RemoteOpenAiPrefs.setEnabled(context, it)
                        },
                    )
                }

                Spacer(Modifier.height(16.dp))
                ADAiTextField(
                    value = remoteUrl,
                    onValueChange = { remoteUrl = it; remoteSaved = false },
                    placeholder = "http://192.168.1.50:11434/v1",
                    label = "Server address",
                )
                Spacer(Modifier.height(10.dp))
                ADAiTextField(
                    value = remoteModel,
                    onValueChange = { remoteModel = it; remoteSaved = false },
                    placeholder = "model name",
                    label = "Model",
                )
                Spacer(Modifier.height(10.dp))
                ADAiTextField(
                    value = remoteApiKey,
                    onValueChange = { remoteApiKey = it; remoteSaved = false },
                    placeholder = "Optional",
                    label = "API key",
                    password = true,
                )
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = {
                        RemoteOpenAiPrefs.setBaseUrl(context, remoteUrl)
                        RemoteOpenAiPrefs.setModel(context, remoteModel)
                        RemoteOpenAiPrefs.setApiKey(context, remoteApiKey)
                        RemoteOpenAiPrefs.setEnabled(context, remoteEnabled)
                        remoteSaved = true
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ADColors.Ink),
                ) {
                    if (remoteSaved) Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    if (remoteSaved) Spacer(Modifier.size(8.dp))
                    Text(if (remoteSaved) "Server saved" else "Save server")
                }
            }
        }
    }
}

@Composable
private fun ADAiBackendCard(
    title: String,
    detail: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    androidx.compose.material3.Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 118.dp),
        shape = RoundedCornerShape(20.dp),
        color = if (selected) ADColors.SurfaceSubtle else ADColors.Surface,
    ) {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (selected) Icon(Icons.Outlined.CheckCircle, contentDescription = "Selected", tint = ADColors.Ink, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(5.dp))
            Text(detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
            Spacer(Modifier.weight(1f))
            Text(if (selected) "ACTIVE" else "SELECT", style = MaterialTheme.typography.labelSmall, color = ADColors.Muted)
        }
    }
}

@Composable
private fun ADInstalledModelCard(
    model: InstalledLocalModel,
    selected: Boolean,
    onClick: () -> Unit,
) {
    androidx.compose.material3.Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = if (selected) ADColors.SurfaceSubtle else ADColors.Surface,
    ) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).background(if (selected) ADColors.Ink else ADColors.SurfaceSubtle, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Storage,
                    contentDescription = null,
                    tint = if (selected) ADColors.Surface else ADColors.Ink,
                    modifier = Modifier.size(21.dp),
                )
            }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(model.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(formatAiBytes(model.sizeBytes), style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
            }
            if (selected) ADStatusChip("SELECTED", ADStatusTone.NEUTRAL, showCheck = true)
        }
    }
}

@Composable
private fun ADAiTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    label: String,
    password: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        shape = RoundedCornerShape(16.dp),
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

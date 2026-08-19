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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
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

    ADPageLayout("Relay", onBack) {
        ADCard {
            Text("Server", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.size(6.dp))
            ADAiTextField(
                value = relayUrl,
                onValueChange = { relayUrl = it; saved = false },
                placeholder = "https://your-relay.example",
            )
        }

        ADCard {
            Text("Backend", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.size(5.dp))
            ADAiBackendRow(
                title = "Gemini",
                detail = "Gemini CLI through your relay",
                selected = backend == CliRelayBackend.GEMINI,
            ) {
                backend = CliRelayBackend.GEMINI
                saved = false
            }
            HorizontalDivider(color = ADColors.Separator)
            ADAiBackendRow(
                title = "OpenAI / Codex",
                detail = "Codex CLI through your relay",
                selected = backend == CliRelayBackend.CODEX,
            ) {
                backend = CliRelayBackend.CODEX
                saved = false
            }
        }

        Button(
            onClick = {
                AiProviderPrefs.setRelayBaseUrl(context, relayUrl)
                AiProviderPrefs.setRelayBackend(context, backend)
                AiProviderPrefs.setProvider(context, AiProviderType.CLI_RELAY)
                saved = true
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ADColors.Ink),
        ) {
            Text(if (saved) "Saved" else "Save relay")
        }

        Text(
            "Web Search and remote vision use this relay when those capabilities are requested.",
            style = MaterialTheme.typography.bodySmall,
            color = ADColors.Muted,
        )
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
        ADCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Memory, null, tint = ADColors.Blue, modifier = Modifier.size(19.dp))
                Text("On this phone", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 7.dp))
            }
            Spacer(Modifier.size(7.dp))
            if (installed.isEmpty()) {
                Text("No local model installed", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.size(2.dp))
                Text(
                    "Import a compatible model file to use on-device AI.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ADColors.Muted,
                )
            } else {
                installed.forEachIndexed { index, model ->
                    ADInstalledModelRow(
                        model = model,
                        selected = selectedId == model.id,
                        onClick = {
                            LocalModelStorageRepository.setSelectedModelId(context, model.id)
                            selectedId = model.id
                        },
                    )
                    if (index != installed.lastIndex) HorizontalDivider(color = ADColors.Separator)
                }
            }
            Spacer(Modifier.size(8.dp))
            Button(
                onClick = { importLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ADColors.Ink),
            ) {
                Icon(Icons.Outlined.Download, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(7.dp))
                Text("Import model file")
            }
            importStatus?.let {
                Spacer(Modifier.size(5.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
            }
        }

        ADCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Cloud, null, tint = ADColors.Blue, modifier = Modifier.size(19.dp))
                Column(Modifier.padding(start = 7.dp).weight(1f)) {
                    Text("OpenAI-compatible server", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Ollama, llama.cpp, vLLM or another compatible endpoint",
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
            Spacer(Modifier.size(9.dp))
            Text("Server address", style = MaterialTheme.typography.labelMedium, color = ADColors.Muted)
            Spacer(Modifier.size(4.dp))
            ADAiTextField(
                value = remoteUrl,
                onValueChange = { remoteUrl = it; remoteSaved = false },
                placeholder = "http://192.168.1.50:11434/v1",
            )
            Spacer(Modifier.size(8.dp))
            Text("Model", style = MaterialTheme.typography.labelMedium, color = ADColors.Muted)
            Spacer(Modifier.size(4.dp))
            ADAiTextField(
                value = remoteModel,
                onValueChange = { remoteModel = it; remoteSaved = false },
                placeholder = "model name",
            )
            Spacer(Modifier.size(8.dp))
            Text("API key", style = MaterialTheme.typography.labelMedium, color = ADColors.Muted)
            Spacer(Modifier.size(4.dp))
            ADAiTextField(
                value = remoteApiKey,
                onValueChange = { remoteApiKey = it; remoteSaved = false },
                placeholder = "Optional",
                password = true,
            )
            Spacer(Modifier.size(9.dp))
            Button(
                onClick = {
                    RemoteOpenAiPrefs.setBaseUrl(context, remoteUrl)
                    RemoteOpenAiPrefs.setModel(context, remoteModel)
                    RemoteOpenAiPrefs.setApiKey(context, remoteApiKey)
                    RemoteOpenAiPrefs.setEnabled(context, remoteEnabled)
                    remoteSaved = true
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ADColors.Ink),
            ) { Text(if (remoteSaved) "Saved" else "Save server") }
        }
    }
}

@Composable
private fun ADAiBackendRow(
    title: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
        }
        if (selected) {
            Icon(
                Icons.Outlined.CheckCircle,
                "Selected",
                tint = ADColors.Blue,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

@Composable
private fun ADInstalledModelRow(
    model: InstalledLocalModel,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(34.dp).background(ADColors.SurfaceSubtle, RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Storage, null, tint = ADColors.Ink, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.padding(start = 8.dp).weight(1f)) {
            Text(model.displayName, style = MaterialTheme.typography.titleMedium)
            Text(formatAiBytes(model.sizeBytes), style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
        }
        if (selected) {
            Icon(
                Icons.Outlined.CheckCircle,
                "Selected",
                tint = ADColors.Blue,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

@Composable
private fun ADAiTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    password: Boolean = false,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .background(ADColors.SurfaceSubtle, RoundedCornerShape(11.dp))
            .padding(horizontal = 11.dp, vertical = 10.dp),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = ADColors.Ink),
        cursorBrush = SolidColor(ADColors.Ink),
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        decorationBox = { field ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isBlank()) {
                    Text(placeholder, color = ADColors.Muted, style = MaterialTheme.typography.bodyMedium)
                }
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

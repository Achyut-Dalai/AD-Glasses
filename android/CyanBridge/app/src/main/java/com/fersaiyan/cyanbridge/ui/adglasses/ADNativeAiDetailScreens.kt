package com.fersaiyan.cyanbridge.ui.adglasses

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(44.dp).background(ADColors.CyanSoft, RoundedCornerShape(13.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Cloud, contentDescription = null, tint = ADColors.CyanDeep, modifier = Modifier.size(21.dp))
                }
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text("Remote AI relay", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Used for remote AI, Web Search and supported vision requests",
                        style = MaterialTheme.typography.bodySmall,
                        color = ADColors.Muted,
                    )
                }
                if (saved) ADStatusChip("SAVED", ADStatusTone.INFO)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("Server", style = MaterialTheme.typography.labelLarge, color = ADColors.Muted)
            ADCard {
                ADAiFieldLabel("Relay address")
                ADAiTextField(
                    value = relayUrl,
                    onValueChange = { relayUrl = it; saved = false },
                    placeholder = "https://your-relay.example",
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("Backend", style = MaterialTheme.typography.labelLarge, color = ADColors.Muted)
            ADCard {
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
        }

        Button(
            onClick = {
                AiProviderPrefs.setRelayBaseUrl(context, relayUrl)
                AiProviderPrefs.setRelayBackend(context, backend)
                AiProviderPrefs.setProvider(context, AiProviderType.CLI_RELAY)
                saved = true
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ADColors.Graphite),
        ) {
            Text(if (saved) "Relay saved" else "Save relay")
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
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            ADSectionTitle("On-device models")
            ADCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(42.dp).background(ADColors.CyanSoft, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.Memory, null, tint = ADColors.CyanDeep, modifier = Modifier.size(21.dp))
                    }
                    Column(Modifier.padding(start = 11.dp).weight(1f)) {
                        Text("On this phone", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (installed.isEmpty()) "No local model installed" else "${installed.size} model${if (installed.size == 1) "" else "s"} available",
                            style = MaterialTheme.typography.bodySmall,
                            color = ADColors.Muted,
                        )
                    }
                    if (selectedId != null) ADStatusChip("SELECTED", ADStatusTone.INFO)
                }

                if (installed.isNotEmpty()) {
                    Spacer(Modifier.size(10.dp))
                    HorizontalDivider(color = ADColors.Separator)
                    installed.forEachIndexed { index, model ->
                        ADInstalledModelRow(
                            model = model,
                            selected = selectedId == model.id,
                            onClick = {
                                LocalModelStorageRepository.setSelectedModelId(context, model.id)
                                selectedId = model.id
                            },
                        )
                        if (index != installed.lastIndex) HorizontalDivider(Modifier.padding(start = 50.dp), color = ADColors.Separator)
                    }
                } else {
                    Spacer(Modifier.size(10.dp))
                    Text(
                        "Import a compatible model file to run supported AI locally.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ADColors.Muted,
                    )
                }

                Spacer(Modifier.size(12.dp))
                Button(
                    onClick = { importLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = RoundedCornerShape(13.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ADColors.Graphite),
                ) {
                    Icon(Icons.Outlined.Download, null, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Import model file")
                }
                importStatus?.let {
                    Spacer(Modifier.size(8.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (it.contains("ready", ignoreCase = true)) ADColors.Success else ADColors.Muted,
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            ADSectionTitle("Network model server")
            ADCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(42.dp).background(ADColors.SurfaceSubtle, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.Cloud, null, tint = ADColors.Ink, modifier = Modifier.size(21.dp))
                    }
                    Column(Modifier.padding(start = 11.dp).weight(1f)) {
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

                Spacer(Modifier.size(14.dp))
                ADAiFieldLabel("Server address")
                Spacer(Modifier.size(5.dp))
                ADAiTextField(
                    value = remoteUrl,
                    onValueChange = { remoteUrl = it; remoteSaved = false },
                    placeholder = "http://192.168.1.50:11434/v1",
                )
                Spacer(Modifier.size(11.dp))
                ADAiFieldLabel("Model")
                Spacer(Modifier.size(5.dp))
                ADAiTextField(
                    value = remoteModel,
                    onValueChange = { remoteModel = it; remoteSaved = false },
                    placeholder = "model name",
                )
                Spacer(Modifier.size(11.dp))
                ADAiFieldLabel("API key")
                Spacer(Modifier.size(5.dp))
                ADAiTextField(
                    value = remoteApiKey,
                    onValueChange = { remoteApiKey = it; remoteSaved = false },
                    placeholder = "Optional",
                    password = true,
                )
                Spacer(Modifier.size(13.dp))
                Button(
                    onClick = {
                        RemoteOpenAiPrefs.setBaseUrl(context, remoteUrl)
                        RemoteOpenAiPrefs.setModel(context, remoteModel)
                        RemoteOpenAiPrefs.setApiKey(context, remoteApiKey)
                        RemoteOpenAiPrefs.setEnabled(context, remoteEnabled)
                        remoteSaved = true
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = RoundedCornerShape(13.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ADColors.Graphite),
                ) { Text(if (remoteSaved) "Server saved" else "Save server") }
            }
        }
    }
}

@Composable
private fun ADAiFieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = ADColors.Muted)
}

@Composable
private fun ADAiBackendRow(
    title: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
        }
        if (selected) {
            Icon(Icons.Outlined.CheckCircle, "Selected", tint = ADColors.CyanDeep, modifier = Modifier.size(21.dp))
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
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).background(
                if (selected) ADColors.CyanSoft else ADColors.SurfaceSubtle,
                RoundedCornerShape(12.dp),
            ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Storage,
                null,
                tint = if (selected) ADColors.CyanDeep else ADColors.Ink,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(Modifier.padding(start = 10.dp).weight(1f)) {
            Text(model.displayName, style = MaterialTheme.typography.titleMedium)
            Text(formatAiBytes(model.sizeBytes), style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
        }
        if (selected) Icon(Icons.Outlined.CheckCircle, "Selected", tint = ADColors.CyanDeep, modifier = Modifier.size(21.dp))
    }
}

@Composable
private fun ADAiTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    password: Boolean = false,
) {
    val shape = RoundedCornerShape(12.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .background(ADColors.SurfaceRaised, shape)
            .border(1.dp, ADColors.Outline, shape)
            .padding(horizontal = 13.dp, vertical = 12.dp),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = ADColors.Ink),
        cursorBrush = SolidColor(ADColors.CyanDeep),
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        decorationBox = { field ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isBlank()) Text(placeholder, color = ADColors.Muted, style = MaterialTheme.typography.bodyLarge)
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

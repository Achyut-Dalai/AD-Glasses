package com.fersaiyan.cyanbridge.ui.adglasses

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.font.FontWeight
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
        ADAiDetailHeader(
            title = "Remote intelligence",
            detail = if (relayUrl.isBlank()) "Add your relay endpoint" else relayUrl,
            active = relayUrl.isNotBlank(),
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ADSectionTitle("Server")
            ADAiTextField(
                value = relayUrl,
                onValueChange = { relayUrl = it; saved = false },
                placeholder = "https://your-relay.example",
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ADSectionTitle("Backend")
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = ADColors.Surface.copy(alpha = 0.88f),
                border = BorderStroke(1.dp, ADColors.Outline),
            ) {
                Column(Modifier.padding(horizontal = 10.dp)) {
                    ADAiBackendRow("Gemini", "Gemini CLI through your relay", backend == CliRelayBackend.GEMINI) {
                        backend = CliRelayBackend.GEMINI
                        saved = false
                    }
                    HorizontalDivider(color = ADColors.Separator)
                    ADAiBackendRow("OpenAI / Codex", "Codex CLI through your relay", backend == CliRelayBackend.CODEX) {
                        backend = CliRelayBackend.CODEX
                        saved = false
                    }
                }
            }
        }

        ADPrimaryButton(
            text = if (saved) "Saved" else "Save relay",
            onClick = {
                AiProviderPrefs.setRelayBaseUrl(context, relayUrl)
                AiProviderPrefs.setRelayBackend(context, backend)
                AiProviderPrefs.setProvider(context, AiProviderType.CLI_RELAY)
                saved = true
            },
        )

        Text(
            "Web search and remote vision use this relay only when requested.",
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
            }.onFailure { error -> importStatus = error.message ?: "Couldn’t import that model" }
        }
    }

    ADPageLayout("Local AI", onBack) {
        ADAiDetailHeader(
            title = "Private AI",
            detail = when {
                selectedId != null -> "A local model is selected"
                remoteEnabled -> "Network server is enabled"
                else -> "Choose how local AI should run"
            },
            active = selectedId != null || remoteEnabled,
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ADSectionTitle("On this phone")
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = ADColors.Surface.copy(alpha = 0.88f),
                border = BorderStroke(1.dp, ADColors.Outline),
            ) {
                Column(Modifier.padding(10.dp)) {
                    if (installed.isEmpty()) {
                        Text("No local model installed", style = MaterialTheme.typography.titleMedium)
                        Text("Import a compatible model file to use on-device AI.", style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
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
                    ADPrimaryButton(
                        text = "Import model file",
                        onClick = { importLauncher.launch(arrayOf("*/*")) },
                        icon = Icons.Outlined.Download,
                    )
                    importStatus?.let {
                        Text(it, modifier = Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ADSectionTitle("Network server")
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = ADColors.Surface.copy(alpha = 0.88f),
                border = BorderStroke(1.dp, ADColors.Outline),
            ) {
                Column(Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Cloud, null, tint = ADColors.Ink, modifier = Modifier.size(17.dp))
                        Column(Modifier.padding(start = 8.dp).weight(1f)) {
                            Text("OpenAI-compatible server", style = MaterialTheme.typography.titleMedium)
                            Text("Ollama, llama.cpp, vLLM or compatible endpoint", style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
                        }
                        Switch(
                            checked = remoteEnabled,
                            onCheckedChange = {
                                remoteEnabled = it
                                RemoteOpenAiPrefs.setEnabled(context, it)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = ADColors.Red,
                                uncheckedThumbColor = ADColors.Muted,
                                uncheckedTrackColor = ADColors.SurfaceSubtle,
                                uncheckedBorderColor = ADColors.Outline,
                            ),
                        )
                    }
                    Spacer(Modifier.size(9.dp))
                    ADAiFieldLabel("Server address")
                    ADAiTextField(remoteUrl, { remoteUrl = it; remoteSaved = false }, "http://192.168.1.50:11434/v1")
                    Spacer(Modifier.size(7.dp))
                    ADAiFieldLabel("Model")
                    ADAiTextField(remoteModel, { remoteModel = it; remoteSaved = false }, "model name")
                    Spacer(Modifier.size(7.dp))
                    ADAiFieldLabel("API key")
                    ADAiTextField(remoteApiKey, { remoteApiKey = it; remoteSaved = false }, "Optional", password = true)
                    Spacer(Modifier.size(9.dp))
                    ADPrimaryButton(
                        text = if (remoteSaved) "Saved" else "Save server",
                        onClick = {
                            RemoteOpenAiPrefs.setBaseUrl(context, remoteUrl)
                            RemoteOpenAiPrefs.setModel(context, remoteModel)
                            RemoteOpenAiPrefs.setApiKey(context, remoteApiKey)
                            RemoteOpenAiPrefs.setEnabled(context, remoteEnabled)
                            remoteSaved = true
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ADAiDetailHeader(title: String, detail: String, active: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color.Black.copy(alpha = 0.40f),
        border = BorderStroke(1.dp, ADColors.Outline),
    ) {
        Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
            ADGlyphIcon(ADGlyph.AI, ADColors.Ink, Modifier.size(22.dp), accent = if (active) ADColors.Red else null)
            Column(Modifier.padding(start = 9.dp).weight(1f)) {
                Text("AI CONFIG", style = MaterialTheme.typography.labelSmall, color = ADColors.Muted)
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted, maxLines = 1)
            }
            if (active) Box(Modifier.size(5.dp).background(ADColors.Red, CircleShape))
        }
    }
}

@Composable
private fun ADAiBackendRow(title: String, detail: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ADGlyphIcon(ADGlyph.AI, ADColors.Ink, Modifier.size(18.dp), accent = if (selected) ADColors.Red else null)
        Column(Modifier.padding(start = 9.dp).weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
        }
        if (selected) Icon(Icons.Outlined.CheckCircle, "Selected", tint = ADColors.Ink, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun ADInstalledModelRow(model: InstalledLocalModel, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Storage, null, tint = ADColors.Ink, modifier = Modifier.size(16.dp))
        Column(Modifier.padding(start = 8.dp).weight(1f)) {
            Text(model.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Text(formatAiBytes(model.sizeBytes), style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
        }
        if (selected) {
            Box(Modifier.size(5.dp).background(ADColors.Red, CircleShape))
            Spacer(Modifier.size(5.dp))
            Icon(Icons.Outlined.CheckCircle, "Selected", tint = ADColors.Ink, modifier = Modifier.size(15.dp))
        }
    }
}

@Composable
private fun ADAiFieldLabel(label: String) {
    Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = ADColors.Muted)
    Spacer(Modifier.size(4.dp))
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
            .background(ADColors.SurfaceSubtle, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = ADColors.Ink),
        cursorBrush = SolidColor(ADColors.Red),
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
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

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
import androidx.compose.material3.HorizontalDivider
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
        ADScreenIntro(
            eyebrow = "Remote AI",
            title = "Relay",
            detail = "Use your own relay when a task needs remote intelligence, fresh web data or remote vision.",
        )

        ADAiConfigSummary(
            glyph = ADMatrixGlyph.RELAY,
            label = "RELAY",
            title = if (relayUrl.isBlank()) "Not configured" else "Endpoint ready",
            detail = if (relayUrl.isBlank()) "Add a server address below" else relayUrl,
            active = relayUrl.isNotBlank(),
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ADSectionTitle("Server")
            ADAiFieldLabel("Relay address")
            ADAiTextField(
                value = relayUrl,
                onValueChange = { relayUrl = it; saved = false },
                placeholder = "https://your-relay.example",
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            ADSectionTitle("Backend")
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                ADAiBackendChoice(
                    title = "Gemini",
                    detail = "Gemini CLI",
                    selected = backend == CliRelayBackend.GEMINI,
                    modifier = Modifier.weight(1f),
                ) {
                    backend = CliRelayBackend.GEMINI
                    saved = false
                }
                ADAiBackendChoice(
                    title = "Codex",
                    detail = "OpenAI CLI",
                    selected = backend == CliRelayBackend.CODEX,
                    modifier = Modifier.weight(1f),
                ) {
                    backend = CliRelayBackend.CODEX
                    saved = false
                }
            }
        }

        ADPrimaryButton(
            text = if (saved) "Relay saved" else "Save relay",
            onClick = {
                AiProviderPrefs.setRelayBaseUrl(context, relayUrl)
                AiProviderPrefs.setRelayBackend(context, backend)
                AiProviderPrefs.setProvider(context, AiProviderType.CLI_RELAY)
                saved = true
            },
        )

        Text(
            "The relay is not used simply because it is configured. Routing still decides when remote processing is needed.",
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
        ADScreenIntro(
            eyebrow = "Private intelligence",
            title = "Local AI",
            detail = "Run a model on this phone, or point AD Glasses at an OpenAI-compatible server you control.",
        )

        ADAiConfigSummary(
            glyph = ADMatrixGlyph.LOCAL,
            label = "LOCAL ROUTE",
            title = when {
                selectedId != null -> "On-device model selected"
                remoteEnabled -> "Private server enabled"
                else -> "Choose a local route"
            },
            detail = when {
                selectedId != null -> installed.firstOrNull { it.id == selectedId }?.displayName ?: "Local model"
                remoteEnabled -> remoteUrl.ifBlank { "Server address required" }
                else -> "Nothing is active yet"
            },
            active = selectedId != null || remoteEnabled,
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ADSectionTitle("On this phone")
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = ADColors.Surface,
                border = BorderStroke(1.dp, ADColors.Outline),
            ) {
                Column(Modifier.padding(11.dp)) {
                    if (installed.isEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(34.dp).background(ADColors.SurfaceSubtle, RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                ADMatrixGlyphIcon(ADMatrixGlyph.LOCAL, ADColors.Muted, Modifier.size(19.dp))
                            }
                            Column(Modifier.padding(start = 9.dp).weight(1f)) {
                                Text("No model installed", style = MaterialTheme.typography.titleMedium, color = ADColors.Ink)
                                Text("Import a compatible model file to run AI on this phone.", style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
                            }
                        }
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
                    Spacer(Modifier.size(9.dp))
                    Surface(
                        onClick = { importLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = ADColors.SurfaceSubtle,
                        border = BorderStroke(1.dp, ADColors.Outline),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            ADMatrixGlyphIcon(ADMatrixGlyph.ADD, ADColors.Ink, Modifier.size(16.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("Import model file", style = MaterialTheme.typography.labelLarge, color = ADColors.Ink)
                        }
                    }
                    importStatus?.let {
                        Text(it, modifier = Modifier.padding(top = 7.dp), style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ADSectionTitle("Compatible server")
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = ADColors.Surface,
                border = BorderStroke(1.dp, ADColors.Outline),
            ) {
                Column(Modifier.padding(11.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(34.dp).background(ADColors.SurfaceSubtle, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            ADMatrixGlyphIcon(
                                ADMatrixGlyph.RELAY,
                                ADColors.Ink,
                                Modifier.size(19.dp),
                                accent = if (remoteEnabled) ADColors.Red else null,
                            )
                        }
                        Column(Modifier.padding(start = 9.dp).weight(1f)) {
                            Text("OpenAI-compatible server", style = MaterialTheme.typography.titleMedium, color = ADColors.Ink)
                            Text("Ollama, llama.cpp, vLLM or another compatible endpoint", style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
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

                    if (remoteEnabled) {
                        Spacer(Modifier.size(11.dp))
                        ADAiFieldLabel("Server address")
                        ADAiTextField(remoteUrl, { remoteUrl = it; remoteSaved = false }, "http://192.168.1.50:11434/v1")
                        Spacer(Modifier.size(8.dp))
                        ADAiFieldLabel("Model")
                        ADAiTextField(remoteModel, { remoteModel = it; remoteSaved = false }, "model name")
                        Spacer(Modifier.size(8.dp))
                        ADAiFieldLabel("API key")
                        ADAiTextField(remoteApiKey, { remoteApiKey = it; remoteSaved = false }, "Optional", password = true)
                        Spacer(Modifier.size(10.dp))
                        ADPrimaryButton(
                            text = if (remoteSaved) "Server saved" else "Save server",
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
}

@Composable
private fun ADAiConfigSummary(
    glyph: ADMatrixGlyph,
    label: String,
    title: String,
    detail: String,
    active: Boolean,
) {
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
                ADMatrixGlyphIcon(
                    glyph,
                    ADColors.Ink,
                    Modifier.size(25.dp),
                    accent = if (active) ADColors.Red else null,
                )
            }
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text(label, style = ADMetaTextStyle, color = ADColors.Muted)
                Text(title, style = MaterialTheme.typography.titleLarge, color = ADColors.Ink, fontWeight = FontWeight.SemiBold)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted, maxLines = 2)
            }
            Box(Modifier.size(6.dp).background(if (active) ADColors.Success else ADColors.Muted, CircleShape))
        }
    }
}

@Composable
private fun ADAiBackendChoice(
    title: String,
    detail: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 86.dp),
        shape = RoundedCornerShape(13.dp),
        color = if (selected) ADColors.SurfacePressed else ADColors.Surface,
        border = BorderStroke(1.dp, if (selected) ADColors.Ink.copy(alpha = .36f) else ADColors.Outline),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ADMatrixGlyphIcon(
                    ADMatrixGlyph.AI,
                    ADColors.Ink,
                    Modifier.size(19.dp),
                    accent = if (selected) ADColors.Red else null,
                )
                Spacer(Modifier.weight(1f))
                if (selected) ADMatrixGlyphIcon(ADMatrixGlyph.CHECK, ADColors.Success, Modifier.size(15.dp))
            }
            Column {
                Text(title, style = MaterialTheme.typography.labelLarge, color = ADColors.Ink, fontWeight = FontWeight.SemiBold)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
            }
        }
    }
}

@Composable
private fun ADInstalledModelRow(model: InstalledLocalModel, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(32.dp).background(ADColors.SurfaceSubtle, RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center,
        ) {
            ADMatrixGlyphIcon(ADMatrixGlyph.LOCAL, ADColors.Ink, Modifier.size(18.dp))
        }
        Column(Modifier.padding(start = 8.dp).weight(1f)) {
            Text(model.displayName, style = MaterialTheme.typography.titleMedium, color = ADColors.Ink, fontWeight = FontWeight.Medium)
            Text(formatAiBytes(model.sizeBytes), style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
        }
        if (selected) {
            ADMatrixGlyphIcon(ADMatrixGlyph.CHECK, ADColors.Success, Modifier.size(16.dp))
        }
    }
}

@Composable
private fun ADAiFieldLabel(label: String) {
    Text(label.uppercase(), style = ADMetaTextStyle, color = ADColors.Muted)
    Spacer(Modifier.size(4.dp))
}

@Composable
private fun ADAiTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    password: Boolean = false,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = ADColors.SurfaceSubtle,
        border = BorderStroke(1.dp, ADColors.Outline),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
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
}

private fun formatAiBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return "%.1f KB".format(Locale.US, kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return "%.1f MB".format(Locale.US, mb)
    return "%.1f GB".format(Locale.US, mb / 1024.0)
}

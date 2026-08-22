package com.fersaiyan.cyanbridge.ui.adglasses

import android.content.Intent
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
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.fersaiyan.cyanbridge.ai.router.AiProviderPrefs
import com.fersaiyan.cyanbridge.ai.router.AiProviderType
import com.fersaiyan.cyanbridge.ai.router.CliRelayBackend
import com.fersaiyan.cyanbridge.agent.LocalAgentPrefs
import com.fersaiyan.cyanbridge.ai.orchestrator.AssistantConversationSession
import com.fersaiyan.cyanbridge.ai.transcription.moonshine.MoonshineModelManager
import com.fersaiyan.cyanbridge.agent.LocalModelsConfigureActivity
import com.fersaiyan.cyanbridge.localmodels.remote.RemoteOpenAiPrefs
import com.fersaiyan.cyanbridge.localmodels.storage.InstalledLocalModel
import com.fersaiyan.cyanbridge.localmodels.storage.LocalModelStorageRepository
import com.fersaiyan.cyanbridge.shared.glasses.GlassesAssistantMode
import com.fersaiyan.cyanbridge.shared.settings.AgentProviderType
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
    val relayUrlAllowed = RemoteOpenAiPrefs.isCredentialTransportAllowed(relayUrl)

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
                val routeChanged = LocalAgentPrefs.getGlassesAssistantMode(context) !=
                    GlassesAssistantMode.CUSTOM_AI_PROVIDER ||
                    LocalAgentPrefs.getProviderType(context) != AgentProviderType.PRO_SUBSCRIPTION
                AiProviderPrefs.setRelayBaseUrl(context, relayUrl)
                AiProviderPrefs.setRelayBackend(context, backend)
                AiProviderPrefs.setProvider(context, AiProviderType.CLI_RELAY)
                LocalAgentPrefs.setProviderType(context, AgentProviderType.PRO_SUBSCRIPTION)
                LocalAgentPrefs.setGlassesAssistantMode(context, GlassesAssistantMode.CUSTOM_AI_PROVIDER)
                if (routeChanged) AssistantConversationSession.get(context).startNewConversation()
                saved = true
            },
            enabled = relayUrlAllowed,
            modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ADColors.Ink),
        ) {
            Text(if (saved) "Cloud AI selected" else "Save and use Cloud AI")
        }

        Text(
            "This is the explicit AD-owned cloud route: the provider returns text to AD, then AD speaks it. Saving never tests or contacts the server.",
            style = MaterialTheme.typography.bodySmall,
            color = ADColors.Muted,
        )
        if (relayUrl.isNotBlank() && !relayUrlAllowed) {
            Text(
                "Use HTTPS, or HTTP only for loopback, LAN, or Tailscale addresses.",
                style = MaterialTheme.typography.bodySmall,
                color = ADColors.Error,
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
    val moonshineKind = remember { MoonshineModelManager.chooseDefault() }
    var moonshineInstalled by remember { mutableStateOf(MoonshineModelManager.isInstalled(context, moonshineKind)) }
    var moonshineStatus by remember { mutableStateOf<String?>(null) }
    var moonshineInstalling by remember { mutableStateOf(false) }

    val configureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        installed = LocalModelStorageRepository.listInstalled(context)
        selectedId = LocalModelStorageRepository.getSelectedModelId(context)
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
                    "Download a recommended model or import a compatible GGUF/LiteRT file.",
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
                onClick = {
                    configureLauncher.launch(Intent(context, LocalModelsConfigureActivity::class.java))
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ADColors.Ink),
            ) {
                Text("Manage local models")
            }
        }

        ADCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Mic, null, tint = ADColors.Blue, modifier = Modifier.size(19.dp))
                Column(Modifier.padding(start = 7.dp).weight(1f)) {
                    Text("Offline English transcription", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (moonshineInstalled) {
                            "Ready · English model stored on this phone"
                        } else {
                            "Moonshine engine included · download its English model once"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = ADColors.Muted,
                    )
                }
            }
            if (!moonshineInstalled) {
                Spacer(Modifier.size(9.dp))
                Button(
                    enabled = !moonshineInstalling,
                    onClick = {
                        moonshineInstalling = true
                        moonshineStatus = "Starting download…"
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    MoonshineModelManager.installIfNeeded(context, moonshineKind) { progress ->
                                        scope.launch {
                                            moonshineStatus = "${progress.percent}% — ${progress.message}"
                                        }
                                    }
                                }
                            }.onSuccess {
                                moonshineInstalled = true
                                moonshineStatus = "Moonshine is ready for English voice input"
                            }.onFailure { error ->
                                moonshineStatus = error.message ?: "Moonshine installation failed"
                            }
                            moonshineInstalling = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ADColors.Ink),
                ) { Text(if (moonshineInstalling) "Downloading…" else "Download English model") }
            }
            moonshineStatus?.let {
                Spacer(Modifier.size(5.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = ADColors.Muted)
            }
            Spacer(Modifier.size(6.dp))
            Text(
                "Moonshine converts English speech to text. It is not AD’s response voice; AD uses Android text-to-speech for replies.",
                style = MaterialTheme.typography.bodySmall,
                color = ADColors.Muted,
            )
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

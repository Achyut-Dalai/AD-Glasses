package com.ad_glasses.ui.adglasses

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
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.ad_glasses.agent.CloudServerPrefs
import com.ad_glasses.agent.CloudSettingsActivity
import com.ad_glasses.agent.LocalAgentPrefs
import com.ad_glasses.agent.LocalModelsConfigureActivity
import com.ad_glasses.ai.live.GeminiLiveActivity
import com.ad_glasses.ai.orchestrator.AssistantConversationSession
import com.ad_glasses.ai.router.AiProviderPrefs
import com.ad_glasses.ai.router.AiProviderType
import com.ad_glasses.ai.router.ApiProvider
import com.ad_glasses.ai.transcription.moonshine.MoonshineModelManager
import com.ad_glasses.localmodels.storage.InstalledLocalModel
import com.ad_glasses.localmodels.storage.LocalModelStorageRepository
import com.ad_glasses.shared.settings.AgentProviderType
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun ADNativeCloudAiSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var provider by remember { mutableStateOf(AiProviderPrefs.getApiProvider(context)) }
    var apiKey by remember(provider) { mutableStateOf(AiProviderPrefs.getApiKey(context, provider)) }
    var model by remember(provider) { mutableStateOf(AiProviderPrefs.getModel(context, provider)) }
    var saved by remember { mutableStateOf(false) }
    val realtimeReady = AiProviderPrefs.isRelayConfigured(context) &&
        CloudServerPrefs.getApiToken(context).isNotBlank()

    ADPageLayout("Cloud AI", onBack) {
        ADCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Key, null, tint = ADColors.Blue, modifier = Modifier.size(19.dp))
                Column(Modifier.padding(start = 8.dp)) {
                    Text("Standard REST", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "AD receives the response text, keeps the conversation, and speaks it with Android TTS.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ADColors.Muted,
                    )
                }
            }
        }

        ADCard {
            Text("Provider", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.size(5.dp))
            ApiProvider.entries.forEachIndexed { index, item ->
                ADApiProviderRow(
                    provider = item,
                    selected = provider == item,
                    configured = AiProviderPrefs.isApiConfigured(context, item),
                    onClick = {
                        provider = item
                        apiKey = AiProviderPrefs.getApiKey(context, item)
                        model = AiProviderPrefs.getModel(context, item)
                        saved = false
                    },
                )
                if (index != ApiProvider.entries.lastIndex) HorizontalDivider(color = ADColors.Separator)
            }
        }

        ADCard {
            Text("${provider.label} API key", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.size(6.dp))
            ADAiTextField(
                value = apiKey,
                onValueChange = { apiKey = it; saved = false },
                placeholder = "Paste API key",
                secret = true,
            )
            Spacer(Modifier.size(10.dp))
            Text("Model", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.size(6.dp))
            ADAiTextField(
                value = model,
                onValueChange = { model = it; saved = false },
                placeholder = provider.defaultModel,
            )
        }

        Button(
            onClick = {
                val routeChanged = AiProviderPrefs.getProvider(context) != AiProviderType.CLOUD_API ||
                    AiProviderPrefs.getApiProvider(context) != provider
                AiProviderPrefs.setApiProvider(context, provider)
                AiProviderPrefs.setApiKey(context, provider, apiKey)
                AiProviderPrefs.setModel(context, provider, model)
                AiProviderPrefs.setProvider(context, AiProviderType.CLOUD_API)
                LocalAgentPrefs.setProviderType(context, AgentProviderType.CLOUD_AI)
                if (routeChanged) AssistantConversationSession.get(context).startNewConversation()
                saved = true
            },
            enabled = apiKey.isNotBlank() && model.isNotBlank(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ADColors.Ink),
        ) {
            Text(if (saved) "${provider.label} selected" else "Save and use ${provider.label}")
        }

        ADCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Mic, null, tint = ADColors.Blue, modifier = Modifier.size(19.dp))
                Column(Modifier.padding(start = 8.dp).weight(1f)) {
                    Text("Realtime", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (realtimeReady) "Gemini Live ready through AD's authenticated Realtime session service"
                        else "Configure the service used to authorize short-lived Gemini Live sessions",
                        style = MaterialTheme.typography.bodySmall,
                        color = ADColors.Muted,
                    )
                }
            }
            Spacer(Modifier.size(8.dp))
            Text(
                "Realtime is AD's own WebSocket audio path and stays inside AD Glasses. " +
                    "OpenAI Realtime can live in this same Cloud layer when its client is added.",
                style = MaterialTheme.typography.bodySmall,
                color = ADColors.Muted,
            )
            Spacer(Modifier.size(9.dp))
            Button(
                onClick = { context.startActivity(Intent(context, CloudSettingsActivity::class.java)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ADColors.Ink),
            ) {
                Text("Configure Realtime service")
            }
            Spacer(Modifier.size(6.dp))
            OutlinedButton(
                onClick = { context.startActivity(Intent(context, GeminiLiveActivity::class.java)) },
                enabled = realtimeReady,
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
            ) {
                Text("Open Gemini Live preview")
            }
        }

        Text(
            "Provider API keys and Realtime session credentials are stored with Android Keystore-backed encrypted preferences. " +
                "Standard REST talks directly to the selected provider; the authenticated Realtime service is scoped to AD-owned session authorization.",
            style = MaterialTheme.typography.bodySmall,
            color = ADColors.Muted,
        )
    }
}

@Composable
private fun ADApiProviderRow(
    provider: ApiProvider,
    selected: Boolean,
    configured: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(provider.label, style = MaterialTheme.typography.titleMedium)
            Text(
                if (configured) "Key saved · ${AiProviderLabelModel(provider)}" else "Add your API key",
                style = MaterialTheme.typography.bodySmall,
                color = ADColors.Muted,
            )
        }
        if (selected) {
            Icon(Icons.Outlined.CheckCircle, "Selected", tint = ADColors.Blue, modifier = Modifier.size(19.dp))
        }
    }
}

private fun AiProviderLabelModel(provider: ApiProvider): String = provider.defaultModel

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
                installed.forEachIndexed { index, item ->
                    ADInstalledModelRow(
                        model = item,
                        selected = selectedId == item.id,
                        onClick = {
                            LocalModelStorageRepository.setSelectedModelId(context, item.id)
                            selectedId = item.id
                        },
                    )
                    if (index != installed.lastIndex) HorizontalDivider(color = ADColors.Separator)
                }
            }
            Spacer(Modifier.size(8.dp))
            Button(
                onClick = { configureLauncher.launch(Intent(context, LocalModelsConfigureActivity::class.java)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ADColors.Ink),
            ) { Text("Manage local models") }
        }

        ADCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Mic, null, tint = ADColors.Blue, modifier = Modifier.size(19.dp))
                Column(Modifier.padding(start = 7.dp).weight(1f)) {
                    Text("Offline English transcription", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (moonshineInstalled) "Ready · English model stored on this phone"
                        else "Moonshine engine included · download its English model once",
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
                                        scope.launch { moonshineStatus = "${progress.percent}% — ${progress.message}" }
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
                "Moonshine converts English speech to text. Standard Cloud REST replies use Android text-to-speech; Realtime cloud sessions return their own audio stream.",
                style = MaterialTheme.typography.bodySmall,
                color = ADColors.Muted,
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
            Icon(Icons.Outlined.CheckCircle, "Selected", tint = ADColors.Blue, modifier = Modifier.size(19.dp))
        }
    }
}

@Composable
private fun ADAiTextField(
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

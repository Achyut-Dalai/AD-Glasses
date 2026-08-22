package com.fersaiyan.cyanbridge.agent

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.fersaiyan.cyanbridge.ai.router.AiProviderPrefs
import com.fersaiyan.cyanbridge.ui.setThemedComposeContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Optional AD-owned relay configuration for short-lived Realtime credentials. */
class CloudSettingsActivity : AppCompatActivity() {
    private var relayUrl by mutableStateOf("")
    private var relayToken by mutableStateOf("")
    private var status by mutableStateOf("")
    private var relayUrlError by mutableStateOf<String?>(null)
    private var testing by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        relayUrl = AiProviderPrefs.getRelayBaseUrl(this)
        relayToken = CloudServerPrefs.getApiToken(this)
        status = if (AiProviderPrefs.isRelayConfigured(this)) {
            "Realtime relay configured"
        } else {
            "Realtime relay is not configured"
        }

        setThemedComposeContent {
            CloudRelaySettingsScreen(
                relayUrl = relayUrl,
                relayToken = relayToken,
                status = status,
                relayUrlError = relayUrlError,
                testing = testing,
                onBack = ::finish,
                onRelayUrlChange = {
                    relayUrl = it
                    relayUrlError = null
                },
                onRelayTokenChange = { relayToken = it },
                onSave = { save(showConfirmation = true) },
                onTest = ::testConnection,
            )
        }
    }

    private fun save(showConfirmation: Boolean): Boolean {
        val url = relayUrl.trim().trimEnd('/')
        if (url.isNotBlank() && !url.startsWith("https://") && !url.startsWith("http://")) {
            relayUrlError = "Use a full http:// or https:// URL"
            return false
        }
        relayUrl = url
        relayUrlError = null
        AiProviderPrefs.setRelayBaseUrl(this, url)
        CloudServerPrefs.setApiToken(this, relayToken)
        status = if (url.isBlank()) "Realtime relay is not configured" else "Realtime relay configured"
        if (showConfirmation) Toast.makeText(this, "Realtime relay saved", Toast.LENGTH_SHORT).show()
        return true
    }

    private fun testConnection() {
        if (!save(showConfirmation = false)) return
        testing = true
        status = "Testing relay…"
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                CloudRelayClient.fetchAvailableModels(this@CloudSettingsActivity)
            }
            testing = false
            status = result.fold(
                onSuccess = { models -> "Connected. ${models.size} model(s) reported by relay." },
                onFailure = { error -> CloudRelayClient.relayUnavailableHint(error) ?: error.message ?: "Connection failed" },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@androidx.compose.runtime.Composable
private fun CloudRelaySettingsScreen(
    relayUrl: String,
    relayToken: String,
    status: String,
    relayUrlError: String?,
    testing: Boolean,
    onBack: () -> Unit,
    onRelayUrlChange: (String) -> Unit,
    onRelayTokenChange: (String) -> Unit,
    onSave: () -> Unit,
    onTest: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Realtime relay") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "This relay is optional. AD uses it for short-lived Realtime session credentials such as Gemini Live. " +
                    "Standard REST provider API keys are stored separately and are never entered here.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = relayUrl,
                onValueChange = onRelayUrlChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Relay base URL") },
                placeholder = { Text("https://…") },
                singleLine = true,
                isError = relayUrlError != null,
                supportingText = relayUrlError?.let { error -> { Text(error) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
            OutlinedTextField(
                value = relayToken,
                onValueChange = onRelayTokenChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Relay access token") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )
            Text(
                "The relay token is stored in Android Keystore-backed encrypted preferences.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                Text("Save relay")
            }
            OutlinedButton(onClick = onTest, enabled = !testing, modifier = Modifier.fillMaxWidth()) {
                Text(if (testing) "Testing…" else "Save and test connection")
            }
            Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

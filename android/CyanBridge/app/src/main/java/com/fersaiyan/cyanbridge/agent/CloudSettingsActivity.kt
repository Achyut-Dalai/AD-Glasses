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

/** Configuration for infrastructure owned by the AD Glasses user. */
class CloudSettingsActivity : AppCompatActivity() {
    private var relayUrl by mutableStateOf("")
    private var apiToken by mutableStateOf("")
    private var accountEmail by mutableStateOf("")
    private var requestsModel by mutableStateOf("")
    private var questionsModel by mutableStateOf("")
    private var tasksModel by mutableStateOf("")
    private var status by mutableStateOf("")
    private var relayUrlError by mutableStateOf<String?>(null)
    private var testing by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        relayUrl = AiProviderPrefs.getRelayBaseUrl(this)
        apiToken = CloudServerPrefs.getApiToken(this)
        accountEmail = CloudServerPrefs.getAccountEmail(this)
        requestsModel = CloudAiPrefs.getRequestsModel(this)
        questionsModel = CloudAiPrefs.getQuestionsModel(this)
        tasksModel = CloudAiPrefs.getTasksModel(this)
        status = if (AiProviderPrefs.isRelayConfigured(this)) {
            "Cloud relay configured"
        } else {
            "Cloud relay is not configured"
        }

        setThemedComposeContent {
            CloudSettingsScreen(
                relayUrl = relayUrl,
                apiToken = apiToken,
                accountEmail = accountEmail,
                requestsModel = requestsModel,
                questionsModel = questionsModel,
                tasksModel = tasksModel,
                status = status,
                relayUrlError = relayUrlError,
                testing = testing,
                onBack = ::finish,
                onRelayUrlChange = {
                    relayUrl = it
                    relayUrlError = null
                },
                onApiTokenChange = { apiToken = it },
                onAccountEmailChange = { accountEmail = it },
                onRequestsModelChange = { requestsModel = it },
                onQuestionsModelChange = { questionsModel = it },
                onTasksModelChange = { tasksModel = it },
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
        CloudServerPrefs.setApiToken(this, apiToken)
        CloudServerPrefs.setAccountEmail(this, accountEmail)
        CloudAiPrefs.setRequestsModel(this, requestsModel)
        CloudAiPrefs.setQuestionsModel(this, questionsModel)
        CloudAiPrefs.setTasksModel(this, tasksModel)
        status = if (url.isBlank()) "Cloud relay is not configured" else "Cloud relay configured"
        if (showConfirmation) Toast.makeText(this, "Cloud configuration saved", Toast.LENGTH_SHORT).show()
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
                onSuccess = { models -> "Connected. ${models.size} model(s) available." },
                onFailure = { error -> error.message ?: "Connection failed" },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@androidx.compose.runtime.Composable
private fun CloudSettingsScreen(
    relayUrl: String,
    apiToken: String,
    accountEmail: String,
    requestsModel: String,
    questionsModel: String,
    tasksModel: String,
    status: String,
    relayUrlError: String?,
    testing: Boolean,
    onBack: () -> Unit,
    onRelayUrlChange: (String) -> Unit,
    onApiTokenChange: (String) -> Unit,
    onAccountEmailChange: (String) -> Unit,
    onRequestsModelChange: (String) -> Unit,
    onQuestionsModelChange: (String) -> Unit,
    onTasksModelChange: (String) -> Unit,
    onSave: () -> Unit,
    onTest: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cloud AI") },
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
                "Connect AD Glasses to infrastructure you control. No subscription or author account is required.",
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
                value = apiToken,
                onValueChange = onApiTokenChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API token (optional)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )
            OutlinedTextField(
                value = accountEmail,
                onValueChange = onAccountEmailChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Account email (optional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )
            OutlinedTextField(
                value = requestsModel,
                onValueChange = onRequestsModelChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Chat/request model") },
                singleLine = true,
            )
            OutlinedTextField(
                value = questionsModel,
                onValueChange = onQuestionsModelChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Image/question model") },
                singleLine = true,
            )
            OutlinedTextField(
                value = tasksModel,
                onValueChange = onTasksModelChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Automation/task model") },
                singleLine = true,
            )
            Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                Text("Save configuration")
            }
            OutlinedButton(onClick = onTest, enabled = !testing, modifier = Modifier.fillMaxWidth()) {
                Text(if (testing) "Testing…" else "Save and test connection")
            }
            Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

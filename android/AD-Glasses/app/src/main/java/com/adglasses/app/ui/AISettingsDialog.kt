package com.adglasses.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adglasses.app.core.assistant.AIProviderKind

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AISettingsDialog(vm: ADViewModel, dismiss: () -> Unit) {
    val configuration by vm.aiConfiguration.collectAsStateWithLifecycle()
    var editingNew by remember { mutableStateOf(configuration.activeProfile == null) }
    var provider by remember { mutableStateOf(configuration.activeProfile?.provider ?: AIProviderKind.OpenAI) }
    var name by remember { mutableStateOf(configuration.activeProfile?.name ?: provider.displayName) }
    var model by remember { mutableStateOf(configuration.activeProfile?.model ?: provider.defaultModel) }
    var endpoint by remember { mutableStateOf(configuration.activeProfile?.baseUrl ?: provider.defaultBaseUrl) }
    var apiKey by remember { mutableStateOf("") }
    var profileMenu by remember { mutableStateOf(false) }
    var providerMenu by remember { mutableStateOf(false) }

    LaunchedEffect(configuration.activeProfileId) {
        if (!editingNew) {
            configuration.activeProfile?.let { profile ->
                provider = profile.provider
                name = profile.name
                model = profile.model
                endpoint = profile.baseUrl
                apiKey = ""
            }
        }
    }

    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Cloud AI") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Profiles match the iOS provider model. API keys are encrypted with Android Keystore and never shown again after saving.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (configuration.profiles.isNotEmpty() && !editingNew) {
                    ExposedDropdownMenuBox(expanded = profileMenu, onExpandedChange = { profileMenu = it }) {
                        OutlinedTextField(
                            value = configuration.activeProfile?.name.orEmpty(),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Profile") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(profileMenu) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                        )
                        ExposedDropdownMenu(expanded = profileMenu, onDismissRequest = { profileMenu = false }) {
                            configuration.profiles.forEach { profile ->
                                DropdownMenuItem(
                                    text = { Text("${profile.name} • ${profile.provider.displayName}") },
                                    onClick = {
                                        profileMenu = false
                                        editingNew = false
                                        vm.setActiveAIProfile(profile)
                                    },
                                )
                            }
                        }
                    }
                }

                ExposedDropdownMenuBox(expanded = providerMenu, onExpandedChange = { providerMenu = it }) {
                    OutlinedTextField(
                        value = provider.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Provider") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(providerMenu) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                    )
                    ExposedDropdownMenu(expanded = providerMenu, onDismissRequest = { providerMenu = false }) {
                        AIProviderKind.entries.forEach { candidate ->
                            DropdownMenuItem(
                                text = { Text(candidate.displayName) },
                                onClick = {
                                    providerMenu = false
                                    if (candidate != provider) {
                                        provider = candidate
                                        name = candidate.displayName
                                        model = candidate.defaultModel
                                        endpoint = candidate.defaultBaseUrl
                                        apiKey = ""
                                    }
                                },
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Profile name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("Model") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!provider.managesEndpoint) {
                    OutlinedTextField(
                        value = endpoint,
                        onValueChange = { endpoint = it },
                        label = { Text("HTTPS base URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text(if (editingNew || configuration.activeProfile == null) "API key" else "API key • leave blank to keep current") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = {
                            editingNew = true
                            provider = AIProviderKind.OpenAI
                            name = provider.displayName
                            model = provider.defaultModel
                            endpoint = provider.defaultBaseUrl
                            apiKey = ""
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("New profile") }
                    if (!editingNew && configuration.activeProfile != null) {
                        TextButton(
                            onClick = {
                                vm.deleteActiveAIProfile()
                                editingNew = configuration.profiles.size <= 1
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("Remove") }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val saved = if (editingNew) {
                        vm.addAIProfile(provider, name, model, endpoint, apiKey)
                    } else {
                        vm.saveAIProfile(provider, name, model, endpoint, apiKey)
                    }
                    if (saved) dismiss()
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } },
    )
}

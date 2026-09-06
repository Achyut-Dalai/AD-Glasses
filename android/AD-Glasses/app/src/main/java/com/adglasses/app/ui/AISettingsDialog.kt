package com.adglasses.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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

    Dialog(
        onDismissRequest = dismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                topBar = {
                    CenterAlignedTopAppBar(
                        title = { Text("Settings", fontWeight = FontWeight.SemiBold) },
                        navigationIcon = {
                            IconButton(onClick = dismiss) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                        ),
                    )
                },
            ) { inner ->
                ADAmbientBackground(Modifier.padding(inner)) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        item {
                            Column(
                                modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    "Cloud AI",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    "Choose the model AD uses for conversation. Hardware actions remain deterministic and separate from model output.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        item {
                            ADGroupedCard(
                                modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
                                cornerRadius = 20.dp,
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Icon(Icons.Filled.SmartToy, null, tint = ADAccent.Indigo)
                                        Column(Modifier.weight(1f)) {
                                            Text("AI profile", fontWeight = FontWeight.SemiBold)
                                            Text(
                                                if (editingNew) "Create a new provider profile" else configuration.activeProfile?.name.orEmpty(),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }

                                    if (configuration.profiles.isNotEmpty() && !editingNew) {
                                        ExposedDropdownMenuBox(
                                            expanded = profileMenu,
                                            onExpandedChange = { profileMenu = it },
                                        ) {
                                            OutlinedTextField(
                                                value = configuration.activeProfile?.name.orEmpty(),
                                                onValueChange = {},
                                                readOnly = true,
                                                label = { Text("Profile") },
                                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(profileMenu) },
                                                modifier = Modifier.menuAnchor().fillMaxWidth(),
                                            )
                                            ExposedDropdownMenu(
                                                expanded = profileMenu,
                                                onDismissRequest = { profileMenu = false },
                                            ) {
                                                configuration.profiles.forEach { profile ->
                                                    DropdownMenuItem(
                                                        text = { Text("${profile.name} · ${profile.provider.displayName}") },
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

                                    ExposedDropdownMenuBox(
                                        expanded = providerMenu,
                                        onExpandedChange = { providerMenu = it },
                                    ) {
                                        OutlinedTextField(
                                            value = provider.displayName,
                                            onValueChange = {},
                                            readOnly = true,
                                            label = { Text("Provider") },
                                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(providerMenu) },
                                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                                        )
                                        ExposedDropdownMenu(
                                            expanded = providerMenu,
                                            onDismissRequest = { providerMenu = false },
                                        ) {
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
                                        label = {
                                            Text(
                                                if (editingNew || configuration.activeProfile == null) {
                                                    "API key"
                                                } else {
                                                    "API key · leave blank to keep current"
                                                },
                                            )
                                        },
                                        singleLine = true,
                                        visualTransformation = PasswordVisualTransformation(),
                                        modifier = Modifier.fillMaxWidth(),
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
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
                            }
                        }

                        item {
                            Surface(
                                modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Icon(Icons.Filled.Lock, null, tint = ADAccent.Green)
                                    Text(
                                        "API keys are encrypted with Android Keystore and are never shown again after saving.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        item {
                            Button(
                                onClick = {
                                    val saved = if (editingNew) {
                                        vm.addAIProfile(provider, name, model, endpoint, apiKey)
                                    } else {
                                        vm.saveAIProfile(provider, name, model, endpoint, apiKey)
                                    }
                                    if (saved) dismiss()
                                },
                                modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().height(52.dp),
                            ) { Text("Save settings") }
                        }

                        item { Spacer(Modifier.height(20.dp)) }
                    }
                }
            }
        }
    }
}

package com.ad_glasses.shared.ui.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.selection.SelectionContainer
import com.ad_glasses.shared.generated.resources.*
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class, ExperimentalResourceApi::class)
@Composable
fun TranscriptionDebugScreen(
    endpointUrl: String,
    apiKey: String,
    useHttp: Boolean,
    transcriptStorageEnabled: Boolean,
    latestSessionInfo: String,
    isTranscribing: Boolean,
    progress: Int,
    progressText: String,
    persistedText: String,
    output: String,
    onEndpointUrlChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onUseHttpChange: (Boolean) -> Unit,
    onStorageEnabledChange: (Boolean) -> Unit,
    onSaveEndpoint: () -> Unit,
    onLoadLatest: () -> Unit,
    onTranscribe: () -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
         topBar = { TopAppBar(title = { Text(stringResource(Res.string.diagnostics_title)) }) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                     stringResource(Res.string.diagnostics_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                         Text(stringResource(Res.string.diagnostics_provider), style = MaterialTheme.typography.titleSmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = !useHttp, onClick = { onUseHttpChange(false) })
                             Text(stringResource(Res.string.diagnostics_fake))
                            RadioButton(selected = useHttp, onClick = { onUseHttpChange(true) })
                             Text(stringResource(Res.string.diagnostics_http))
                        }
                        OutlinedTextField(
                            value = endpointUrl,
                            onValueChange = onEndpointUrlChange,
                             label = { Text(stringResource(Res.string.diagnostics_endpoint)) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = useHttp,
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = onApiKeyChange,
                             label = { Text(stringResource(Res.string.diagnostics_api_key)) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = useHttp,
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                             Text(stringResource(Res.string.diagnostics_persist), modifier = Modifier.weight(1f))
                            Switch(checked = transcriptStorageEnabled, onCheckedChange = onStorageEnabledChange)
                        }
                         TextButton(onClick = onSaveEndpoint) { Text(stringResource(Res.string.diagnostics_save_endpoint)) }
                    }
                }
            }
            item {
                FilledTonalButton(onClick = onLoadLatest, modifier = Modifier.fillMaxWidth()) {
                     Text(stringResource(Res.string.diagnostics_load_latest))
                }
            }
            item {
                Text(latestSessionInfo, style = MaterialTheme.typography.bodySmall)
            }
            item {
                FilledTonalButton(
                    onClick = onTranscribe,
                    enabled = !isTranscribing,
                    modifier = Modifier.fillMaxWidth(),
                 ) {
                     Text(
                         if (isTranscribing) {
                             stringResource(Res.string.diagnostics_transcribing)
                         } else {
                             stringResource(Res.string.diagnostics_transcribe_latest)
                         },
                     )
                 }
            }
            if (isTranscribing || progressText.isNotBlank()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LinearProgressIndicator(progress = { progress.coerceIn(0, 100) / 100f }, modifier = Modifier.fillMaxWidth())
                        Text(progressText, style = MaterialTheme.typography.bodySmall)
                        if (persistedText.isNotBlank()) Text(persistedText, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (output.isNotBlank()) {
                item {
                     Text(stringResource(Res.string.diagnostics_transcript_output), style = MaterialTheme.typography.titleSmall)
                    Card(modifier = Modifier.fillMaxWidth()) {
                        SelectionContainer(modifier = Modifier.padding(16.dp)) {
                            Text(output, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

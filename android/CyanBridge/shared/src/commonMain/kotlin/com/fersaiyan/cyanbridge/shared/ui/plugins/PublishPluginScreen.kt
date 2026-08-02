package com.fersaiyan.cyanbridge.shared.ui.plugins

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fersaiyan.cyanbridge.shared.plugins.PublishPluginUiState
import com.fersaiyan.cyanbridge.shared.generated.resources.*
import com.fersaiyan.cyanbridge.shared.ui.localizedPluginCategory
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class, ExperimentalResourceApi::class)
@Composable
fun PublishPluginScreen(
    state: PublishPluginUiState,
    categories: List<String>,
    onTitleChanged: (String) -> Unit,
    onAuthorChanged: (String) -> Unit,
    onDescriptionChanged: (String) -> Unit,
    onCategorySelected: (String) -> Unit,
    onTaskerNetLinkChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                 title = { Text(stringResource(Res.string.publish_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                             contentDescription = stringResource(Res.string.action_back),
                        )
                    }
                },
            )
        },
        bottomBar = {
            Surface(modifier = Modifier.imePadding(), tonalElevation = 3.dp) {
                Button(
                    onClick = onSubmit,
                    enabled = !state.isSubmitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .testTag("publish_plugin_submit"),
                ) {
                    if (state.isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(8.dp))
                         Text(stringResource(Res.string.publish_submitting))
                    } else {
                         Text(stringResource(Res.string.publish_submit))
                    }
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                 text = stringResource(Res.string.publish_share_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                         text = stringResource(Res.string.publish_details),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    PluginTextField(
                        value = state.title,
                        onValueChange = onTitleChanged,
                         label = stringResource(Res.string.publish_title_field),
                        error = state.titleError,
                        tag = "publish_plugin_title",
                    )
                    PluginTextField(
                        value = state.author,
                        onValueChange = onAuthorChanged,
                         label = stringResource(Res.string.publish_author_field),
                        error = state.authorError,
                        tag = "publish_plugin_author",
                    )
                    PluginTextField(
                        value = state.description,
                        onValueChange = onDescriptionChanged,
                         label = stringResource(Res.string.publish_description_field),
                        error = state.descriptionError,
                        tag = "publish_plugin_description",
                        minLines = 3,
                    )
                    Text(
                         text = stringResource(Res.string.publish_description_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                     Text(stringResource(Res.string.publish_category), style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        categories.forEach { category ->
                            FilterChip(
                                selected = state.category == category,
                                onClick = { onCategorySelected(category) },
                                 label = { Text(localizedPluginCategory(category)) },
                                modifier = Modifier.testTag("publish_plugin_category_$category"),
                            )
                        }
                    }
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                         text = stringResource(Res.string.publish_download_link),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    PluginTextField(
                        value = state.taskerNetLink,
                        onValueChange = onTaskerNetLinkChanged,
                         label = stringResource(Res.string.publish_taskernet_link),
                        error = state.taskerNetLinkError,
                        tag = "publish_plugin_link",
                        keyboardType = KeyboardType.Uri,
                    )
                    Text(
                         text = stringResource(Res.string.publish_taskernet_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                         text = stringResource(Res.string.publish_how_it_works),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                         text = stringResource(Res.string.publish_steps),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun PluginTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
    tag: String,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag),
        label = { Text(label) },
        minLines = minLines,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    )
}

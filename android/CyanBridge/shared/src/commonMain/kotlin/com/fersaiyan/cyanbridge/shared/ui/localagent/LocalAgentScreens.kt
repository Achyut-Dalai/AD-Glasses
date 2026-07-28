package com.fersaiyan.cyanbridge.shared.ui.localagent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.selection.SelectionContainer

data class BlacklistAppItem(
    val packageName: String,
    val label: String,
    val isSystemApp: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalAgentDocumentScreen(
    title: String,
    path: String,
    text: String,
    hint: String,
    editable: Boolean,
    primaryLabel: String,
    onTextChange: (String) -> Unit,
    onPrimary: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    onBack: () -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                label = { Text(hint) },
                readOnly = !editable,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                secondaryLabel?.let { label ->
                    TextButton(onClick = onSecondary ?: {}) { Text(label) }
                    Spacer(Modifier.width(8.dp))
                }
                FilledTonalButton(onClick = onPrimary) { Text(primaryLabel) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingActionsScreen(
    pendingCount: Int,
    renderedAction: String,
    hasPendingAction: Boolean,
    onRefresh: () -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("Pending actions") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Pending: $pendingCount",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Card(modifier = Modifier.weight(1f).fillMaxWidth()) {
                SelectionContainer(modifier = Modifier.padding(16.dp)) {
                    Text(
                        renderedAction,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onReject, enabled = hasPendingAction, modifier = Modifier.weight(1f)) {
                    Text("Reject")
                }
                FilledTonalButton(onClick = onApprove, enabled = hasPendingAction, modifier = Modifier.weight(1f)) {
                    Text("Approve")
                }
                TextButton(onClick = onRefresh, modifier = Modifier.weight(1f)) { Text("Refresh") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailySummaryScreen(
    title: String,
    path: String,
    status: String,
    summary: String,
    isBusy: Boolean,
    progress: Int,
    progressTitle: String,
    progressDetail: String,
    onRefresh: () -> Unit,
    onRegenerate: () -> Unit,
    onShare: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (isBusy) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(progressTitle, style = MaterialTheme.typography.titleSmall)
                        LinearProgressIndicator(progress = { progress.coerceIn(0, 100) / 100f }, modifier = Modifier.fillMaxWidth())
                        Text(progressDetail, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Card(modifier = Modifier.weight(1f).fillMaxWidth()) {
                SelectionContainer(modifier = Modifier.padding(16.dp)) {
                    Text(summary, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRefresh, enabled = !isBusy, modifier = Modifier.weight(1f)) { Text("Refresh") }
                FilledTonalButton(onClick = onRegenerate, enabled = !isBusy, modifier = Modifier.weight(1f)) { Text("Regenerate") }
                TextButton(onClick = onShare, enabled = !isBusy, modifier = Modifier.weight(1f)) { Text("Share") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBlacklistScreen(
    apps: List<BlacklistAppItem>,
    totalCount: Int,
    query: String,
    hideSystemApps: Boolean,
    selectedPackages: Set<String>,
    isLoading: Boolean,
    onQueryChange: (String) -> Unit,
    onHideSystemAppsChange: (Boolean) -> Unit,
    onTogglePackage: (String) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("Blacklist apps") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = if (isLoading) "Loading installed apps..." else "${apps.size} / $totalCount apps",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                label = { Text("Search") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                singleLine = true,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Hide system apps", modifier = Modifier.weight(1f))
                Switch(checked = hideSystemApps, onCheckedChange = onHideSystemAppsChange)
            }
            HorizontalDivider()
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(apps, key = { it.packageName }) { app ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = app.packageName in selectedPackages,
                            onCheckedChange = { onTogglePackage(app.packageName) },
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(app.label, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = buildString {
                                    append(app.packageName)
                                    if (app.isSystemApp) append(" · System")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            FilledTonalButton(onClick = onSave, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Text("Save blacklist")
            }
        }
    }
}

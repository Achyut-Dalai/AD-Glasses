package com.fersaiyan.cyanbridge.ui.plugins

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fersaiyan.cyanbridge.shared.navigation.AppDestination
import com.fersaiyan.cyanbridge.shared.plugins.CommunityPluginCardData
import com.fersaiyan.cyanbridge.shared.plugins.PluginTimeWindow
import com.fersaiyan.cyanbridge.ui.navigation.CyanBridgeNavigationBar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityPluginsScreen(
    plugins: List<CommunityPluginCardData>,
    selectedWindow: PluginTimeWindow,
    imageAutomationEnabled: Boolean,
    showImageAutomationBanner: Boolean,
    isRefreshing: Boolean,
    onWindowSelected: (PluginTimeWindow) -> Unit,
    onRefresh: () -> Unit,
    onDismissImageAutomationBanner: () -> Unit,
    onOpenTaskerStore: () -> Unit,
    onOpenTaskerNet: () -> Unit,
    onPublishPlugin: () -> Unit,
    onDestinationSelected: (AppDestination) -> Unit,
) {
    var showTaskerSetupDialog by rememberSaveable { mutableStateOf(false) }
    var showHideBannerDialog by rememberSaveable { mutableStateOf(false) }
    val trending = plugins.sortedByDescending { it.trend(selectedWindow) }.take(4)
    val topVoted = plugins.sortedByDescending { it.votes(selectedWindow) }.take(4)
    val topDownloaded = plugins.sortedByDescending { it.downloads(selectedWindow) }.take(4)

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("Community Plugins") },
                actions = {
                    IconButton(
                        onClick = onRefresh,
                        enabled = !isRefreshing,
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = "Refresh plugins",
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            CyanBridgeNavigationBar(
                selectedDestination = AppDestination.PLUGINS,
                onDestinationSelected = onDestinationSelected,
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onPublishPlugin) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = "Publish plugin",
                )
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .testTag("community_plugins_list"),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                top = 16.dp,
                end = 16.dp,
                bottom = 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { PluginHero() }
            item {
                Text(
                    text = "Future notice: top plugins will have real cash prizes for developers, proportional to the number of Pro subscription users with the plugin enabled.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (showImageAutomationBanner) {
                item {
                    ImageAutomationCard(
                        enabled = imageAutomationEnabled,
                        onOpenSetup = {
                            showTaskerSetupDialog = true
                        },
                        onDismiss = { showHideBannerDialog = true },
                    )
                }
            }
            item {
                PeriodFilter(
                    selectedWindow = selectedWindow,
                    onWindowSelected = onWindowSelected,
                )
            }
            item { PluginSectionLabel("Trending plugins") }
            itemsIndexed(trending, key = { _, plugin -> "trending-${plugin.title}" }) { index, plugin ->
                PluginCard(plugin = plugin, rank = index + 1, window = selectedWindow)
            }
            item { PluginSectionLabel("Top voted") }
            itemsIndexed(topVoted, key = { _, plugin -> "voted-${plugin.title}" }) { index, plugin ->
                PluginCard(plugin = plugin, rank = index + 1, window = selectedWindow)
            }
            item { PluginSectionLabel("Top downloaded") }
            itemsIndexed(topDownloaded, key = { _, plugin -> "downloaded-${plugin.title}" }) { index, plugin ->
                PluginCard(plugin = plugin, rank = index + 1, window = selectedWindow)
            }
        }
    }

    if (showTaskerSetupDialog) {
        AlertDialog(
            onDismissRequest = { showTaskerSetupDialog = false },
            title = { Text("Image Questions Automation") },
            text = {
                Text(
                    "Gemini and ChatGPT image questions need the Tasker automation profile. Install Tasker if needed, then open the TaskerNet profile.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showTaskerSetupDialog = false
                        onOpenTaskerNet()
                    },
                ) {
                    Text("Open TaskerNet")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showTaskerSetupDialog = false
                        onOpenTaskerStore()
                    },
                ) {
                    Text("Get Tasker")
                }
            },
        )
    }

    if (showHideBannerDialog) {
        AlertDialog(
            onDismissRequest = { showHideBannerDialog = false },
            title = { Text("Hide this banner?") },
            text = {
                Text(
                    "This hides the Image Questions Automation setup card. You can re-enable it from Settings if needed.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showHideBannerDialog = false
                        onDismissImageAutomationBanner()
                    },
                ) {
                    Text("Hide")
                }
            },
            dismissButton = {
                TextButton(onClick = { showHideBannerDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun PluginHero() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "Discover community-built automations",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = "Find trending plugins, compare what users love, and install new workflows in seconds.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun ImageAutomationCard(
    enabled: Boolean,
    onOpenSetup: () -> Unit,
    onDismiss: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("image_automation_banner"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Gemini/ChatGPT Image Questions Automation",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = "Required when using Gemini or ChatGPT image questions with Tasker automation.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = if (enabled) "Status: Downloaded and enabled" else "Status: Not downloaded",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpenSetup) {
                    Text(if (enabled) "Open setup" else "Download plugin")
                }
                TextButton(onClick = onDismiss) {
                    Text("Already have it")
                }
            }
        }
    }
}

@Composable
private fun PeriodFilter(
    selectedWindow: PluginTimeWindow,
    onWindowSelected: (PluginTimeWindow) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Filter / sort",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PluginTimeWindow.entries.forEach { window ->
                FilterChip(
                    selected = window == selectedWindow,
                    onClick = { onWindowSelected(window) },
                    label = { Text(window.label) },
                )
            }
        }
    }
}

@Composable
private fun PluginSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun PluginCard(
    plugin: CommunityPluginCardData,
    rank: Int,
    window: PluginTimeWindow,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$rank. ${plugin.title}",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        text = plugin.badge,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            Text(
                text = "by ${plugin.author}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = plugin.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "${formatCount(plugin.downloads(window))} downloads",
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    text = "${formatCount(plugin.votes(window))} votes",
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    text = "${window.label.lowercase(Locale.US)} trend ${plugin.trend(window)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private val PluginTimeWindow.label: String
    get() = when (this) {
        PluginTimeWindow.ALL_TIME -> "All time"
        PluginTimeWindow.WEEKLY -> "Weekly"
        PluginTimeWindow.MONTHLY -> "Monthly"
    }

private fun formatCount(value: Int): String = when {
    value >= 1_000_000 -> String.format(Locale.US, "%.1fM", value / 1_000_000f)
    value >= 1_000 -> String.format(Locale.US, "%.1fk", value / 1_000f)
    else -> value.toString()
}

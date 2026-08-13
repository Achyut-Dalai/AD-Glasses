package com.achyut.adglasses.shared.ui.glasses

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.achyut.adglasses.shared.generated.resources.*
import com.achyut.adglasses.shared.glasses.GlassesAssistantMode
import com.achyut.adglasses.shared.glasses.GlassesDashboardAction
import com.achyut.adglasses.shared.glasses.GlassesDashboardUiState
import com.achyut.adglasses.shared.navigation.AppDestination
import com.achyut.adglasses.shared.navigation.icon
import com.achyut.adglasses.shared.navigation.label
import com.achyut.adglasses.shared.icons.imageVector
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class, ExperimentalResourceApi::class)
@Composable
fun GlassesDashboardScreen(
    state: GlassesDashboardUiState,
    onAction: (GlassesDashboardAction) -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(title = { Text(stringResource(Res.string.app_name)) })
        },
        bottomBar = {
            NavigationBar {
                AppDestination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = destination == AppDestination.GLASSES,
                        onClick = { onAction(GlassesDashboardAction.Navigate(destination)) },
                        icon = {
                            Icon(
                                imageVector = destination.icon.imageVector(),
                                contentDescription = null,
                            )
                        },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { ConnectionStatusCard(state, onAction) }

            if (state.transfer.isVisible) {
                item { MediaTransferCard(state.transfer, onAction) }
            }

            if (state.showHeyCyanControls) {
                item { HeyCyanControls(state, onAction) }
            }

            if (state.showMeizuMyvuControls) {
                item { MeizuMyvuControls(state.meizuMyvu, onAction) }
            }

            if (state.showMetaRaybanControls) {
                item { MetaRaybanControls(state.metaRayban, onAction) }
            }

            item { GlassesAssistantControls(state, onAction) }

            item { AdvancedSettingsSection(state, onAction) }
        }
    }
}

@Composable
private fun ConnectionStatusCard(
    state: GlassesDashboardUiState,
    onAction: (GlassesDashboardAction) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BoxWithStatus(isConnected = state.connectionLabel != "Disconnected")
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.connectionLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = state.deviceClassLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.showBattery && state.batteryPercent != null) {
                    BatteryIndicator(state.batteryPercent)
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.connectionLabel == "Disconnected") {
                    Button(
                        onClick = { onAction(GlassesDashboardAction.Scan) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Connect")
                    }
                } else {
                    OutlinedButton(
                        onClick = { onAction(GlassesDashboardAction.Disconnect) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Disconnect")
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxWithStatus(isConnected: Boolean) {
    Surface(
        modifier = Modifier.size(12.dp),
        shape = CircleShape,
        color = if (isConnected) Color(0xFF4CAF50) else Color(0xFFF44336),
    ) {}
}

@Composable
private fun BatteryIndicator(percent: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "$percent%",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(4.dp))
    }
}

@Composable
private fun MediaTransferCard(
    state: com.achyut.adglasses.shared.glasses.GlassesTransferUiState,
    onAction: (GlassesDashboardAction) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = state.flowLabel,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = state.countsLabel,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            if (state.progress != null) {
                LinearProgressIndicator(
                    progress = state.progress,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = state.detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(12.dp))
            TextButton(
                onClick = { onAction(GlassesDashboardAction.StopSync) },
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("Cancel")
            }
        }
    }
}

@Composable
private fun HeyCyanControls(
    state: GlassesDashboardUiState,
    onAction: (GlassesDashboardAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("Quick Actions")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(
                label = "Photo",
                onClick = { onAction(GlassesDashboardAction.CapturePhoto) },
                modifier = Modifier.weight(1f),
            )
            ActionButton(
                label = "Video",
                onClick = { onAction(GlassesDashboardAction.ToggleVideo) },
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(
                label = "Audio",
                onClick = { onAction(GlassesDashboardAction.StartAudioRecording) },
                modifier = Modifier.weight(1f),
            )
            ActionButton(
                label = "Sync",
                onClick = { onAction(GlassesDashboardAction.StartSync) },
                modifier = Modifier.weight(1f),
                style = ActionButtonStyle.Primary,
            )
        }
    }
}

@Composable
private fun GlassesAssistantControls(
    state: GlassesDashboardUiState,
    onAction: (GlassesDashboardAction) -> Unit,
) {
    Column(
        modifier = Modifier.testTag("glasses_assistant_controls"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionTitle("AI Assistant", accented = true)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AssistantModeChip(
                label = "Gemini",
                mode = GlassesAssistantMode.GEMINI,
                selectedMode = state.assistantMode,
                onAction = onAction,
                modifier = Modifier.weight(1f),
            )
            AssistantModeChip(
                label = "ChatGPT",
                mode = GlassesAssistantMode.CHAT_GPT,
                selectedMode = state.assistantMode,
                onAction = onAction,
                modifier = Modifier.weight(1f),
            )
            AssistantModeChip(
                label = "Phone default",
                mode = GlassesAssistantMode.PHONE_ASSISTANT,
                selectedMode = state.assistantMode,
                onAction = onAction,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AssistantModeChip(
                label = "Custom Provider",
                mode = GlassesAssistantMode.CUSTOM_AI_PROVIDER,
                selectedMode = state.assistantMode,
                onAction = onAction,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        ActionRow(
            primaryLabel = "Test Voice",
            onPrimary = { onAction(GlassesDashboardAction.TestVoiceQuestion) },
            secondaryLabel = state.imageQueryLabel,
            onSecondary = { onAction(GlassesDashboardAction.TestImageQuestion) },
            secondaryEnabled = state.imageQueryEnabled,
        )
    }
}

@Composable
private fun AssistantModeChip(
    label: String,
    mode: GlassesAssistantMode,
    selectedMode: GlassesAssistantMode,
    onAction: (GlassesDashboardAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = mode == selectedMode
    Surface(
        modifier = modifier.clickable { onAction(GlassesDashboardAction.SelectAssistantMode(mode)) },
        shape = MaterialTheme.shapes.small,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SectionTitle(title: String, accented: Boolean = false) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = if (accented) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

@Composable
private fun ActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: ActionButtonStyle = ActionButtonStyle.Secondary,
) {
    val containerColor = when (style) {
        ActionButtonStyle.Primary -> MaterialTheme.colorScheme.primary
        ActionButtonStyle.Secondary -> MaterialTheme.colorScheme.secondaryContainer
        ActionButtonStyle.Destructive -> MaterialTheme.colorScheme.error
    }
    val contentColor = when (style) {
        ActionButtonStyle.Primary -> MaterialTheme.colorScheme.onPrimary
        ActionButtonStyle.Secondary -> MaterialTheme.colorScheme.onSecondaryContainer
        ActionButtonStyle.Destructive -> MaterialTheme.colorScheme.onError
    }

    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

enum class ActionButtonStyle {
    Primary, Secondary, Destructive
}

@Composable
private fun ActionRow(
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String,
    onSecondary: () -> Unit,
    primaryEnabled: Boolean = true,
    secondaryEnabled: Boolean = true,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ActionButton(
            label = primaryLabel,
            onClick = onPrimary,
            enabled = primaryEnabled,
            modifier = Modifier.weight(1f),
        )
        ActionButton(
            label = secondaryLabel,
            onClick = onSecondary,
            enabled = secondaryEnabled,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun AdvancedSettingsSection(
    state: GlassesDashboardUiState,
    onAction: (GlassesDashboardAction) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAction(GlassesDashboardAction.ToggleAdvanced) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "ADVANCED",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(
                    imageVector = if (state.advancedExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                )
            }

            AnimatedVisibility(
                visible = state.advancedExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(
                    modifier = Modifier.padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ActionButton(
                        label = "Sync Time",
                        onClick = { onAction(GlassesDashboardAction.SyncTime) },
                    )
                    ActionButton(
                        label = "Request Version",
                        onClick = { onAction(GlassesDashboardAction.RequestVersion) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MeizuMyvuControls(state: com.achyut.adglasses.shared.glasses.MeizuMyvuUiState, onAction: (GlassesDashboardAction) -> Unit) {
    // Re-implement or restore Meizu specific controls if needed
}

@Composable
private fun MetaRaybanControls(state: com.achyut.adglasses.shared.glasses.MetaRaybanUiState, onAction: (GlassesDashboardAction) -> Unit) {
    // Re-implement or restore Meta specific controls if needed
}

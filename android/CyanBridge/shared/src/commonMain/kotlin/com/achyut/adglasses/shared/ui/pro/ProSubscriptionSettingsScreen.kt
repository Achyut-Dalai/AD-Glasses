package com.achyut.adglasses.shared.ui.pro

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.achyut.adglasses.shared.billing.BillingCatalog
import com.achyut.adglasses.shared.billing.BillingPlan
import com.achyut.adglasses.shared.billing.ProSubscriptionSettingsUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProSubscriptionSettingsScreen(
    state: ProSubscriptionSettingsUiState,
    onRefreshPlan: () -> Unit,
    onChangePlan: (String) -> Unit,
    onCancelSubscription: () -> Unit,
    onRefreshAccount: () -> Unit,
    onRefreshQuota: () -> Unit,
    onRefreshModels: () -> Unit,
    onJoinBeta: () -> Unit,
    onStartGeminiLive: () -> Unit,
    onCloudSyncChange: (Boolean) -> Unit,
    onPrioritySupportChange: (Boolean) -> Unit,
    onPluginRewardsChange: (Boolean) -> Unit,
    onEarlyAccessDevicesChange: (Boolean) -> Unit,
    onBackupFrequencyChange: (Int) -> Unit,
    onSupportChannelChange: (Int) -> Unit,
    onRequestsModelChange: (String) -> Unit,
    onQuestionsModelChange: (String) -> Unit,
    onTasksModelChange: (String) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    var showChangePlanDialog by remember { mutableStateOf(false) }
    var showCancelDialog by remember { mutableStateOf(false) }
    var selectedPlanId by remember(state.plan) {
        mutableStateOf(
            BillingCatalog.plans.firstOrNull { it.id == state.plan.removePrefix("Plan: ").trim() }?.id
                ?: BillingCatalog.plans.getOrNull(1)?.id
                ?: BillingCatalog.plans.firstOrNull()?.id.orEmpty(),
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = { TopAppBar(title = { Text("Pro settings") }) },
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
                ProSettingsCard("Plan") {
                    Text(state.planStatus)
                    Text(state.plan, style = MaterialTheme.typography.bodySmall)
                    Text(state.expires, style = MaterialTheme.typography.bodySmall)
                    Text(state.verified, style = MaterialTheme.typography.bodySmall)
                    ActionButtons(
                        primaryLabel = "Refresh",
                        onPrimary = onRefreshPlan,
                        secondaryLabel = "Change plan",
                        onSecondary = { showChangePlanDialog = true },
                    )
                    OutlinedButton(
                        onClick = { showCancelDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Cancel subscription")
                    }
                }
            }
            item {
                ProSettingsCard("Account") {
                    Text(state.accountEmail, style = MaterialTheme.typography.bodySmall)
                    Text(state.accountToken, style = MaterialTheme.typography.bodySmall)
                    Text(state.accountSubscription, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = onRefreshAccount, modifier = Modifier.fillMaxWidth()) { Text("Refresh account") }
                }
            }
            item {
                ProSettingsCard("AI model routing") {
                    ModelChoice("Requests", state.requestsModel, state.modelOptions, onRequestsModelChange)
                    ModelChoice("Questions", state.questionsModel, state.modelOptions, onQuestionsModelChange)
                    ModelChoice("Tasks", state.tasksModel, state.modelOptions, onTasksModelChange)
                    ActionButtons(
                        primaryLabel = "Refresh models",
                        onPrimary = onRefreshModels,
                        secondaryLabel = "Refresh quota",
                        onSecondary = onRefreshQuota,
                    )
                    Text(state.quotaStatus, style = MaterialTheme.typography.bodySmall)
                    if (state.quotaBreakdown.isNotBlank()) {
                        Text(state.quotaBreakdown, style = MaterialTheme.typography.bodySmall)
                    }
                    state.quotaProgress?.let { percent ->
                        LinearProgressIndicator(
                            progress = { percent.coerceIn(0, 100) / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            item {
                ProSettingsCard("Preferences") {
                    ToggleSetting("Cloud sync", state.cloudSync, onCloudSyncChange)
                    ToggleSetting("Priority support", state.prioritySupport, onPrioritySupportChange)
                    ToggleSetting("Plugin rewards", state.pluginRewards, onPluginRewardsChange)
                    ToggleSetting("Early device access", state.earlyAccessDevices, onEarlyAccessDevicesChange)
                    ChoiceChips(
                        title = "Backup frequency",
                        labels = listOf("1 hour", "6 hours", "Daily"),
                        selectedIndex = state.backupFrequencyIndex,
                        onSelected = onBackupFrequencyChange,
                    )
                    ChoiceChips(
                        title = "Support channel",
                        labels = listOf("In-app", "Email", "Discord"),
                        selectedIndex = state.supportChannelIndex,
                        onSelected = onSupportChannelChange,
                    )
                }
            }
            item {
                ProSettingsCard("Beta cloud") {
                    Text(
                        state.betaStatus.ifBlank { "Register interest for beta cloud features." },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(onClick = onJoinBeta, modifier = Modifier.fillMaxWidth()) { Text("Sign up for beta cloud") }
                }
            }
            item {
                ProSettingsCard("Gemini Live (voice and vision, preview)") {
                    Text(
                        "Direct Google Gemini Live connection. Requires an active paid Pro plan and network access. Live audio and deliberate still images are sent to Google.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(onClick = onStartGeminiLive, modifier = Modifier.fillMaxWidth()) {
                        Text("Open Gemini Live preview")
                    }
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(onClick = onBack) { Text("Back") }
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(onClick = onSave) { Text("Save") }
                }
            }
        }
    }

    if (showChangePlanDialog) {
        AlertDialog(
            onDismissRequest = { showChangePlanDialog = false },
            title = { Text("Change plan") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    BillingCatalog.plans.forEach { plan ->
                        PlanChoice(
                            plan = plan,
                            selected = selectedPlanId == plan.id,
                            onClick = { selectedPlanId = plan.id },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showChangePlanDialog = false
                        onChangePlan(selectedPlanId)
                    },
                ) { Text("Continue") }
            },
            dismissButton = {
                TextButton(onClick = { showChangePlanDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (showCancelDialog) {
        val isFreeTrial = state.plan.removePrefix("Plan: ").trim() == "free_trial"
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text("Cancel subscription?") },
            text = {
                Text(
                    if (isFreeTrial) {
                        "Are you sure you want to end your free trial now?"
                    } else {
                        "You will keep access until the end of your current billing period."
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCancelDialog = false
                        onCancelSubscription()
                    },
                ) { Text("Yes, cancel") }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) { Text("Keep subscription") }
            },
        )
    }
}

@Composable
private fun PlanChoice(
    plan: BillingPlan,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(plan.name, style = MaterialTheme.typography.titleSmall)
            Text(
                "Localized price and renewal terms are shown in checkout.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProSettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun ToggleSetting(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ActionButtons(
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String,
    onSecondary: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = onPrimary, modifier = Modifier.weight(1f)) { Text(primaryLabel) }
        OutlinedButton(onClick = onSecondary, modifier = Modifier.weight(1f)) { Text(secondaryLabel) }
    }
}

@Composable
private fun ChoiceChips(
    title: String,
    labels: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
) {
    Text(title, style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.forEachIndexed { index, label ->
            FilterChip(selected = index == selectedIndex, onClick = { onSelected(index) }, label = { Text(label) })
        }
    }
}

@Composable
private fun ModelChoice(
    label: String,
    value: String,
    options: List<String>,
    onSelected: (String) -> Unit,
) {
    var showChoices by remember { mutableStateOf(false) }
    TextButton(onClick = { showChoices = true }, modifier = Modifier.fillMaxWidth()) {
        Text("$label: ${value.ifBlank { "Select model" }}")
    }
    if (showChoices) {
        AlertDialog(
            onDismissRequest = { showChoices = false },
            title = { Text("$label model") },
            text = {
                LazyColumn {
                    options.forEach { option ->
                        item {
                            TextButton(
                                onClick = {
                                    onSelected(option)
                                    showChoices = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(option) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showChoices = false }) { Text("Close") } },
        )
    }
}

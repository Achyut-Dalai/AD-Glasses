package com.fersaiyan.cyanbridge.shared.ui.pro

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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fersaiyan.cyanbridge.shared.billing.ProSubscriptionUiState

private val planLabels = listOf(
    "free_trial" to "Free trial · 30 days",
    "cheap" to "Cheap · $1/month",
    "standard" to "Standard · $5/month",
    "max" to "Max · $20/month",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProSubscriptionScreen(
    state: ProSubscriptionUiState,
    onPlanSelected: (String) -> Unit,
    onSubscribeInApp: () -> Unit,
    onSubscribeOnWebsite: () -> Unit,
    onDonate: () -> Unit,
    onCancelSubscription: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = { TopAppBar(title = { Text("Pro subscription") }) },
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
                    "Use CyanBridge completely free with local models. Pro is optional for cloud AI and cloud sync.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Choose a plan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        planLabels.forEach { (id, label) ->
                            FilterChip(
                                selected = state.selectedPlan == id,
                                onClick = { onPlanSelected(id) },
                                label = { Text(label) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
            item { BenefitCard("Fund ongoing device support", "Subscriptions help fund bug fixes and compatibility for more glasses.") }
            item { BenefitCard("Support plugin developers", "Help sustain community workflows and automation plugins.") }
            item { BenefitCard("Encrypted cloud options", "Optional cross-device cloud sync remains separate from local-first workflows.") }
            item { BenefitCard("Priority support", "Get priority access to support channels and early feature access.") }
            if (state.webCheckoutAvailable) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Secure website checkout", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Use the provider-hosted checkout and return to CyanBridge automatically.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedButton(onClick = onSubscribeOnWebsite) { Text("Subscribe on website") }
                        }
                    }
                }
            }
            item {
                OutlinedButton(onClick = onDonate, modifier = Modifier.fillMaxWidth()) {
                    Text("Donate via Asaas")
                }
            }
            if (state.isSubscribed) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Manage subscription", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "You retain access through the current billing period after cancellation.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(onClick = onCancelSubscription) {
                                Text("Cancel subscription", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    state.status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(onClick = onBack) { Text("Back") }
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(onClick = onSubscribeInApp) { Text("Continue") }
                }
            }
        }
    }
}

@Composable
private fun BenefitCard(title: String, description: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

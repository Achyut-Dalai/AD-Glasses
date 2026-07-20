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
import androidx.compose.material3.AlertDialog
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
import com.fersaiyan.cyanbridge.shared.billing.BillingCatalog
import com.fersaiyan.cyanbridge.shared.billing.BillingPlan
import com.fersaiyan.cyanbridge.shared.billing.ProviderOffer
import com.fersaiyan.cyanbridge.shared.billing.ProSubscriptionUiState
import kotlin.math.round

private val planLabels = buildList {
    add("free_trial" to "Free trial · 30 days")
    addAll(
        BillingCatalog.plans.map { plan ->
            val basePrice = plan.asaasOffer.referencePriceUsd - plan.asaasOffer.adjustmentUsd
            plan.id to "${plan.name} · Base \$${formatUsd(basePrice)}/month · Checkout \$${formatUsd(plan.asaasOffer.referencePriceUsd)}/month"
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProSubscriptionScreen(
    state: ProSubscriptionUiState,
    onPlanSelected: (String) -> Unit,
    onSubscribeInApp: () -> Unit,
    onSubscribeOnWebsite: () -> Unit,
    onSecureCheckoutSelected: (String) -> Unit,
    onDismissSecureCheckout: () -> Unit,
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
                            Text("Secure checkout", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Continue to secure payment and return to CyanBridge automatically.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedButton(onClick = onSubscribeOnWebsite) { Text("Continue to secure checkout") }
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
                            Text("Cancel subscription", style = MaterialTheme.typography.titleSmall)
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

    state.checkoutPlan?.let { planId ->
        SecureCheckoutDialog(
            plan = BillingCatalog.plan(planId),
            onProviderSelected = onSecureCheckoutSelected,
            onDismiss = onDismissSecureCheckout,
        )
    }
}

@Composable
private fun SecureCheckoutDialog(
    plan: BillingPlan,
    onProviderSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val basePrice = plan.asaasOffer.referencePriceUsd - plan.asaasOffer.adjustmentUsd
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose secure checkout") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Base plan price: \$${formatUsd(basePrice)}/month")
                Text(
                    "Choose the payment option that fits you. Checkout prices include each provider's fees; the final total and available payment methods are shown before payment.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CheckoutProviderOption(
                    title = "Asaas",
                    offer = plan.asaasOffer,
                    details = "Lower checkout price · charged in the BRL equivalent",
                    onClick = { onProviderSelected(plan.asaasOffer.provider.wireName) },
                )
                CheckoutProviderOption(
                    title = "Paddle",
                    offer = plan.paddleOffer,
                    details = "More payment methods · higher price includes provider fees",
                    onClick = { onProviderSelected(plan.paddleOffer.provider.wireName) },
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun CheckoutProviderOption(
    title: String,
    offer: ProviderOffer,
    details: String,
    onClick: () -> Unit,
) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                "Checkout price: \$${formatUsd(offer.referencePriceUsd)}/month",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Includes \$${formatUsd(offer.adjustmentUsd)} provider fee · $details",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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

private fun formatUsd(value: Double): String {
    val rounded = round(value * 100.0) / 100.0
    return if (rounded == rounded.toLong().toDouble()) {
        rounded.toLong().toString()
    } else {
        rounded.toString()
    }
}

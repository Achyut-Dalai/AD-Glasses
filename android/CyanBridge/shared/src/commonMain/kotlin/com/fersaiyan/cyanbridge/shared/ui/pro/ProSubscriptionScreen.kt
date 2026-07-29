package com.fersaiyan.cyanbridge.shared.ui.pro

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
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
import com.fersaiyan.cyanbridge.shared.billing.BillingProvider
import com.fersaiyan.cyanbridge.shared.billing.BillingCatalog
import com.fersaiyan.cyanbridge.shared.billing.ProviderOffer
import com.fersaiyan.cyanbridge.shared.billing.ProSubscriptionUiState
import kotlin.math.roundToInt

private fun planLabels(state: ProSubscriptionUiState) = buildList {
    add("free_trial" to "Free trial · 30 days")
    addAll(
        BillingCatalog.plans.map { plan ->
            val price = state.playPriceLabels[plan.id]
            val label = price?.let { "Google Play: $it" }
                ?: if (plan.id in state.playCheckoutAvailablePlans) {
                    "Google Play price shown at checkout"
                } else {
                    "Choose checkout to compare options"
                }
            plan.id to "${plan.name} · $label"
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProSubscriptionScreen(
    state: ProSubscriptionUiState,
    onPlanSelected: (String) -> Unit,
    onStartFreeTrial: () -> Unit,
    onSubscribeWithGooglePlay: () -> Unit,
    onSubscribeOnWebsite: (BillingProvider) -> Unit,
    onCheckoutUnavailable: () -> Unit,
    onDonate: () -> Unit,
    onCancelSubscription: () -> Unit,
    onBack: () -> Unit,
) {
    var showCheckoutChoices by remember { mutableStateOf(false) }

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
                        planLabels(state).forEach { (id, label) ->
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
                            Text("Checkout choices", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Use lower-cost web checkout with Asaas or Paddle, or use the easier in-app Google Play checkout.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
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
                    FilledTonalButton(
                        onClick = {
                            if (state.selectedPlan == "free_trial") {
                                onStartFreeTrial()
                            } else if (
                                state.webCheckoutAvailable ||
                                    (
                                        state.selectedPlan in state.playCheckoutAvailablePlans &&
                                            state.googlePlayCheckoutAllowed
                                    )
                            ) {
                                showCheckoutChoices = true
                            } else {
                                onCheckoutUnavailable()
                            }
                        },
                    ) {
                        Text(if (state.selectedPlan == "free_trial") "Start free trial" else "Choose checkout")
                    }
                }
            }
        }
    }

    if (showCheckoutChoices) {
        CheckoutChoiceDialog(
            state = state,
            onDismiss = { showCheckoutChoices = false },
            onGooglePlaySelected = {
                showCheckoutChoices = false
                onSubscribeWithGooglePlay()
            },
            onWebProviderSelected = { provider ->
                showCheckoutChoices = false
                onSubscribeOnWebsite(provider)
            },
        )
    }
}

@Composable
private fun CheckoutChoiceDialog(
    state: ProSubscriptionUiState,
    onDismiss: () -> Unit,
    onGooglePlaySelected: () -> Unit,
    onWebProviderSelected: (BillingProvider) -> Unit,
) {
    val plan = BillingCatalog.plan(state.selectedPlan)
    val playPrice = state.playPriceLabels[plan.id]
    val playAvailable = plan.id in state.playCheckoutAvailablePlans && state.googlePlayCheckoutAllowed
    var selectedWebProvider by remember(plan.id) {
        mutableStateOf(BillingProvider.ASAAS.wireName)
    }
    val webProvider = if (selectedWebProvider == BillingProvider.PADDLE.wireName) {
        BillingProvider.PADDLE
    } else {
        BillingProvider.ASAAS
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose checkout") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "${plan.name} renews monthly. Web prices are confirmed again before payment.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (state.webCheckoutAvailable) {
                    Text("Web checkout", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    ProviderChoice(
                        provider = BillingProvider.ASAAS,
                        offer = plan.asaasOffer,
                        selected = webProvider == BillingProvider.ASAAS,
                        onClick = { selectedWebProvider = BillingProvider.ASAAS.wireName },
                    )
                    ProviderChoice(
                        provider = BillingProvider.PADDLE,
                        offer = plan.paddleOffer,
                        selected = webProvider == BillingProvider.PADDLE,
                        onClick = { selectedWebProvider = BillingProvider.PADDLE.wireName },
                    )
                    Text(
                        webCheckoutDescription(webProvider),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FilledTonalButton(
                        onClick = { onWebProviderSelected(webProvider) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Continue with ${providerName(webProvider)}")
                    }
                }
                if (playAvailable || state.webCheckoutAvailable) {
                    Text("Google Play", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
                Text(
                    if (playPrice != null) {
                        "Google Play price: $playPrice. Google Play manages payment and renewal in-app."
                    } else if (!state.googlePlayCheckoutAllowed) {
                        "Change this web subscription through website checkout to avoid overlapping subscriptions."
                    } else {
                        "Google Play availability and localized price load from the Play Store."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = onGooglePlaySelected,
                    enabled = playAvailable,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        when {
                            playAvailable -> "Use Google Play"
                            !state.googlePlayCheckoutAllowed -> "Use web checkout for this change"
                            else -> "Google Play unavailable"
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Not now") }
        },
    )
}

@Composable
private fun ProviderChoice(
    provider: BillingProvider,
    offer: ProviderOffer,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(providerName(provider), style = MaterialTheme.typography.bodyLarge)
            Text(
                providerPriceLabel(provider, offer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun providerName(provider: BillingProvider): String = when (provider) {
    BillingProvider.ASAAS -> "Asaas"
    BillingProvider.PADDLE -> "Paddle"
    BillingProvider.GOOGLE_PLAY -> "Google Play"
}

private fun providerPriceLabel(provider: BillingProvider, offer: ProviderOffer): String = when (provider) {
    BillingProvider.ASAAS ->
        "About ${formatUsd(offer.referencePriceUsd)}/month (${formatUsd(offer.adjustmentUsd)} processing), charged in BRL"
    BillingProvider.PADDLE ->
        "${formatUsd(offer.referencePriceUsd)}/month (${formatUsd(offer.adjustmentUsd)} checkout adjustment) in USD"
    BillingProvider.GOOGLE_PLAY -> "Price shown by Google Play"
}

private fun webCheckoutDescription(provider: BillingProvider): String = when (provider) {
    BillingProvider.ASAAS ->
        "Lower-cost direct card checkout. The exact BRL total and renewal amount are shown before payment."
    BillingProvider.PADDLE ->
        "Global card checkout. Any applicable tax and the final renewal total are shown before payment."
    BillingProvider.GOOGLE_PLAY -> ""
}

private fun formatUsd(amount: Double): String {
    val cents = (amount * 100.0).roundToInt()
    val whole = cents / 100
    val fraction = (cents % 100).toString().padStart(2, '0')
    return "\$$whole.$fraction"
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

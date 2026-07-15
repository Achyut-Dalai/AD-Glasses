package com.fersaiyan.cyanbridge.shared.billing

enum class BillingProvider(val wireName: String) {
    ASAAS("asaas"),
    PADDLE("paddle"),
}

data class ProviderOffer(
    val provider: BillingProvider,
    val referencePriceUsd: Double,
    val currency: String,
    val adjustmentUsd: Double = 0.0,
)

data class BillingPlan(
    val id: String,
    val name: String,
    val asaasOffer: ProviderOffer,
    val paddleOffer: ProviderOffer,
)

enum class CheckoutState {
    IDLE,
    PREPARING,
    OPEN,
    VERIFYING,
    COMPLETE,
    ERROR,
}

enum class SubscriptionStatus {
    INACTIVE,
    PENDING,
    ACTIVE,
    PAST_DUE,
    CANCELED,
}

object BillingCatalog {
    val plans = listOf(
        paidPlan("cheap", "Cheap", 1.0, 1.55),
        paidPlan("standard", "Standard", 5.0, 5.75),
        paidPlan("max", "Max", 20.0, 21.50),
    )

    fun plan(id: String): BillingPlan = plans.firstOrNull { it.id == id } ?: plans[1]

    private fun paidPlan(id: String, name: String, basePriceUsd: Double, paddlePriceUsd: Double) = BillingPlan(
        id = id,
        name = name,
        asaasOffer = ProviderOffer(
            provider = BillingProvider.ASAAS,
            referencePriceUsd = basePriceUsd,
            currency = "BRL",
        ),
        paddleOffer = ProviderOffer(
            provider = BillingProvider.PADDLE,
            referencePriceUsd = paddlePriceUsd,
            currency = "USD",
            adjustmentUsd = paddlePriceUsd - basePriceUsd,
        ),
    )
}

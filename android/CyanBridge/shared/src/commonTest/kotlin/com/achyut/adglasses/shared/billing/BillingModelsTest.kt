package com.achyut.adglasses.shared.billing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class BillingModelsTest {
    @Test
    fun webProviderOffersKeepSeparateFeeAdjustedPricesAndChargeCurrencies() {
        val standard = BillingCatalog.plan("standard")

        assertEquals(5.25, standard.asaasOffer.referencePriceUsd)
        assertEquals(0.25, standard.asaasOffer.adjustmentUsd)
        assertEquals("USD", standard.asaasOffer.currency)
        assertEquals("BRL", standard.asaasOffer.chargeCurrency)
        assertEquals(5.75, standard.paddleOffer.referencePriceUsd)
        assertEquals(0.75, standard.paddleOffer.adjustmentUsd)
        assertEquals("USD", standard.paddleOffer.chargeCurrency)
    }

    @Test
    fun unknownPlanFallsBackToStandard() {
        assertEquals("standard", BillingCatalog.plan("unknown").id)
    }

    @Test
    fun unavailableCheckoutDoesNotClaimPaymentOrAccess() {
        val state = ProSubscriptionUiState()

        assertFalse(state.isSubscribed)
        assertFalse(state.webCheckoutAvailable)
        assertEquals(
            "Subscription checkout is unavailable on this host. No payment was started and no Pro access was granted.",
            unavailableProSubscriptionStatus(ProSubscriptionAction.SUBSCRIBE),
        )
    }
}

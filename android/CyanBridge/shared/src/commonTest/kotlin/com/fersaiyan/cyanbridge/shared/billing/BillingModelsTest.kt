package com.fersaiyan.cyanbridge.shared.billing

import kotlin.test.Test
import kotlin.test.assertEquals

class BillingModelsTest {
    @Test
    fun providerOffersKeepSeparateFeeAdjustedPricesAndChargeCurrencies() {
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
}

package com.fersaiyan.cyanbridge.shared.billing

import kotlin.test.Test
import kotlin.test.assertEquals

class BillingModelsTest {
    @Test
    fun providerOffersKeepAsaasAtBasePrice() {
        val standard = BillingCatalog.plan("standard")

        assertEquals(5.0, standard.asaasOffer.referencePriceUsd)
        assertEquals(5.75, standard.paddleOffer.referencePriceUsd)
        assertEquals(0.75, standard.paddleOffer.adjustmentUsd)
    }

    @Test
    fun unknownPlanFallsBackToStandard() {
        assertEquals("standard", BillingCatalog.plan("unknown").id)
    }
}

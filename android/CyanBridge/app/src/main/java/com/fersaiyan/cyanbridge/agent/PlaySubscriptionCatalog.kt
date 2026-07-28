package com.fersaiyan.cyanbridge.agent

import com.android.billingclient.api.ProductDetails
import com.fersaiyan.cyanbridge.BuildConfig

object PlaySubscriptionCatalog {

    data class SubscriptionOffer(
        val plan: String,
        val productId: String,
        val basePlanId: String,
        val offerId: String?,
    )

    private val entries = listOf(
        SubscriptionOffer(
            plan = "cheap",
            productId = BuildConfig.PRO_CHEAP_SKU.trim(),
            basePlanId = BuildConfig.PRO_CHEAP_BASE_PLAN_ID.trim(),
            offerId = BuildConfig.PRO_CHEAP_OFFER_ID.trim().ifBlank { null },
        ),
        SubscriptionOffer(
            plan = "standard",
            productId = BuildConfig.PRO_STANDARD_SKU.trim(),
            basePlanId = BuildConfig.PRO_STANDARD_BASE_PLAN_ID.trim(),
            offerId = BuildConfig.PRO_STANDARD_OFFER_ID.trim().ifBlank { null },
        ),
        SubscriptionOffer(
            plan = "max",
            productId = BuildConfig.PRO_MAX_SKU.trim(),
            basePlanId = BuildConfig.PRO_MAX_BASE_PLAN_ID.trim(),
            offerId = BuildConfig.PRO_MAX_OFFER_ID.trim().ifBlank { null },
        ),
    ).filter { it.productId.isNotBlank() }

    private val configuredEntries = entries.filter { it.basePlanId.isNotBlank() }

    fun allProductIds(): List<String> =
        configuredEntries.map { it.productId }.distinct()

    fun productIdForPlan(plan: String): String =
        entries.firstOrNull { it.plan == plan }?.productId.orEmpty()

    fun planForProductId(productId: String): String =
        entries.firstOrNull { it.productId == productId.trim() }?.plan.orEmpty()

    fun offerForPlan(plan: String): SubscriptionOffer? =
        configuredEntries.firstOrNull { it.plan == plan }

    fun configuredOffer(
        productDetails: ProductDetails,
        offer: SubscriptionOffer,
    ): ProductDetails.SubscriptionOfferDetails? {
        if (productDetails.productId != offer.productId) return null
        return productDetails.subscriptionOfferDetails?.firstOrNull {
            it.basePlanId == offer.basePlanId && it.offerId.orEmpty() == offer.offerId.orEmpty()
        }
    }
}

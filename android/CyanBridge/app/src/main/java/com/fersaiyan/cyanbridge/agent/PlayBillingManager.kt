package com.fersaiyan.cyanbridge.agent

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.ProductDetailsResponseListener
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesResponseListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryProductDetailsResult
import com.android.billingclient.api.QueryPurchasesParams

class PlayBillingManager(
    context: Context,
    private val onPurchasesUpdated: (List<Purchase>) -> Unit,
    private val onError: (String) -> Unit,
) {

    private val reconnectHandler = Handler(Looper.getMainLooper())
    private var onReady: (() -> Unit)? = null
    private var destroyed = false
    private var connectionInProgress = false
    private var reconnectScheduled = false
    private var reconnectAttempt = 0

    private val connectionListener = object : BillingClientStateListener {
        override fun onBillingSetupFinished(result: BillingResult) {
            connectionInProgress = false
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                reconnectAttempt = 0
                onReady?.invoke()
            } else {
                onError("billing_setup_${result.responseCode}")
                scheduleReconnect()
            }
        }

        override fun onBillingServiceDisconnected() {
            connectionInProgress = false
            onError("billing_disconnected_retrying")
            scheduleReconnect()
        }
    }

    private val billingClient: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .enablePrepaidPlans()
                .build()
        )
        .setListener { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK && !purchases.isNullOrEmpty()) {
                onPurchasesUpdated(purchases)
            } else if (result.responseCode != BillingClient.BillingResponseCode.USER_CANCELED) {
                onError("purchase_update_${result.responseCode}")
            }
        }
        .build()

    fun start(onReady: () -> Unit) {
        this.onReady = onReady
        connect()
    }

    private fun connect() {
        if (destroyed || connectionInProgress) return
        if (billingClient.isReady) {
            reconnectAttempt = 0
            onReady?.invoke()
            return
        }

        reconnectScheduled = false
        connectionInProgress = true
        billingClient.startConnection(connectionListener)
    }

    private fun scheduleReconnect() {
        if (destroyed || reconnectScheduled) return

        val delayMs = minOf(
            RECONNECT_MAX_DELAY_MS,
            RECONNECT_INITIAL_DELAY_MS * (1L shl reconnectAttempt.coerceAtMost(MAX_RECONNECT_EXPONENT)),
        )
        reconnectAttempt = (reconnectAttempt + 1).coerceAtMost(MAX_RECONNECT_EXPONENT)
        reconnectScheduled = true
        reconnectHandler.postDelayed(
            {
                reconnectScheduled = false
                connect()
            },
            delayMs,
        )
    }

    fun querySubscriptionProducts(productIds: List<String>, onResult: (Map<String, ProductDetails>) -> Unit) {
        val products = productIds
            .map { id ->
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(id)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            }

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(products)
            .build()

        billingClient.queryProductDetailsAsync(params, object : ProductDetailsResponseListener {
            override fun onProductDetailsResponse(result: BillingResult, queryResult: QueryProductDetailsResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    val detailsList = queryResult.productDetailsList
                    val resultMap = mutableMapOf<String, ProductDetails>()
                    if (detailsList != null) {
                        for (item in detailsList) {
                            resultMap[item.productId] = item
                        }
                    }
                    onResult(resultMap)
                } else {
                    onError("product_details_${result.responseCode}")
                    onResult(emptyMap())
                }
            }
        })
    }

    fun launchSubscriptionPurchase(
        activity: Activity,
        productDetails: ProductDetails,
        offer: PlaySubscriptionCatalog.SubscriptionOffer,
    ) {
        val offerToken = PlaySubscriptionCatalog.configuredOffer(productDetails, offer)
            ?.offerToken
            ?: run {
                onError("configured_offer_missing_${productDetails.productId}")
                return
            }

        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .setOfferToken(offerToken)
            .build()

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()

        val result = billingClient.launchBillingFlow(activity, flowParams)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            onError("launch_billing_${result.responseCode}")
        }
    }

    fun queryActivePurchases(listener: PurchasesResponseListener) {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        billingClient.queryPurchasesAsync(params, listener)
    }

    fun acknowledgeIfNeeded(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        if (purchase.isAcknowledged) return

        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient.acknowledgePurchase(params) { result ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                onError("acknowledge_failed_${result.responseCode}")
            }
        }
    }

    fun destroy() {
        destroyed = true
        reconnectHandler.removeCallbacksAndMessages(null)
        onReady = null
        billingClient.endConnection()
    }

    companion object {
        private const val RECONNECT_INITIAL_DELAY_MS = 1_000L
        private const val RECONNECT_MAX_DELAY_MS = 30_000L
        private const val MAX_RECONNECT_EXPONENT = 5

        fun localizedOfferDescription(
            productDetails: ProductDetails,
            offer: PlaySubscriptionCatalog.SubscriptionOffer,
        ): String? {
            val pricingPhases = PlaySubscriptionCatalog.configuredOffer(productDetails, offer)
                ?.pricingPhases
                ?.pricingPhaseList
                .orEmpty()
            if (pricingPhases.isEmpty()) return null
            return pricingPhases.joinToString(" then ") { phase ->
                val unit = billingPeriodUnit(phase.billingPeriod)
                val cycles = phase.billingCycleCount
                when {
                    cycles > 0 && unit != null -> {
                        "${phase.formattedPrice} for ${cycles} ${if (cycles == 1) unit else "${unit}s"}"
                    }
                    unit != null -> "${phase.formattedPrice} per $unit"
                    else -> "${phase.formattedPrice} (${phase.billingPeriod})"
                }
            }
        }

        private fun billingPeriodUnit(billingPeriod: String): String? = when (billingPeriod) {
            "P1D" -> "day"
            "P1W" -> "week"
            "P1M" -> "month"
            "P1Y" -> "year"
            else -> null
        }
    }
}

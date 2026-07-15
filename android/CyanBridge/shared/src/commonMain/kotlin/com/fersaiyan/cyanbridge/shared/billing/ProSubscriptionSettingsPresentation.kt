package com.fersaiyan.cyanbridge.shared.billing

/** Platform-neutral summary state for the subscription checkout screen. */
data class ProSubscriptionUiState(
    val status: String = "Not subscribed",
    val selectedPlan: String = "free_trial",
    val webCheckoutAvailable: Boolean = false,
    val isSubscribed: Boolean = false,
)

/** Platform-neutral presentation state for the subscription settings screen. */
data class ProSubscriptionSettingsUiState(
    val planStatus: String = "Status: loading...",
    val plan: String = "Plan: -",
    val expires: String = "Expires: -",
    val verified: String = "Last verified: -",
    val accountEmail: String = "Email: -",
    val accountToken: String = "API token: -",
    val accountSubscription: String = "Subscription: -",
    val quotaStatus: String = "Quota: -",
    val quotaBreakdown: String = "",
    val quotaProgress: Int? = null,
    val betaStatus: String = "",
    val cloudSync: Boolean = true,
    val prioritySupport: Boolean = true,
    val pluginRewards: Boolean = true,
    val earlyAccessDevices: Boolean = true,
    val backupFrequencyIndex: Int = 1,
    val supportChannelIndex: Int = 0,
    val modelOptions: List<String> = emptyList(),
    val requestsModel: String = "",
    val questionsModel: String = "",
    val tasksModel: String = "",
)

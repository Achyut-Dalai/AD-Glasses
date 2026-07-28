package com.fersaiyan.cyanbridge.shared.navigation

/** Local route state for the shared Settings-to-Pro flow. */
enum class SharedSubscriptionRoute {
    SETTINGS,
    PRO_SUBSCRIPTION,
}

fun SharedSubscriptionRoute.openSubscription(): SharedSubscriptionRoute =
    SharedSubscriptionRoute.PRO_SUBSCRIPTION

fun SharedSubscriptionRoute.closeSubscription(): SharedSubscriptionRoute =
    SharedSubscriptionRoute.SETTINGS

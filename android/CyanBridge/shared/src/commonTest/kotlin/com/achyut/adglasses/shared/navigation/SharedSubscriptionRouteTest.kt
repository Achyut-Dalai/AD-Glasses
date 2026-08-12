package com.achyut.adglasses.shared.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class SharedSubscriptionRouteTest {
    @Test
    fun settingsSubscriptionRouteRoundTripsToSettings() {
        val opened = SharedSubscriptionRoute.SETTINGS.openSubscription()

        assertEquals(SharedSubscriptionRoute.PRO_SUBSCRIPTION, opened)
        assertEquals(SharedSubscriptionRoute.SETTINGS, opened.closeSubscription())
    }

    @Test
    fun routeTransitionsAreIdempotent() {
        assertEquals(
            SharedSubscriptionRoute.PRO_SUBSCRIPTION,
            SharedSubscriptionRoute.PRO_SUBSCRIPTION.openSubscription(),
        )
        assertEquals(
            SharedSubscriptionRoute.SETTINGS,
            SharedSubscriptionRoute.SETTINGS.closeSubscription(),
        )
    }
}

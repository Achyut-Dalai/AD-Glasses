package com.fersaiyan.cyanbridge.agent

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SubscriptionCheckoutPolicyTest {
    @Test
    fun `creates callback on the verified app link`() {
        val url = SubscriptionCheckoutPolicy.createVerifiedCallbackUrl("one-time-result")

        assertEquals(
            "https://cyanbridge.vercel.app/web-subscribe/callback?result=one-time-result",
            url,
        )
        assertEquals("one-time-result", SubscriptionCheckoutPolicy.callbackResultFrom(Uri.parse(url)))
    }

    @Test
    fun `rejects custom scheme and untrusted callback hosts`() {
        assertNull(
            SubscriptionCheckoutPolicy.callbackResultFrom(
                Uri.parse("fersaiyan://pro-sub/callback?result=one-time-result"),
            ),
        )
        assertNull(
            SubscriptionCheckoutPolicy.callbackResultFrom(
                Uri.parse("https://example.com/web-subscribe/callback?result=one-time-result"),
            ),
        )
    }
}

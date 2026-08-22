package com.ad_glasses.localagent

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ad_glasses.agent.LocalAgentPrefs as AutomationPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalAgentSafetyPolicyTest {
    @Test
    fun `only blocks packages configured by user`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        AutomationPrefs.setCaptureBlacklistPackages(context, emptySet())

        assertNull(LocalAgentSafetyPolicy.blockedReason(context, "com.example.mobilebanking"))

        AutomationPrefs.setCaptureBlacklistPackages(context, setOf("com.example.mobilebanking"))
        assertEquals(
            "The current app is blocked in ADGlasses privacy settings.",
            LocalAgentSafetyPolicy.blockedReason(context, "COM.EXAMPLE.MOBILEBANKING"),
        )
        assertTrue(AutomationPrefs.getCaptureBlacklistPackages(context).contains("com.example.mobilebanking"))
    }
}

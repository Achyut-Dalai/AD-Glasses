package com.ad_glasses.ota

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LivePreviewSourceSafetyTest {
    @Test
    fun `live preview remains a passive probe`() {
        val source = File(
            "src/main/java/com/ad_glasses/ota/LivePreviewManager.kt",
        ).readText()

        assertTrue(source.contains("PASSIVE MODE: no BLE mode-control command will be sent"))
        assertFalse(Regex("""\bglassesControl\s*\(""").containsMatchIn(source))
        assertFalse(source.contains("byteArrayOf(0x09, 0x0a, 0x0d, 0x0e)"))
        assertTrue(source.contains("manager.startPeerDiscovery(allowDeviceResetOnTimeout = false)"))
    }
}

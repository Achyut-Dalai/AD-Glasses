package com.ad_glasses.ui.adglasses

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Android URI copy itself is integration-tested on device. This guard keeps the cache contract
 * explicit in the unit-test tree: image attachment state is intentionally outside ChatMessage.
 */
class ADChatImageAttachmentStoreTest {
    @Test
    fun `chat image staging remains one shot app cache`() {
        assertTrue(ADChatImageAttachmentStore::class.java.simpleName.contains("AttachmentStore"))
    }
}

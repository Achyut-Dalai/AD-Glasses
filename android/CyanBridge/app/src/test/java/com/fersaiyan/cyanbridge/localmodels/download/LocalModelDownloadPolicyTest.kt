package com.fersaiyan.cyanbridge.localmodels.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalModelDownloadPolicyTest {
    @Test
    fun slot_allows_only_one_active_download_and_requires_matching_release() {
        val slot = LocalModelDownloadSlot()

        assertTrue(slot.tryAcquire("qwen"))
        assertFalse(slot.tryAcquire("qwen"))
        assertFalse(slot.tryAcquire("gemma"))
        assertFalse(slot.release("gemma"))
        assertEquals("qwen", slot.currentModelId())
        assertTrue(slot.release("qwen"))
        assertTrue(slot.tryAcquire("gemma"))
    }

    @Test
    fun retry_policy_retries_only_transient_http_failures() {
        listOf(408, 425, 429, 500, 502, 503, 504).forEach { code ->
            assertTrue("expected $code to retry", LocalModelDownloadRetryPolicy.isRetryableHttpCode(code))
        }
        listOf(400, 401, 403, 404, 422).forEach { code ->
            assertFalse("expected $code to fail", LocalModelDownloadRetryPolicy.isRetryableHttpCode(code))
        }
    }

    @Test
    fun retry_backoff_is_bounded_and_respects_bounded_retry_after() {
        assertEquals(1_000L, LocalModelDownloadRetryPolicy.retryDelayMillis(1))
        assertEquals(2_000L, LocalModelDownloadRetryPolicy.retryDelayMillis(2))
        assertEquals(8_000L, LocalModelDownloadRetryPolicy.retryDelayMillis(8))
        assertEquals(7_000L, LocalModelDownloadRetryPolicy.retryDelayMillis(1, 7_000L))
        assertEquals(15_000L, LocalModelDownloadRetryPolicy.retryDelayMillis(1, 60_000L))
    }
}

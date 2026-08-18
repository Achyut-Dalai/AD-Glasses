package com.fersaiyan.cyanbridge.ui.adglasses

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ADNavigationRequestStoreTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        ADNavigationRequestStore.observe(context).value?.let {
            ADNavigationRequestStore.consume(context, it.id)
        }
    }

    @After
    fun tearDown() {
        ADNavigationRequestStore.observe(context).value?.let {
            ADNavigationRequestStore.consume(context, it.id)
        }
    }

    @Test
    fun persistsConversationPrefillUntilNativeShellConsumesIt() {
        val posted = ADNavigationRequestStore.post(
            context = context,
            destination = ADExternalDestination.CONVERSATIONS,
            prefill = "compare these two",
            threadId = "thread-123",
        )

        val visible = ADNavigationRequestStore.observe(context).value
        assertEquals(posted.id, visible?.id)
        assertEquals(ADExternalDestination.CONVERSATIONS, visible?.destination)
        assertEquals("compare these two", visible?.prefill)
        assertEquals("thread-123", visible?.threadId)

        ADNavigationRequestStore.consume(context, posted.id)
        assertNull(ADNavigationRequestStore.observe(context).value)
    }

    @Test
    fun newRequestsAlwaysReceiveANewerId() {
        val first = ADNavigationRequestStore.post(context, ADExternalDestination.SETTINGS)
        val second = ADNavigationRequestStore.post(context, ADExternalDestination.AI)

        assertTrue(second.id > first.id)
        assertEquals(ADExternalDestination.AI, ADNavigationRequestStore.observe(context).value?.destination)
    }
}

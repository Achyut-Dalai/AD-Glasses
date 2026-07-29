package com.fersaiyan.cyanbridge.ai.image

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TaskerImageProfileStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun profileHandshakeRequiresThePendingToken() {
        val token = TaskerImageProfileStore.beginVerification(context)

        assertFalse(TaskerImageProfileStore.verifyAndRecord(context, "gemini", "gemini-v3", "wrong"))
        assertTrue(TaskerImageProfileStore.verifyAndRecord(context, "gemini", "gemini-v3", token))
        assertEquals("gemini", TaskerImageProfileStore.target(context))
        assertEquals("gemini-v3", TaskerImageProfileStore.version(context))
        assertFalse(TaskerImageProfileStore.verifyAndRecord(context, "chatgpt", "chatgpt-v1", token))
    }
}

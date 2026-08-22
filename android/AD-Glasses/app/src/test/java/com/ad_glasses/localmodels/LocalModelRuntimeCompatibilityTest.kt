package com.ad_glasses.localmodels

import com.ad_glasses.localmodels.settings.LocalModelRuntime
import com.ad_glasses.localmodels.settings.LocalModelRuntimeCompatibility
import com.ad_glasses.localmodels.storage.LocalModelFileFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalModelRuntimeCompatibilityTest {
    @Test
    fun gguf_only_uses_llama_cpp() {
        assertTrue(LocalModelRuntimeCompatibility.isCompatible(LocalModelFileFormat.GGUF, LocalModelRuntime.LLAMA_CPP))
        assertFalse(LocalModelRuntimeCompatibility.isCompatible(LocalModelFileFormat.GGUF, LocalModelRuntime.LITERT))
        assertEquals(
            LocalModelRuntime.LLAMA_CPP,
            LocalModelRuntimeCompatibility.enforce(LocalModelFileFormat.GGUF, LocalModelRuntime.LITERT),
        )
    }

    @Test
    fun litert_packages_only_use_litert() {
        assertTrue(LocalModelRuntimeCompatibility.isCompatible(LocalModelFileFormat.LITERT, LocalModelRuntime.LITERT))
        assertFalse(LocalModelRuntimeCompatibility.isCompatible(LocalModelFileFormat.LITERT, LocalModelRuntime.LLAMA_CPP))
        assertEquals(
            LocalModelRuntime.LITERT,
            LocalModelRuntimeCompatibility.enforce(LocalModelFileFormat.LITERT, LocalModelRuntime.LLAMA_CPP),
        )
    }
}

package com.fersaiyan.cyanbridge.localmodels.settings

import com.fersaiyan.cyanbridge.localmodels.storage.LocalModelFileFormat

object LocalModelRuntimeCompatibility {
    fun requiredRuntime(format: LocalModelFileFormat): LocalModelRuntime? = when (format) {
        LocalModelFileFormat.GGUF -> LocalModelRuntime.LLAMA_CPP
        LocalModelFileFormat.LITERT -> LocalModelRuntime.LITERT
        LocalModelFileFormat.UNKNOWN -> null
    }

    fun isCompatible(format: LocalModelFileFormat, runtime: LocalModelRuntime): Boolean {
        val required = requiredRuntime(format) ?: return false
        return runtime == required
    }

    fun enforce(format: LocalModelFileFormat, requested: LocalModelRuntime): LocalModelRuntime {
        return requiredRuntime(format) ?: requested
    }
}

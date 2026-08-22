package com.ad_glasses.ai.orchestrator

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Lightweight invalidation signal for Compose surfaces that display persisted capability state. */
object AssistantCapabilityRuntimeEvents {
    private val mutableVersion = MutableStateFlow(0L)
    val version: StateFlow<Long> = mutableVersion.asStateFlow()

    fun notifyChanged() {
        mutableVersion.update { current -> current + 1L }
    }
}

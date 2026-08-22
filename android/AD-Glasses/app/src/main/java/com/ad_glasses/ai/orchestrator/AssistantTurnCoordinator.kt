package com.ad_glasses.ai.orchestrator

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serializes accepted turns within one conversation while allowing a newly-created topic to run
 * independently. This preserves user/assistant ordering when phone, voice, and Lens overlap.
 */
object AssistantTurnCoordinator {
    private val threadLocks = ConcurrentHashMap<String, Mutex>()

    suspend fun <T> withThread(threadId: String, block: suspend () -> T): T {
        return threadLocks.getOrPut(threadId) { Mutex() }.withLock { block() }
    }
}

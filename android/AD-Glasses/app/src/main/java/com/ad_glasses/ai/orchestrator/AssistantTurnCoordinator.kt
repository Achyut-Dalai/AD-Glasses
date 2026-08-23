package com.ad_glasses.ai.orchestrator

import android.os.SystemClock
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serializes accepted turns within one conversation while allowing a newly-created topic to run
 * independently. This preserves user/assistant ordering when phone, voice, and Lens overlap.
 */
object AssistantTurnCoordinator {
    private const val TAG = "AssistantTiming"
    private val threadLocks = ConcurrentHashMap<String, Mutex>()

    suspend fun <T> withThread(threadId: String, block: suspend () -> T): T {
        val queuedAt = SystemClock.elapsedRealtime()
        val mutex = threadLocks.getOrPut(threadId) { Mutex() }
        return mutex.withLock {
            val acquiredAt = SystemClock.elapsedRealtime()
            val threadLabel = threadId.takeLast(8)
            Log.i(TAG, "stage=queue_acquired thread=$threadLabel waitMs=${acquiredAt - queuedAt}")
            try {
                block()
            } finally {
                Log.i(
                    TAG,
                    "stage=turn_released thread=$threadLabel heldMs=${SystemClock.elapsedRealtime() - acquiredAt}",
                )
            }
        }
    }
}

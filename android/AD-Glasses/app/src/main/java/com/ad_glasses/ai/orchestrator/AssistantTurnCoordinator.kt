package com.ad_glasses.ai.orchestrator

import android.os.SystemClock
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Coordinates conversational state without serializing the network lifecycle.
 *
 * State mutations for one thread are kept ordered behind a very small mutex. Interactive turns are
 * latest-wins: a new phone/glasses request cancels the older in-flight coroutine and increments a
 * generation so an uncooperative/late transport result cannot be persisted as the active answer.
 */
object AssistantTurnCoordinator {
    private const val TAG = "AssistantTiming"
    private val threadLocks = ConcurrentHashMap<String, Mutex>()
    private val generations = ConcurrentHashMap<String, AtomicLong>()
    private val activeInteractiveJobs = ConcurrentHashMap<String, Job>()

    data class InteractiveLease internal constructor(
        val threadId: String,
        val generation: Long,
        internal val job: Job,
    )

    suspend fun <T> withThreadState(threadId: String, block: suspend () -> T): T {
        val queuedAt = SystemClock.elapsedRealtime()
        val mutex = threadLocks.getOrPut(threadId) { Mutex() }
        return mutex.withLock {
            val acquiredAt = SystemClock.elapsedRealtime()
            val threadLabel = threadId.takeLast(8)
            Log.i(TAG, "stage=state_lock_acquired thread=$threadLabel waitMs=${acquiredAt - queuedAt}")
            try {
                block()
            } finally {
                Log.i(
                    TAG,
                    "stage=state_lock_released thread=$threadLabel heldMs=${SystemClock.elapsedRealtime() - acquiredAt}",
                )
            }
        }
    }

    fun beginInteractiveTurn(threadId: String, job: Job): InteractiveLease {
        val generation = generations.getOrPut(threadId) { AtomicLong(0L) }.incrementAndGet()
        val previous = activeInteractiveJobs.put(threadId, job)
        if (previous != null && previous !== job && previous.isActive) {
            previous.cancel(CancellationException("Superseded by a newer assistant turn"))
            Log.i(TAG, "stage=turn_superseded thread=${threadId.takeLast(8)} generation=$generation")
        }
        return InteractiveLease(threadId, generation, job)
    }

    fun isCurrent(lease: InteractiveLease): Boolean {
        val currentGeneration = generations[lease.threadId]?.get() ?: return false
        return currentGeneration == lease.generation &&
            activeInteractiveJobs[lease.threadId] === lease.job &&
            lease.job.isActive
    }

    fun finishInteractiveTurn(lease: InteractiveLease) {
        activeInteractiveJobs.remove(lease.threadId, lease.job)
    }

    fun cancelActive(threadId: String, reason: String = "Assistant turn cancelled") {
        generations.getOrPut(threadId) { AtomicLong(0L) }.incrementAndGet()
        activeInteractiveJobs.remove(threadId)?.cancel(CancellationException(reason))
        Log.i(TAG, "stage=turn_cancelled thread=${threadId.takeLast(8)}")
    }
}

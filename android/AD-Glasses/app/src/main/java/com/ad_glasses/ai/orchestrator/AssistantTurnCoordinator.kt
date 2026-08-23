package com.ad_glasses.ai.orchestrator

import android.os.SystemClock
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Coordinates conversational state without serializing the network lifecycle.
 *
 * State mutations for one thread are kept ordered behind a very small mutex. Interactive turns are
 * latest-wins: a new phone/glasses request atomically replaces the older in-flight generation, then
 * cancels its coroutine. The generation guard also prevents an uncooperative/late transport result
 * from being persisted as the active answer.
 */
object AssistantTurnCoordinator {
    private const val TAG = "AssistantTiming"
    private val threadLocks = ConcurrentHashMap<String, Mutex>()
    private val interactiveStates = ConcurrentHashMap<String, InteractiveState>()

    data class InteractiveLease internal constructor(
        val threadId: String,
        val generation: Long,
        internal val job: Job,
    )

    private data class InteractiveState(
        val generation: Long,
        val job: Job?,
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
        var generation = 0L
        var previous: Job? = null
        interactiveStates.compute(threadId) { _, current ->
            generation = (current?.generation ?: 0L) + 1L
            previous = current?.job
            InteractiveState(generation = generation, job = job)
        }

        previous?.takeIf { it !== job && it.isActive }?.let { oldJob ->
            oldJob.cancel(CancellationException("Superseded by a newer assistant turn"))
            Log.i(TAG, "stage=turn_superseded thread=${threadId.takeLast(8)} generation=$generation")
        }
        return InteractiveLease(threadId, generation, job)
    }

    fun isCurrent(lease: InteractiveLease): Boolean {
        val current = interactiveStates[lease.threadId] ?: return false
        return current.generation == lease.generation &&
            current.job === lease.job &&
            lease.job.isActive
    }

    fun finishInteractiveTurn(lease: InteractiveLease) {
        interactiveStates.computeIfPresent(lease.threadId) { _, current ->
            if (current.generation == lease.generation && current.job === lease.job) {
                current.copy(job = null)
            } else {
                current
            }
        }
    }

    fun cancelActive(threadId: String, reason: String = "Assistant turn cancelled") {
        var previous: Job? = null
        interactiveStates.compute(threadId) { _, current ->
            previous = current?.job
            InteractiveState(
                generation = (current?.generation ?: 0L) + 1L,
                job = null,
            )
        }
        previous?.cancel(CancellationException(reason))
        Log.i(TAG, "stage=turn_cancelled thread=${threadId.takeLast(8)}")
    }
}

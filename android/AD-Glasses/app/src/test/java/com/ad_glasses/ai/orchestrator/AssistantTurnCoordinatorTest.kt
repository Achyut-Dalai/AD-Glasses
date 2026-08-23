package com.ad_glasses.ai.orchestrator

import java.util.Collections
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantTurnCoordinatorTest {
    @Test
    fun same_thread_state_mutations_are_serialized() = runBlocking {
        val events = Collections.synchronizedList(mutableListOf<String>())
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()

        val first = async {
            AssistantTurnCoordinator.withThreadState("thread-a") {
                events += "first-start"
                firstStarted.complete(Unit)
                releaseFirst.await()
                events += "first-end"
            }
        }
        firstStarted.await()
        val second = async {
            AssistantTurnCoordinator.withThreadState("thread-a") { events += "second" }
        }
        kotlinx.coroutines.yield()
        assertEquals(listOf("first-start"), events)
        releaseFirst.complete(Unit)
        first.await()
        second.await()

        assertEquals(listOf("first-start", "first-end", "second"), events)
    }

    @Test
    fun different_thread_state_can_progress_independently() = runBlocking {
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val otherFinished = CompletableDeferred<Unit>()

        val first = async {
            AssistantTurnCoordinator.withThreadState("thread-a") {
                firstStarted.complete(Unit)
                releaseFirst.await()
            }
        }
        firstStarted.await()
        val other = async {
            AssistantTurnCoordinator.withThreadState("thread-b") { otherFinished.complete(Unit) }
        }
        otherFinished.await()
        releaseFirst.complete(Unit)
        first.await()
        other.await()
    }

    @Test
    fun newer_interactive_turn_supersedes_the_previous_job() {
        val firstJob = Job()
        val firstLease = AssistantTurnCoordinator.beginInteractiveTurn("thread-c", firstJob)
        assertTrue(AssistantTurnCoordinator.isCurrent(firstLease))

        val secondJob = Job()
        val secondLease = AssistantTurnCoordinator.beginInteractiveTurn("thread-c", secondJob)

        assertTrue(firstJob.isCancelled)
        assertFalse(AssistantTurnCoordinator.isCurrent(firstLease))
        assertTrue(AssistantTurnCoordinator.isCurrent(secondLease))

        AssistantTurnCoordinator.finishInteractiveTurn(firstLease)
        assertTrue(AssistantTurnCoordinator.isCurrent(secondLease))
        AssistantTurnCoordinator.finishInteractiveTurn(secondLease)
        secondJob.cancel()
    }
}

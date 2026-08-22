package com.ad_glasses.ai.orchestrator

import java.util.Collections
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class AssistantTurnCoordinatorTest {
    @Test
    fun same_thread_turns_are_serialized_in_acceptance_order() = runBlocking {
        val events = Collections.synchronizedList(mutableListOf<String>())
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()

        val first = async {
            AssistantTurnCoordinator.withThread("thread-a") {
                events += "first-start"
                firstStarted.complete(Unit)
                releaseFirst.await()
                events += "first-end"
            }
        }
        firstStarted.await()
        val second = async {
            AssistantTurnCoordinator.withThread("thread-a") { events += "second" }
        }
        kotlinx.coroutines.yield()
        assertEquals(listOf("first-start"), events)
        releaseFirst.complete(Unit)
        first.await()
        second.await()

        assertEquals(listOf("first-start", "first-end", "second"), events)
    }

    @Test
    fun different_topics_can_progress_independently() {
        runBlocking {
            val firstStarted = CompletableDeferred<Unit>()
            val releaseFirst = CompletableDeferred<Unit>()
            val otherFinished = CompletableDeferred<Unit>()

            val first = async {
                AssistantTurnCoordinator.withThread("thread-a") {
                    firstStarted.complete(Unit)
                    releaseFirst.await()
                }
            }
            firstStarted.await()
            val other = async {
                AssistantTurnCoordinator.withThread("thread-b") { otherFinished.complete(Unit) }
            }
            otherFinished.await()
            releaseFirst.complete(Unit)
            first.await()
            other.await()
        }
    }
}

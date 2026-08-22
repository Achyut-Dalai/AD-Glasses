package com.fersaiyan.cyanbridge.localmodels.session

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalModelOperationGateTest {
    @Test
    fun secondEngineOperationWaitsForActiveGeneration() = runBlocking {
        val gate = LocalModelOperationGate()
        val generationStarted = CompletableDeferred<Unit>()
        val releaseGeneration = CompletableDeferred<Unit>()
        val unloadStarted = CompletableDeferred<Unit>()

        val generation = async(Dispatchers.Default) {
            gate.withExclusiveOperation {
                generationStarted.complete(Unit)
                releaseGeneration.await()
            }
        }
        generationStarted.await()

        val unload = async(Dispatchers.Default) {
            gate.withExclusiveOperation {
                unloadStarted.complete(Unit)
            }
        }

        assertNull(withTimeoutOrNull(100L) { unloadStarted.await() })
        releaseGeneration.complete(Unit)
        withTimeout(2_000L) {
            generation.await()
            unload.await()
        }
        assertTrue(unloadStarted.isCompleted)
    }
}

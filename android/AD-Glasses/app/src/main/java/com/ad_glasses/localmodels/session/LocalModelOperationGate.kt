package com.ad_glasses.localmodels.session

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serializes operations that read or mutate the native model engine.
 *
 * Native inference runtimes generally do not permit loading, unloading, or tokenizing a model
 * while generation is active. Cancellation intentionally bypasses this gate so it can interrupt
 * the operation currently holding it.
 */
internal class LocalModelOperationGate {
    private val mutex = Mutex()

    suspend fun <T> withExclusiveOperation(block: suspend () -> T): T {
        return mutex.withLock { block() }
    }
}

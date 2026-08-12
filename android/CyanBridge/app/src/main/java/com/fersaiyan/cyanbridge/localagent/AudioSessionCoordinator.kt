package com.achyut.adglasses.localagent

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

object AudioSessionCoordinator {

    private val busy = AtomicBoolean(false)
    @Volatile
    private var idleDeferred: CompletableDeferred<Unit>? = null

    fun isBusy(): Boolean = busy.get()

    fun markBusy() {
        busy.set(true)
        idleDeferred = CompletableDeferred()
    }

    fun markIdle() {
        idleDeferred?.complete(Unit)
        idleDeferred = null
        busy.set(false)
    }

    suspend fun waitUntilIdle(timeoutMs: Long = 5_000L) {
        if (!busy.get()) return
        val deferred = idleDeferred ?: return
        withTimeoutOrNull(timeoutMs) { deferred.await() }
    }
}

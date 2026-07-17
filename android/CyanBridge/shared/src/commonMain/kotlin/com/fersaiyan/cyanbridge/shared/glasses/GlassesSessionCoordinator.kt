package com.fersaiyan.cyanbridge.shared.glasses

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

/**
 * The vendor SDK exposes singleton BLE response/listener slots and a singleton P2P controller.
 * Only one workflow may control those resources at a time.
 */
enum class GlassesSession(val label: String) {
    MEDIA_SYNC("media sync"),
    LIVE_PREVIEW("live preview"),
    OTA("firmware update"),
}

class GlassesSessionLease internal constructor(
    val session: GlassesSession,
    internal val id: Long,
)

class BackgroundGlassesCommandPermit internal constructor(internal val id: Long)

object GlassesSessionCoordinator : SynchronizedObject() {
    private var activeLease: GlassesSessionLease? = null
    private var nextSessionId = 0L
    private var nextBackgroundCommandId = 0L
    private val activeBackgroundCommandIds = mutableSetOf<Long>()

    fun tryAcquire(session: GlassesSession): Boolean = synchronized(this) {
        return tryAcquireLease(session) != null
    }

    fun tryAcquireLease(session: GlassesSession): GlassesSessionLease? = synchronized(this) {
        if (activeLease != null || activeBackgroundCommandIds.isNotEmpty()) return null
        return GlassesSessionLease(session, ++nextSessionId).also { activeLease = it }
    }

    fun release(session: GlassesSession): Boolean = synchronized(this) {
        val lease = activeLease ?: return false
        if (lease.session != session) return false
        activeLease = null
        return true
    }

    fun release(lease: GlassesSessionLease): Boolean = synchronized(this) {
        if (activeLease !== lease) return false
        activeLease = null
        return true
    }

    fun currentSession(): GlassesSession? = synchronized(this) { activeLease?.session }

    fun isOwnedBy(session: GlassesSession): Boolean = synchronized(this) { activeLease?.session == session }

    fun isActive(lease: GlassesSessionLease): Boolean = synchronized(this) { activeLease === lease }

    fun canRunBackgroundCommand(): Boolean = synchronized(this) {
        activeLease == null && activeBackgroundCommandIds.isEmpty()
    }

    /** Atomically reserves the shared SDK response slot for a short one-shot command. */
    fun tryAcquireBackgroundCommand(): BackgroundGlassesCommandPermit? = synchronized(this) {
        if (activeLease != null || activeBackgroundCommandIds.isNotEmpty()) return null
        val permit = BackgroundGlassesCommandPermit(++nextBackgroundCommandId)
        activeBackgroundCommandIds += permit.id
        return permit
    }

    fun releaseBackgroundCommand(permit: BackgroundGlassesCommandPermit) = synchronized(this) {
        activeBackgroundCommandIds -= permit.id
    }

    fun isBackgroundCommandActive(permit: BackgroundGlassesCommandPermit): Boolean = synchronized(this) {
        permit.id in activeBackgroundCommandIds
    }

    /** A BLE reconnect discards any vendor callback slot that may have been left pending. */
    fun clearBackgroundCommands() = synchronized(this) {
        activeBackgroundCommandIds.clear()
    }

    /** A real BLE disconnect invalidates every pending vendor response and P2P workflow. */
    fun clearForDisconnectedDevice() = synchronized(this) {
        activeLease = null
        activeBackgroundCommandIds.clear()
    }
}

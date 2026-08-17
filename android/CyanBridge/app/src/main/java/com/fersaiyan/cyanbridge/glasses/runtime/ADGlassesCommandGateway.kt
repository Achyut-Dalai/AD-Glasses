package com.fersaiyan.cyanbridge.glasses.runtime

import com.fersaiyan.cyanbridge.MainActivity
import com.fersaiyan.cyanbridge.shared.glasses.GlassesDashboardAction
import com.fersaiyan.cyanbridge.shared.glasses.GlassesDashboardUiState
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicReference

/**
 * Process-level command boundary between AD Assistant/UI and the native glasses transport.
 *
 * The persistent runtime always gets first chance to handle a command. During the migration the
 * Activity adapter remains a compatibility fallback for vendor operations that have not yet been
 * extracted. Consumers never need to know which owner completed the action.
 */
object ADGlassesCommandGateway {
    interface Runtime {
        /** Return true only when this owner accepted the action. */
        fun dispatch(action: GlassesDashboardAction): Boolean
        fun snapshot(): GlassesDashboardUiState?
    }

    private val persistentRuntime = AtomicReference<Runtime?>(null)
    private val activityRuntime = AtomicReference<Runtime?>(null)

    fun attachPersistent(owner: Runtime) {
        persistentRuntime.set(owner)
    }

    fun detachPersistent(owner: Runtime) {
        persistentRuntime.compareAndSet(owner, null)
    }

    fun attachActivity(owner: Runtime) {
        activityRuntime.set(owner)
    }

    fun detachActivity(owner: Runtime) {
        activityRuntime.compareAndSet(owner, null)
    }

    fun dispatch(action: GlassesDashboardAction): Boolean {
        if (persistentRuntime.get()?.dispatch(action) == true) return true
        return activityRuntime.get()?.dispatch(action) == true
    }

    fun snapshot(): GlassesDashboardUiState? =
        persistentRuntime.get()?.snapshot() ?: activityRuntime.get()?.snapshot()

    fun hasPersistentRuntime(): Boolean = persistentRuntime.get() != null
    fun hasActivityFallback(): Boolean = activityRuntime.get() != null
}

/** Temporary adapter around inherited Activity-only operations. */
class ADLegacyMainActivityRuntime(activity: MainActivity) : ADGlassesCommandGateway.Runtime {
    private val activityRef = WeakReference(activity)

    override fun dispatch(action: GlassesDashboardAction): Boolean {
        val activity = activityRef.get() ?: return false
        activity.runOnUiThread {
            runCatching {
                val method = MainActivity::class.java.declaredMethods.firstOrNull {
                    it.name == "handleDashboardAction" && it.parameterTypes.size == 1
                } ?: return@runCatching
                method.isAccessible = true
                method.invoke(activity, action)
            }
        }
        return true
    }

    override fun snapshot(): GlassesDashboardUiState? {
        val activity = activityRef.get() ?: return null
        return runCatching {
            val getter = MainActivity::class.java.declaredMethods.firstOrNull {
                it.name == "getDashboardState" && it.parameterTypes.isEmpty()
            } ?: return@runCatching null
            getter.isAccessible = true
            getter.invoke(activity) as? GlassesDashboardUiState
        }.getOrNull()
    }
}

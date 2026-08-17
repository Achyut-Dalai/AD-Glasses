package com.fersaiyan.cyanbridge.glasses.runtime

import com.fersaiyan.cyanbridge.MainActivity
import com.fersaiyan.cyanbridge.shared.glasses.GlassesDashboardAction
import com.fersaiyan.cyanbridge.shared.glasses.GlassesDashboardUiState
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicReference

/**
 * Process-level command boundary between AD Assistant/UI and the native glasses transport.
 *
 * Consumers never need an Activity. During migration MainActivity is adapted here; the persistent
 * glasses runtime/service can replace that adapter without changing React Native, Gemini tools or
 * automation routing.
 */
object ADGlassesCommandGateway {
    interface Runtime {
        fun dispatch(action: GlassesDashboardAction): Boolean
        fun snapshot(): GlassesDashboardUiState?
    }

    private val runtime = AtomicReference<Runtime?>(null)

    fun attach(owner: Runtime) {
        runtime.set(owner)
    }

    fun detach(owner: Runtime) {
        runtime.compareAndSet(owner, null)
    }

    fun dispatch(action: GlassesDashboardAction): Boolean = runtime.get()?.dispatch(action) == true

    fun snapshot(): GlassesDashboardUiState? = runtime.get()?.snapshot()

    fun attached(): Boolean = runtime.get() != null
}

/** Temporary adapter around the inherited Activity-owned runtime. */
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

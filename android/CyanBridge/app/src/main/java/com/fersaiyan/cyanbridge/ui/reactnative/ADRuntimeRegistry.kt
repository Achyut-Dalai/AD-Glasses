package com.fersaiyan.cyanbridge.ui.reactnative

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.fersaiyan.cyanbridge.MainActivity
import com.fersaiyan.cyanbridge.ai.AiQuestionForegroundService
import com.fersaiyan.cyanbridge.glasses.runtime.ADGlassesCommandGateway
import com.fersaiyan.cyanbridge.glasses.runtime.ADLegacyMainActivityRuntime
import java.lang.ref.WeakReference

/**
 * Tracks Activity lifetime for compatibility while command ownership migrates to a persistent
 * native glasses runtime. Product code should use ADGlassesCommandGateway, not this registry.
 */
object ADRuntimeRegistry : Application.ActivityLifecycleCallbacks {
    private var installed = false
    private var mainActivityRef: WeakReference<MainActivity>? = null
    private var legacyRuntime: ADLegacyMainActivityRuntime? = null

    @Synchronized
    fun install(application: Application) {
        if (installed) return
        installed = true
        application.registerActivityLifecycleCallbacks(this)
    }

    fun mainActivity(): MainActivity? = mainActivityRef?.get()

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (activity !is MainActivity) return
        mainActivityRef = WeakReference(activity)
        legacyRuntime?.let(ADGlassesCommandGateway::detachActivity)
        legacyRuntime = ADLegacyMainActivityRuntime(activity).also(ADGlassesCommandGateway::attachActivity)
        // Start the already-declared connected-device service while a visible Activity makes the
        // request. It remains the persistent owner after the React/Activity UI goes away.
        AiQuestionForegroundService.startRuntime(activity.applicationContext)
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (activity !== mainActivityRef?.get()) return
        legacyRuntime?.let(ADGlassesCommandGateway::detachActivity)
        legacyRuntime = null
        mainActivityRef = null
    }

    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}

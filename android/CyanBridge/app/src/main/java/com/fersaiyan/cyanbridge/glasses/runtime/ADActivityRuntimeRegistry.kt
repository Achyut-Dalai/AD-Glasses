package com.fersaiyan.cyanbridge.glasses.runtime

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.fersaiyan.cyanbridge.MainActivity
import com.fersaiyan.cyanbridge.ai.AiQuestionForegroundService
import java.lang.ref.WeakReference

/**
 * Tracks the MainActivity-backed compatibility runtime while native transport ownership is
 * progressively moved into process-level services. The Compose UI does not depend on this
 * registry; it exists solely to preserve mature HeyCyan, sync and OTA command handling.
 */
object ADActivityRuntimeRegistry : Application.ActivityLifecycleCallbacks {
    private var installed = false
    private var mainActivityRef: WeakReference<MainActivity>? = null
    private var activityRuntime: ADLegacyMainActivityRuntime? = null

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
        activityRuntime?.let(ADGlassesCommandGateway::detachActivity)
        activityRuntime = ADLegacyMainActivityRuntime(activity).also(ADGlassesCommandGateway::attachActivity)
        AiQuestionForegroundService.startRuntime(activity.applicationContext)
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (activity !== mainActivityRef?.get()) return
        activityRuntime?.let(ADGlassesCommandGateway::detachActivity)
        activityRuntime = null
        mainActivityRef = null
    }

    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}

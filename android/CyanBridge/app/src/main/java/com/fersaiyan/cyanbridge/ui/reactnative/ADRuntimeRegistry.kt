package com.fersaiyan.cyanbridge.ui.reactnative

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.fersaiyan.cyanbridge.MainActivity
import java.lang.ref.WeakReference

/** Keeps a non-owning handle to the native glasses runtime while React owns presentation. */
object ADRuntimeRegistry : Application.ActivityLifecycleCallbacks {
    private var installed = false
    private var mainActivityRef: WeakReference<MainActivity>? = null

    @Synchronized
    fun install(application: Application) {
        if (installed) return
        installed = true
        application.registerActivityLifecycleCallbacks(this)
    }

    fun mainActivity(): MainActivity? = mainActivityRef?.get()

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (activity is MainActivity) mainActivityRef = WeakReference(activity)
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (activity === mainActivityRef?.get()) mainActivityRef = null
    }

    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}

package com.fersaiyan.cyanbridge.ui.reactnative

import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.fabricEnabled
import com.facebook.react.defaults.DefaultReactActivityDelegate
import java.lang.ref.WeakReference

/**
 * Activity access for native modules that need to launch Android-owned UI such as permission
 * prompts, document pickers, or system settings. React Native 0.86 no longer exposes the old
 * module-level currentActivity accessor, so keep this tied to the resumed product host instead.
 */
internal val currentActivity: ADReactNativeActivity?
    get() = ADReactNativeActivity.currentHostActivity()

/** Full-screen product shell. The heavy glasses runtime remains in MainActivity underneath. */
class ADReactNativeActivity : ReactActivity() {
    override fun getMainComponentName(): String =
        if (intent?.getBooleanExtra(EXTRA_WELCOME, false) == true) "ADGlassesWelcome" else "ADGlasses"

    override fun createReactActivityDelegate(): ReactActivityDelegate =
        DefaultReactActivityDelegate(this, mainComponentName, fabricEnabled)

    override fun onResume() {
        super.onResume()
        activeActivity = WeakReference(this)
    }

    override fun onPause() {
        if (activeActivity?.get() === this) {
            activeActivity = null
        }
        super.onPause()
    }

    companion object {
        private var activeActivity: WeakReference<ADReactNativeActivity>? = null

        internal fun currentHostActivity(): ADReactNativeActivity? = activeActivity?.get()

        const val EXTRA_WELCOME = "ad_react_welcome"
    }
}

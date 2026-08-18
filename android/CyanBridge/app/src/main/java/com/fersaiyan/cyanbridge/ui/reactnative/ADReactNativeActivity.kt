package com.fersaiyan.cyanbridge.ui.reactnative

import android.os.Bundle
import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.fabricEnabled
import com.facebook.react.defaults.DefaultReactActivityDelegate
import java.lang.ref.WeakReference

/**
 * Activity access for native modules that need to launch Android-owned UI such as permission
 * prompts, document pickers, or system settings. Keep this tied to the resumed product host so
 * bridge code never guesses which Android Activity should own a system interaction.
 */
internal val currentActivity: ADReactNativeActivity?
    get() = ADReactNativeActivity.currentHostActivity()

/** Full-screen product shell. The heavy glasses runtime remains in MainActivity underneath. */
class ADReactNativeActivity : ReactActivity() {
    override fun getMainComponentName(): String =
        if (intent?.getBooleanExtra(EXTRA_WELCOME, false) == true) "ADGlassesWelcome" else "ADGlasses"

    override fun createReactActivityDelegate(): ReactActivityDelegate =
        object : DefaultReactActivityDelegate(this, mainComponentName, fabricEnabled) {
            override fun getLaunchOptions(): Bundle? = Bundle().apply {
                intent?.getStringExtra(EXTRA_INITIAL_ROUTE)?.let { putString("initialRoute", it) }
                intent?.getStringExtra(EXTRA_INITIAL_PREFILL)?.let { putString("initialPrefill", it) }
                intent?.getStringExtra(EXTRA_INITIAL_THREAD_ID)?.let { putString("initialThreadId", it) }
                if (intent?.hasExtra(EXTRA_INITIAL_WEB_SEARCH) == true) {
                    putBoolean(
                        "initialWebSearchRequested",
                        intent.getBooleanExtra(EXTRA_INITIAL_WEB_SEARCH, false),
                    )
                }
            }
        }

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
        const val EXTRA_INITIAL_ROUTE = "ad_react_initial_route"
        const val EXTRA_INITIAL_PREFILL = "ad_react_initial_prefill"
        const val EXTRA_INITIAL_THREAD_ID = "ad_react_initial_thread_id"
        const val EXTRA_INITIAL_WEB_SEARCH = "ad_react_initial_web_search"
    }
}

package com.fersaiyan.cyanbridge.ui.reactnative

import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.fabricEnabled
import com.facebook.react.defaults.DefaultReactActivityDelegate

/** Full-screen product shell. The heavy glasses runtime remains in MainActivity underneath. */
class ADReactNativeActivity : ReactActivity() {
    override fun getMainComponentName(): String =
        if (intent?.getBooleanExtra(EXTRA_WELCOME, false) == true) "ADGlassesWelcome" else "ADGlasses"

    override fun createReactActivityDelegate(): ReactActivityDelegate =
        DefaultReactActivityDelegate(this, mainComponentName, fabricEnabled)

    companion object {
        const val EXTRA_WELCOME = "ad_react_welcome"
    }
}

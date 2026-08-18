package com.fersaiyan.cyanbridge.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.fersaiyan.cyanbridge.MainActivity
import com.fersaiyan.cyanbridge.ui.reactnative.ADReactNativeActivity

/** Launcher and external callback router. Product presentation lives in the React Native shell. */
class WelcomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // MainActivity remains underneath as the proven glasses runtime during the brownfield
        // migration. Forward native callback data to it, then keep React Native on top.
        val runtime = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            if (intent?.data != null) {
                action = intent.action
                data = intent.data
            }
        }
        val productShell = Intent(this, ADReactNativeActivity::class.java).apply {
            putExtra(ADReactNativeActivity.EXTRA_WELCOME, !isOnboardingCompleted())
        }
        startActivities(arrayOf(runtime, productShell))
        finish()
        overridePendingTransition(0, 0)
    }

    private fun isOnboardingCompleted(): Boolean {
        // Keep the existing preference namespace so upgrades retain first-run state.
        val prefs = getSharedPreferences("cyanbridge_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("onboarding_completed", false)
    }
}

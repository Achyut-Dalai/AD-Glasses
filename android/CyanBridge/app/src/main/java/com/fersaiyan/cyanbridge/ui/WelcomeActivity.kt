package com.fersaiyan.cyanbridge.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.fersaiyan.cyanbridge.BuildConfig
import com.fersaiyan.cyanbridge.MainActivity
import com.fersaiyan.cyanbridge.ui.reactnative.ADReactNativeActivity

/** Launcher and external callback router. Debug builds can compare both product shells. */
class WelcomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // External callbacks should never stop at the comparison chooser. Keep RN visible while
        // forwarding the callback to the hidden native runtime underneath.
        if (!BuildConfig.DEBUG || intent?.data != null) {
            launchReactNative()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Choose UI preview")
            .setMessage(
                "Both previews use the same native glasses runtime. Compare the UI on your phone; " +
                    "this chooser is debug-only and can be removed after you pick a direction.",
            )
            .setPositiveButton("React Native") { _, _ -> launchReactNative() }
            .setNegativeButton("Jetpack Compose") { _, _ -> launchCompose() }
            .setOnCancelListener { launchReactNative() }
            .show()
    }

    private fun launchReactNative() {
        val productShell = Intent(this, ADReactNativeActivity::class.java).apply {
            putExtra(ADReactNativeActivity.EXTRA_WELCOME, !isOnboardingCompleted())
        }
        startActivities(arrayOf(runtimeIntent(), productShell))
        finishLauncher()
    }

    private fun launchCompose() {
        // MainActivity still owns the mature HeyCyan/OTA runtime and its retained Compose shell.
        // Bringing it to the top also clears an existing RN Activity above it, making comparison
        // repeatable by simply launching the debug app again.
        startActivity(runtimeIntent())
        finishLauncher()
    }

    private fun runtimeIntent(): Intent = Intent(this, MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (this@WelcomeActivity.intent?.data != null) {
            action = this@WelcomeActivity.intent.action
            data = this@WelcomeActivity.intent.data
        }
    }

    private fun finishLauncher() {
        finish()
        overridePendingTransition(0, 0)
    }

    private fun isOnboardingCompleted(): Boolean {
        // Keep the existing preference namespace so upgrades retain first-run state.
        val prefs = getSharedPreferences("cyanbridge_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("onboarding_completed", false)
    }
}

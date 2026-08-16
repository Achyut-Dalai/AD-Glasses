package com.fersaiyan.cyanbridge.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import com.fersaiyan.cyanbridge.MainActivity
import com.fersaiyan.cyanbridge.ui.adglasses.ADWelcomeScreen

class WelcomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (isOnboardingCompleted()) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        setContent {
            ADWelcomeScreen(
                onStartSetup = {
                    completeOnboarding()
                    startActivities(
                        arrayOf(
                            Intent(this, MainActivity::class.java),
                            Intent(this, DeviceBindActivity::class.java),
                        ),
                    )
                    finish()
                },
                onExplore = {
                    completeOnboarding()
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                },
            )
        }
    }

    private fun isOnboardingCompleted(): Boolean {
        // Keep the existing preference namespace so upgrades retain first-run state.
        val prefs = getSharedPreferences("cyanbridge_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("onboarding_completed", false)
    }

    private fun completeOnboarding() {
        getSharedPreferences("cyanbridge_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("onboarding_completed", true)
            .apply()
    }
}

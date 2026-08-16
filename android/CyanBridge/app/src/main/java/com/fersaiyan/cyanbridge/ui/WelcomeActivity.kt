package com.fersaiyan.cyanbridge.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
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
                onSupportedDevices = {
                    Toast.makeText(
                        this,
                        "HeyCyan is primary. Eyevue, Meta and Meizu support is experimental; generic audio is limited.",
                        Toast.LENGTH_LONG,
                    ).show()
                },
                onPrivacy = {
                    Toast.makeText(
                        this,
                        "Local data stays on this phone unless you deliberately configure an external service.",
                        Toast.LENGTH_LONG,
                    ).show()
                },
            )
        }
    }

    private fun isOnboardingCompleted(): Boolean {
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

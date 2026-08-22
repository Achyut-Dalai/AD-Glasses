package com.ad_glasses.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.ad_glasses.MainActivity

/** Lightweight launcher and external callback router for the Compose application. */
class WelcomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                if (this@WelcomeActivity.intent?.data != null) {
                    action = this@WelcomeActivity.intent.action
                    data = this@WelcomeActivity.intent.data
                }
            },
        )
        finish()
        overridePendingTransition(0, 0)
    }
}

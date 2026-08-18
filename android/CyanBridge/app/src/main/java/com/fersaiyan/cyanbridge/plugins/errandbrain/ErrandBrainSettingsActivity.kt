package com.fersaiyan.cyanbridge.plugins.errandbrain

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/** Compatibility component for old internal intents; Cron has been removed from the product. */
class ErrandBrainSettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ErrandBrainPreferences.setEnabled(this, false)
        Toast.makeText(this, "Cron has been removed from AD Glasses.", Toast.LENGTH_SHORT).show()
        finish()
    }
}

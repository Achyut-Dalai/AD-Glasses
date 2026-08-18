package com.fersaiyan.cyanbridge.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Small compatibility base retained for device Activities that still share lifecycle helpers.
 * It no longer assumes or decorates an XML title bar; Compose screens own their own chrome.
 */
open class BaseActivity : AppCompatActivity() {
    private var isActive: Boolean = false

    /** Current Activity instance retained for historical subclasses. */
    protected var activity: Activity? = null

    /** Log output tag retained for historical subclasses. */
    protected val TAG: String = this.javaClass.simpleName

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activity = this
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        isActive = true
    }

    override fun onPause() {
        isActive = false
        super.onPause()
    }

    override fun onDestroy() {
        activity = null
        super.onDestroy()
    }
}

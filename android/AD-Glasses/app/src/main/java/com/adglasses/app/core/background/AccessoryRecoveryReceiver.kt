package com.adglasses.app.core.background

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.adglasses.app.AppGraph

/**
 * Restores the connected-device foreground service after a reboot or an in-place app update.
 * It deliberately does nothing until the user has paired a device and granted Bluetooth access.
 */
class AccessoryRecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)) return
        if (!AppGraph.glasses.hasRememberedDevice()) return
        if (Build.VERSION.SDK_INT >= 31 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) return

        runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, AccessoryService::class.java),
            )
        }
    }
}

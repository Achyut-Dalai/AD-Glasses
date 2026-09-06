package com.adglasses.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.startForegroundService
import com.adglasses.app.core.background.AccessoryService
import com.adglasses.app.ui.ADGlassesRoot
import com.adglasses.app.ui.theme.ADGlassesTheme

class MainActivity : ComponentActivity() {
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { startConnectionService() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestCorePermissionsIfNeeded()
        setContent {
            ADGlassesTheme {
                ADGlassesRoot()
            }
        }
    }

    private fun requestCorePermissionsIfNeeded() {
        val permissions = buildList {
            if (Build.VERSION.SDK_INT in 29..30) add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= 31) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (Build.VERSION.SDK_INT >= 33) {
                add(Manifest.permission.POST_NOTIFICATIONS)
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }

        if (permissions.isEmpty()) {
            startConnectionService()
        } else {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun startConnectionService() {
        runCatching {
            startForegroundService(this, Intent(this, AccessoryService::class.java))
        }
    }
}

package com.fersaiyan.cyanbridge.devices.meizumyvu

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.fersaiyan.cyanbridge.MainActivity
import com.fersaiyan.cyanbridge.ui.ensureNotificationPermission
import com.fersaiyan.cyanbridge.ui.hasNotificationPermission

/** Keeps MYVU's BLE heartbeat and per-session RFCOMM relay alive off-screen. */
class MeizuMyvuConnectionService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!hasNotificationPermission(this)) {
            Log.w(TAG, "Cannot start MYVU connection: notification permission is missing")
            stopSelf(startId)
            return START_NOT_STICKY
        }
        startConnectedDeviceForeground()
        intent?.getStringExtra(EXTRA_MAC)?.let { address ->
            MeizuMyvuManager.getInstance(this).connectTransport(address)
        }
        return START_REDELIVER_INTENT
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        MeizuMyvuManager.getInstance(this).stopTransport()
        super.onDestroy()
    }

    private fun startConnectedDeviceForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "MYVU connection", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
                description = "Keeps the MYVU display and microphone connection active"
            },
        )
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("MYVU glasses")
            .setContentText("Keeping the display and microphone connection active")
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val TAG = "MeizuMyvuService"
        private const val ACTION_CONNECT = "com.fersaiyan.cyanbridge.meizu.CONNECT"
        private const val EXTRA_MAC = "mac"
        private const val CHANNEL_ID = "myvu_connection"
        private const val NOTIFICATION_ID = 7101

        fun intent(context: Context): Intent = Intent(context, MeizuMyvuConnectionService::class.java)

        fun start(context: Context, macAddress: String) {
            if (!hasNotificationPermission(context)) {
                if (context is FragmentActivity) {
                    ensureNotificationPermission(context, "MYVU connection") {
                        start(context, macAddress)
                    }
                } else {
                    Log.w(TAG, "Cannot request MYVU notification permission without an Activity context")
                }
                return
            }
            val serviceIntent = intent(context).setAction(ACTION_CONNECT).putExtra(EXTRA_MAC, macAddress)
            runCatching {
                ContextCompat.startForegroundService(context, serviceIntent)
            }.onFailure { error ->
                // Android can reject a foreground-service start from a background
                // receiver. The active app process can still establish the link.
                Log.w("MeizuMyvuService", "Foreground connection service was blocked", error)
                MeizuMyvuManager.getInstance(context).connectTransport(macAddress)
            }
        }
    }
}

package com.adglasses.app.core.background

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.adglasses.app.AppGraph
import com.adglasses.app.MainActivity
import com.adglasses.app.R
import com.adglasses.app.core.model.ConnectionPhase
import com.adglasses.app.core.model.GlassesConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class AccessoryService : Service() {
    companion object {
        const val CHANNEL_ID = "ad_glasses_connection"
        const val NOTIFICATION_ID = 1001
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.accessory_service_channel),
                NotificationManager.IMPORTANCE_LOW,
            )
        )

        startForeground(NOTIFICATION_ID, buildNotification(AppGraph.glasses.state.value))

        serviceScope.launch {
            AppGraph.glasses.state.collect { state ->
                notificationManager.notify(NOTIFICATION_ID, buildNotification(state))
            }
        }

        AppGraph.glasses.resumeRememberedConnection()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AppGraph.glasses.resumeRememberedConnection()
        return START_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(state: GlassesConnectionState): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val title = when {
            state.isReady && !state.deviceName.isNullOrBlank() -> state.deviceName
            state.isReady -> "AD Glasses"
            else -> getString(R.string.accessory_service_title)
        }
        val statusText = connectionText(state)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title)
            .setContentText(statusText)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun connectionText(state: GlassesConnectionState): String {
        state.detail?.takeIf { it.isNotBlank() }?.let { detail ->
            if (state.phase != ConnectionPhase.Ready) return detail
        }

        return when (state.phase) {
            ConnectionPhase.Disconnected -> "Disconnected • background reconnect ready"
            ConnectionPhase.Scanning -> "Scanning for glasses"
            ConnectionPhase.Connecting -> "Connecting to glasses"
            ConnectionPhase.Discovering -> "Discovering verified glasses services"
            ConnectionPhase.Initializing -> "Synchronizing glasses"
            ConnectionPhase.Error -> state.detail ?: "Connection needs attention"
            ConnectionPhase.Ready -> buildString {
                append("Connected")
                state.batteryPercent?.let { battery ->
                    append(" • ").append(battery).append('%')
                    if (state.charging) append(" charging")
                }
            }
        }
    }
}

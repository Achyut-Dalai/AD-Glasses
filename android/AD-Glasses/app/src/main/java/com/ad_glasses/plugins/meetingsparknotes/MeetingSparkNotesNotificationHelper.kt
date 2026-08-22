package com.ad_glasses.plugins.meetingsparknotes

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.ad_glasses.R
import com.ad_glasses.navigation.ADAppLaunchIntents

object MeetingSparkNotesNotificationHelper {
    const val CHANNEL_ID = "meeting_spark_notes_service"
    const val NOTIFICATION_ID = 77422

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val ch = NotificationChannel(
            CHANNEL_ID,
            "Soundbites",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Live meeting capture and concise AI notes"
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        nm.createNotificationChannel(ch)
    }

    fun buildNotification(context: Context, content: String): Notification {
        val openPi = PendingIntent.getActivity(
            context,
            0,
            ADAppLaunchIntents.productHome(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val stopIntent = Intent(context, MeetingSparkNotesService::class.java).apply {
            action = MeetingSparkNotesService.ACTION_STOP
        }
        val stopPi = PendingIntent.getService(
            context,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_ad_glasses)
            .setContentTitle("Soundbites")
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setOngoing(true)
            .setContentIntent(openPi)
            .addAction(
                NotificationCompat.Action.Builder(
                    0,
                    "Stop",
                    stopPi,
                ).build(),
            )
            .build()
    }

    fun updateNotification(context: Context, content: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runCatching { nm.notify(NOTIFICATION_ID, buildNotification(context, content)) }
    }
}

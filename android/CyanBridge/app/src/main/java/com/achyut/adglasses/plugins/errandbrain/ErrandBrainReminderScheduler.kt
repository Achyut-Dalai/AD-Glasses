package com.achyut.adglasses.plugins.errandbrain

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

object ErrandBrainReminderScheduler {
    private const val ACTION_REMINDER = "com.achyut.adglasses.ACTION_ERRAND_BRAIN_REMINDER"
    private const val EXTRA_REMINDER_ID = "reminder_id"
    private const val EXTRA_TITLE = "title"
    private const val EXTRA_DESCRIPTION = "description"

    fun schedule(context: Context, reminder: ReminderEntry) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = pendingIntent(context, reminder)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.reminderTime, pendingIntent)
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, reminder.reminderTime, pendingIntent)
        }
    }

    fun cancel(context: Context, reminder: ReminderEntry) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = pendingIntent(context, reminder)
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun pendingIntent(context: Context, reminder: ReminderEntry): PendingIntent {
        val intent = Intent(context, ErrandBrainReminderReceiver::class.java).apply {
            action = ACTION_REMINDER
            putExtra(EXTRA_REMINDER_ID, reminder.id)
            putExtra(EXTRA_TITLE, reminder.title)
            putExtra(EXTRA_DESCRIPTION, reminder.description)
        }
        return PendingIntent.getBroadcast(
            context,
            reminder.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    internal fun isReminderIntent(intent: Intent?): Boolean = intent?.action == ACTION_REMINDER

    internal fun reminderId(intent: Intent): String = intent.getStringExtra(EXTRA_REMINDER_ID).orEmpty()

    internal fun title(intent: Intent): String = intent.getStringExtra(EXTRA_TITLE).orEmpty()

    internal fun description(intent: Intent): String = intent.getStringExtra(EXTRA_DESCRIPTION).orEmpty()
}

class ErrandBrainReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!ErrandBrainReminderScheduler.isReminderIntent(intent)) return
        val reminderId = ErrandBrainReminderScheduler.reminderId(intent)
        if (reminderId.isBlank()) return

        val store = ErrandBrainStore().apply { load(context) }
        store.markReminderTriggered(reminderId)
        store.persist(context, ErrandBrainPreferences.getMaxHistory(context))

        val title = ErrandBrainReminderScheduler.title(intent).ifBlank { "Reminder" }
        val description = ErrandBrainReminderScheduler.description(intent)
        ErrandBrainNotificationHelper.showReminder(context, reminderId, title, description)
        Log.i(TAG, "Delivered reminder id=$reminderId")
    }

    private companion object {
        private const val TAG = "ErrandBrainReminder"
    }
}

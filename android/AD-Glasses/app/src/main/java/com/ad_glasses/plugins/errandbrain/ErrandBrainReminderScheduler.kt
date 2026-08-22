package com.ad_glasses.plugins.errandbrain

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Inert compatibility shell. Cron reminders are no longer scheduled by AD Glasses. */
object ErrandBrainReminderScheduler {
    fun schedule(context: Context, reminder: ReminderEntry) = Unit
    fun cancel(context: Context, reminder: ReminderEntry) = Unit
}

/** Existing alarms from older installs are intentionally ignored after Cron removal. */
class ErrandBrainReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Unit
}

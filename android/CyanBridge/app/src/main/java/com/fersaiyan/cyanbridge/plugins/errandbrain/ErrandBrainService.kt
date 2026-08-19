package com.fersaiyan.cyanbridge.plugins.errandbrain

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder

/**
 * Inert compatibility shell for inherited MainActivity references.
 *
 * Cron / Errand Brain is retired from the AD Glasses product. Keeping this tiny class avoids a
 * risky rewrite of the legacy activity while ensuring old preferences, shortcuts or intents cannot
 * start listening, create tasks or schedule reminders.
 */
class ErrandBrainService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ErrandBrainPreferences.setEnabled(this, false)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ErrandBrainPreferences.setEnabled(this, false)
        stopSelf()
        return START_NOT_STICKY
    }

    companion object {
        const val ACTION_START = "com.fersaiyan.cyanbridge.ACTION_START_ERRAND"
        const val ACTION_STOP = "com.fersaiyan.cyanbridge.ACTION_STOP_ERRAND"
        const val ACTION_ADD_TASK = "com.fersaiyan.cyanbridge.ACTION_ADD_TASK"
        const val ACTION_ADD_REMINDER = "com.fersaiyan.cyanbridge.ACTION_ADD_REMINDER"
        const val EXTRA_TASK_TITLE = "task_title"
        const val EXTRA_REMINDER_TITLE = "reminder_title"
        const val EXTRA_REMINDER_TIME = "reminder_time"

        fun start(context: Context) {
            ErrandBrainPreferences.setEnabled(context, false)
        }

        fun stop(context: Context) {
            ErrandBrainPreferences.setEnabled(context, false)
            runCatching { context.stopService(Intent(context, ErrandBrainService::class.java)) }
        }

        fun addTask(context: Context, taskTitle: String) {
            ErrandBrainPreferences.setEnabled(context, false)
        }

        fun addReminder(context: Context, reminderTitle: String, reminderTime: Long) {
            ErrandBrainPreferences.setEnabled(context, false)
        }
    }
}

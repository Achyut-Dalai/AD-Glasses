package com.fersaiyan.cyanbridge.automation

import android.content.Context
import android.content.Intent
import java.util.UUID

/**
 * Screen-off app-to-Tasker contract.
 *
 * Generic trigger events contain metadata only. AI-planned phone actions include the goal
 * only when the user has explicitly selected Tasker as their Automation executor.
 */
object AutomationEventBroadcaster {
    const val ACTION_AUTOMATION_EVENT = "com.fersaiyan.cyanbridge.AUTOMATION_EVENT"
    const val TASKER_PACKAGE = "net.dinglisch.android.taskerm"

    const val EXTRA_EVENT = "event"
    const val EXTRA_SOURCE = "source"
    const val EXTRA_GESTURE = "gesture"
    const val EXTRA_COMMAND_TYPE = "command_type"
    const val EXTRA_REQUEST_ID = "request_id"
    const val EXTRA_TIMESTAMP = "timestamp"
    const val EXTRA_GOAL = "goal"

    fun sendTrigger(
        context: Context,
        event: String,
        source: String = "glasses",
        gesture: String? = null,
        commandType: String? = null,
        requestId: String = UUID.randomUUID().toString(),
        timestamp: Long = System.currentTimeMillis(),
    ) {
        context.applicationContext.sendBroadcast(
            baseIntent(event, source, gesture, commandType, requestId, timestamp),
        )
    }

    fun sendPhoneAction(
        context: Context,
        goal: String,
        requestId: String = UUID.randomUUID().toString(),
        timestamp: Long = System.currentTimeMillis(),
    ) {
        val cleanGoal = goal.trim()
        require(cleanGoal.isNotEmpty()) { "Automation goal cannot be blank" }
        context.applicationContext.sendBroadcast(
            baseIntent(
                event = "phone_action",
                source = "glasses_ai",
                gesture = null,
                commandType = "phone_action",
                requestId = requestId,
                timestamp = timestamp,
            ).putExtra(EXTRA_GOAL, cleanGoal),
        )
    }

    private fun baseIntent(
        event: String,
        source: String,
        gesture: String?,
        commandType: String?,
        requestId: String,
        timestamp: Long,
    ): Intent = Intent(ACTION_AUTOMATION_EVENT).apply {
        // Keep the event private to Tasker instead of advertising it to every installed app.
        setPackage(TASKER_PACKAGE)
        putExtra(EXTRA_EVENT, event)
        putExtra(EXTRA_SOURCE, source)
        gesture?.let { putExtra(EXTRA_GESTURE, it) }
        commandType?.let { putExtra(EXTRA_COMMAND_TYPE, it) }
        putExtra(EXTRA_REQUEST_ID, requestId)
        putExtra(EXTRA_TIMESTAMP, timestamp)
    }
}

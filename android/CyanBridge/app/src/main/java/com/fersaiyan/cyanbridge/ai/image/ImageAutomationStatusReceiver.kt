package com.fersaiyan.cyanbridge.ai.image

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager

/** Receives only session-token-bound callbacks emitted by the Tasker profile. */
class ImageAutomationStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ExternalImageAutomationIntents.profileAction(context.packageName)) {
            val accepted = TaskerImageProfileStore.verifyAndRecord(
                context = context,
                target = intent.getStringExtra(ExternalImageAutomationIntents.EXTRA_PROFILE_TARGET),
                version = intent.getStringExtra(ExternalImageAutomationIntents.EXTRA_PROFILE_VERSION),
                token = intent.getStringExtra(ExternalImageAutomationIntents.EXTRA_PROFILE_TOKEN),
            )
            if (!accepted) Log.w(TAG, "Ignoring assistant profile callback with an invalid setup token")
            return
        }
        if (intent.action != ExternalImageAutomationIntents.statusAction(context.packageName)) return

        val sessionId = intent.getStringExtra(ImageQuestionBroadcast.EXTRA_CALLBACK_SESSION)
        val callbackToken = intent.getStringExtra(ImageQuestionBroadcast.EXTRA_CALLBACK_TOKEN)
        if (!ExternalImageAutomationStore.acceptsCallback(context, sessionId, callbackToken)) {
            Log.w(TAG, "Ignoring image automation callback for an unknown session")
            return
        }

        val stage = ExternalImageAutomationStage.fromWireName(
            intent.getStringExtra(ExternalImageAutomationIntents.EXTRA_STATUS),
        )
        if (stage == null || stage == ExternalImageAutomationStage.IDLE) {
            Log.w(TAG, "Ignoring image automation callback with invalid status")
            return
        }

        val session = ExternalImageAutomationStore.recordCallback(
            context = context,
            stage = stage,
            error = intent.getStringExtra(ExternalImageAutomationIntents.EXTRA_ERROR),
        ) ?: return
        Log.i(TAG, "Tasker callback: ${session.state.stage.wireName}, error=${session.state.error.orEmpty()}")

        LocalBroadcastManager.getInstance(context).sendBroadcast(
            Intent(ExternalImageAutomationIntents.internalStatusAction(context.packageName)),
        )
    }

    private companion object {
        const val TAG = "ImageAutomation"
    }
}

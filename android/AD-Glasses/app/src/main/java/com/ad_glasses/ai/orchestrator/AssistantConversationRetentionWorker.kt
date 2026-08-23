package com.ad_glasses.ai.orchestrator

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters

/**
 * Compatibility shell for installs that may still have the former seven-day retention worker in
 * WorkManager's persisted database. Chat history is now user-managed, so this worker deliberately
 * performs no deletion. New app sessions also cancel the legacy unique work.
 */
class AssistantConversationRetentionWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = Result.success()

    companion object {
        private const val UNIQUE_WORK_NAME = "ad_assistant_conversation_retention"

        fun cancelLegacySchedule(context: Context) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }
}

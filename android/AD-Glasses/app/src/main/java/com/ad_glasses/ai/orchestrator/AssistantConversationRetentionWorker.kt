package com.ad_glasses.ai.orchestrator

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Periodic backstop for deleting inactive AD assistant conversations after seven days. */
class AssistantConversationRetentionWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = runCatching {
        AssistantConversationSession.get(applicationContext).pruneExpiredConversations()
    }.fold(
        onSuccess = { Result.success() },
        onFailure = { Result.retry() },
    )

    companion object {
        private const val UNIQUE_WORK_NAME = "ad_assistant_conversation_retention"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<AssistantConversationRetentionWorker>(
                1L,
                TimeUnit.HOURS,
            ).build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}

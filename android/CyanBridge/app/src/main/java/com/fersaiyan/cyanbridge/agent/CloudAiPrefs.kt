package com.fersaiyan.cyanbridge.agent

import android.content.Context
import com.fersaiyan.cyanbridge.ai.router.AiProviderPrefs

/**
 * Compatibility facade for inherited host code.
 *
 * Cloud AI now has one provider/model selection in [AiProviderPrefs]. The old subscription-era
 * per-purpose model buckets are intentionally gone.
 */
@Deprecated("Use AiProviderPrefs directly")
object CloudAiPrefs {
    fun getRequestsModel(context: Context): String = AiProviderPrefs.getModel(context)

    fun setRequestsModel(context: Context, model: String) {
        AiProviderPrefs.setModel(context, AiProviderPrefs.getApiProvider(context), model)
    }

    fun getQuestionsModel(context: Context): String = AiProviderPrefs.getModel(context)

    fun setQuestionsModel(context: Context, model: String) {
        AiProviderPrefs.setModel(context, AiProviderPrefs.getApiProvider(context), model)
    }

    fun getTasksModel(context: Context): String = AiProviderPrefs.getModel(context)

    fun setTasksModel(context: Context, model: String) {
        AiProviderPrefs.setModel(context, AiProviderPrefs.getApiProvider(context), model)
    }
}

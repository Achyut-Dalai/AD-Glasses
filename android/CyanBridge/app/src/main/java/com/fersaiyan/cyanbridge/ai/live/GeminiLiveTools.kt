package com.fersaiyan.cyanbridge.ai.live

import android.content.Context
import com.fersaiyan.cyanbridge.MainActivity
import com.fersaiyan.cyanbridge.automation.AutomationEventBroadcaster
import com.fersaiyan.cyanbridge.shared.glasses.GlassesDashboardAction
import com.fersaiyan.cyanbridge.ui.reactnative.ADRuntimeRegistry
import org.json.JSONObject

/** Executes only the narrow tools exposed to a Gemini Live AD session. */
fun interface GeminiLiveToolExecutor {
    suspend fun execute(name: String, args: JSONObject): JSONObject
}

object NoOpGeminiLiveToolExecutor : GeminiLiveToolExecutor {
    override suspend fun execute(name: String, args: JSONObject): JSONObject = JSONObject()
        .put("ok", false)
        .put("error", "Tool $name is unavailable in this session")
}

/**
 * Tool implementation for the displayless glasses runtime.
 *
 * Precise glasses actions stay native. Broad Android actions are emitted to the background
 * automation contract. Sensitive/external actions require a second, confirmed tool call.
 */
class ADGeminiLiveToolExecutor(context: Context) : GeminiLiveToolExecutor {
    private val appContext = context.applicationContext

    override suspend fun execute(name: String, args: JSONObject): JSONObject = when (name) {
        "capture_photo" -> dispatch(GlassesDashboardAction.CapturePhoto, "Photo capture requested")
        "toggle_video" -> dispatch(GlassesDashboardAction.ToggleVideo, "Video recording toggled")
        "start_recording" -> dispatch(GlassesDashboardAction.StartMeetingCapture, "Audio recording started")
        "stop_recording" -> dispatch(GlassesDashboardAction.StopMeetingCapture, "Audio recording stopped")
        "start_sync" -> dispatch(GlassesDashboardAction.StartSync, "Media sync started")
        "stop_sync" -> dispatch(GlassesDashboardAction.StopSync, "Media sync stopped")
        "background_phone_action" -> executeBackgroundPhoneAction(args)
        else -> JSONObject().put("ok", false).put("error", "Unknown AD tool: $name")
    }

    private fun executeBackgroundPhoneAction(args: JSONObject): JSONObject {
        val goal = args.optString("goal").trim()
        if (goal.isBlank()) {
            return JSONObject().put("ok", false).put("error", "A phone-action goal is required")
        }
        val confirmed = args.optBoolean("confirmed", false)
        val risk = AutomationConfirmationPolicy.classify(goal)
        if (risk.requiresConfirmation && !confirmed) {
            return JSONObject()
                .put("ok", false)
                .put("requires_confirmation", true)
                .put("reason", risk.reason)
                .put("goal", goal)
        }

        // Tasker is a true background contract and must not depend on MainActivity being alive.
        AutomationEventBroadcaster.sendPhoneAction(appContext, goal)
        return JSONObject()
            .put("ok", true)
            .put("executed_in_background", true)
            .put("goal", goal)
    }

    private fun dispatch(action: GlassesDashboardAction, message: String): JSONObject {
        val activity = ADRuntimeRegistry.mainActivity()
            ?: return JSONObject()
                .put("ok", false)
                .put("error", "This glasses action still requires the native device runtime to be attached")
        return runCatching {
            activity.runOnUiThread {
                val method = MainActivity::class.java.declaredMethods.firstOrNull {
                    it.name == "handleDashboardAction" && it.parameterTypes.size == 1
                } ?: return@runOnUiThread
                method.isAccessible = true
                method.invoke(activity, action)
            }
            JSONObject().put("ok", true).put("message", message)
        }.getOrElse { error ->
            JSONObject().put("ok", false).put("error", error.message ?: "Native glasses action failed")
        }
    }
}

data class AutomationRisk(
    val requiresConfirmation: Boolean,
    val reason: String = "",
)

/** Conservative boundary for tool calls that can affect other people, money or user data. */
object AutomationConfirmationPolicy {
    private val confirmationPatterns = listOf(
        Regex("\\b(call|dial|phone)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(send|text|message|email|reply|post|share)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(pay|purchase|buy|order|transfer money|bank)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(delete|erase|remove account|factory reset)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(password|passcode|security|permission|install|uninstall)\\b", RegexOption.IGNORE_CASE),
    )

    fun classify(goal: String): AutomationRisk {
        val requires = confirmationPatterns.any { it.containsMatchIn(goal) }
        return if (requires) {
            AutomationRisk(
                requiresConfirmation = true,
                reason = "This action communicates externally or changes sensitive user state. Ask the user for spoken confirmation first.",
            )
        } else {
            AutomationRisk(requiresConfirmation = false)
        }
    }
}

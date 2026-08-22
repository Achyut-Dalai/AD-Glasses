package com.ad_glasses.ai.live

import android.content.Context
import com.ad_glasses.glasses.runtime.ADGlassesCommandGateway
import com.ad_glasses.localagent.LocalAgentController
import com.ad_glasses.shared.glasses.GlassesDashboardAction
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

        val result = LocalAgentController.start(appContext, goal)
        return JSONObject()
            .put("ok", result.ok)
            .put("executed_in_background", false)
            .put("goal", goal)
            .put("message", result.userMessage)
            .apply { result.error?.let { put("error", it) } }
    }

    private fun dispatch(action: GlassesDashboardAction, message: String): JSONObject {
        if (!ADGlassesCommandGateway.dispatch(action)) {
            return JSONObject()
                .put("ok", false)
                .put("error", "The native glasses runtime is not attached")
        }
        return JSONObject().put("ok", true).put("message", message)
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

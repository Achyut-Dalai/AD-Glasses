package com.ad_glasses.plugins.localagent

import android.content.Context
import com.ad_glasses.agent.LocalAgentPrefs as AutomationPrefs
import com.ad_glasses.ai.orchestrator.AssistantCapabilityRuntimeEvents
import com.ad_glasses.ai.router.AiProviderPrefs
import com.ad_glasses.ai.router.AiProviderType
import com.ad_glasses.localagent.LocalAgentController
import com.ad_glasses.localagent.LocalAgentNotificationSpeaker
import com.ad_glasses.localagent.LocalAgentPrefs as RuntimePrefs
import com.ad_glasses.localagent.LocalAgentTelegramService
import com.ad_glasses.shared.plugins.NativePluginIds
import com.ad_glasses.shared.settings.AgentProviderType
import com.ad_glasses.ui.CommunityPluginPrefs

/**
 * Native-plugin facade for the existing Local Agent runtime.
 *
 * The automation preference remains the source of truth so upgrading users keep
 * their existing phone-control setting. The native-plugin flag mirrors it for
 * the plugin registry and shortcut surfaces.
 */
object LocalAgentPlugin {

    fun isEnabled(context: Context): Boolean =
        AutomationPrefs.isLocalAgentAutomationEnabled(context)

    fun setEnabled(context: Context, enabled: Boolean) {
        val changed = isEnabled(context) != enabled
        AutomationPrefs.setLocalAgentAutomationEnabled(context, enabled)
        CommunityPluginPrefs.setNativePluginEnabled(context, NativePluginIds.LOCAL_AGENT, enabled)
        if (!enabled) {
            LocalAgentController.stop(context)
            LocalAgentTelegramService.stop(context)
            LocalAgentNotificationSpeaker.stop()
        } else if (RuntimePrefs.isTelegramRemoteControlEnabled(context)) {
            // Remote control was explicitly configured earlier; restoring phone control can
            // resume only that already allowlisted Telegram listener.
            LocalAgentTelegramService.start(context)
        }
        if (changed) AssistantCapabilityRuntimeEvents.notifyChanged()
    }

    fun start(context: Context, goal: String? = null): LocalAgentController.CommandResult {
        val trimmedGoal = goal?.trim().orEmpty()
        if (trimmedGoal.isBlank()) {
            return LocalAgentController.CommandResult(
                ok = false,
                userMessage = "No agent goal was provided.",
                error = "missing_goal",
            )
        }
        if (!isEnabled(context)) {
            setEnabled(context, true)
        }
        return LocalAgentController.start(context, trimmedGoal)
    }

    fun stop(context: Context) {
        setEnabled(context, false)
    }

    fun syncNativePluginState(context: Context) {
        CommunityPluginPrefs.setNativePluginEnabled(
            context,
            NativePluginIds.LOCAL_AGENT,
            isEnabled(context),
        )
    }

    fun setPlanningProvider(context: Context, type: AgentProviderType) {
        AutomationPrefs.setProviderType(context, type)
        AiProviderPrefs.setProvider(
            context,
            when (type) {
                AgentProviderType.CLOUD_AI -> AiProviderType.CLI_RELAY
                AgentProviderType.LOCAL_AGENT -> AiProviderType.LOCAL_MODELS
            },
        )
    }
}

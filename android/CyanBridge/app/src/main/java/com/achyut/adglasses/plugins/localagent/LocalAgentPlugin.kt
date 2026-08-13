package com.achyut.adglasses.plugins.localagent

import android.content.Context
import com.achyut.adglasses.agent.LocalAgentPrefs as AutomationPrefs
import com.achyut.adglasses.ai.router.AiProviderPrefs
import com.achyut.adglasses.ai.router.AiProviderType
import com.achyut.adglasses.localagent.LocalAgentController
import com.achyut.adglasses.localagent.LocalAgentNotificationSpeaker
import com.achyut.adglasses.localagent.LocalAgentPrefs as RuntimePrefs
import com.achyut.adglasses.localagent.LocalAgentTelegramService
import com.achyut.adglasses.shared.plugins.NativePluginIds
import com.achyut.adglasses.shared.settings.AgentProviderType
import com.achyut.adglasses.ui.CommunityPluginPrefs

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
                AgentProviderType.CLOUD_API -> AiProviderType.CLOUD_API
                AgentProviderType.LOCAL_AGENT -> AiProviderType.LOCAL_MODELS
                AgentProviderType.TASKER -> AiProviderType.MOCK
            },
        )
    }
}

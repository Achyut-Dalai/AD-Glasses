package com.fersaiyan.cyanbridge.ai.orchestrator

import android.content.Context
import com.fersaiyan.cyanbridge.media.autocapture.AutoAudioCapturePrefs
import com.fersaiyan.cyanbridge.media.autocapture.AutoAudioCaptureService
import com.fersaiyan.cyanbridge.plugins.PluginVoicePermissions
import com.fersaiyan.cyanbridge.plugins.autodiary.AutoDiaryService
import com.fersaiyan.cyanbridge.plugins.errandbrain.ErrandBrainPreferences
import com.fersaiyan.cyanbridge.plugins.errandbrain.ErrandBrainService
import com.fersaiyan.cyanbridge.plugins.handsfreetranslator.HandsFreeTranslatorPreferences
import com.fersaiyan.cyanbridge.plugins.handsfreetranslator.HandsFreeTranslatorService
import com.fersaiyan.cyanbridge.plugins.livecaptionrelay.LiveCaptionRelayPreferences
import com.fersaiyan.cyanbridge.plugins.livecaptionrelay.LiveCaptionRelayService
import com.fersaiyan.cyanbridge.plugins.localagent.LocalAgentPlugin
import com.fersaiyan.cyanbridge.plugins.meetingsparknotes.MeetingSparkNotesPreferences
import com.fersaiyan.cyanbridge.plugins.meetingsparknotes.MeetingSparkNotesService
import com.fersaiyan.cyanbridge.plugins.visualdiary.VisualDiaryPreferences
import com.fersaiyan.cyanbridge.plugins.visualdiary.VisualDiaryService

/** Native start/stop bridge for capabilities that AD can control without UI automation. */
class AndroidCapabilityCommandExecutor(
    private val context: Context,
) {
    /** Read persisted runtime state instead of deriving UI state from a selected shortcut. */
    fun isActive(capability: AssistantCapability): Boolean = when (capability) {
        AssistantCapability.TRANSLATOR -> HandsFreeTranslatorPreferences.isEnabled(context)
        AssistantCapability.MEETING_NOTES -> MeetingSparkNotesPreferences.isEnabled(context)
        AssistantCapability.LIVE_CAPTIONS -> LiveCaptionRelayPreferences.isEnabled(context)
        AssistantCapability.ERRAND_BRAIN -> ErrandBrainPreferences.isEnabled(context)
        AssistantCapability.AUTO_AUDIO -> AutoAudioCapturePrefs.isEnabled(context)
        AssistantCapability.AUTO_DIARY -> AutoDiaryService.isEnabled(context)
        AssistantCapability.VISUAL_DIARY -> VisualDiaryPreferences.isEnabled(context)
        AssistantCapability.LOCAL_AGENT -> LocalAgentPlugin.isEnabled(context)
    }

    fun activeCapabilities(): Set<AssistantCapability> =
        AssistantCapability.entries.filterTo(linkedSetOf(), ::isActive)

    fun isExclusiveVoiceCapability(capability: AssistantCapability): Boolean =
        capability in EXCLUSIVE_VOICE_CAPABILITIES

    fun activeVoiceCapability(excluding: AssistantCapability? = null): AssistantCapability? =
        EXCLUSIVE_VOICE_CAPABILITIES.firstOrNull { it != excluding && isActive(it) }

    fun displayName(capability: AssistantCapability): String = when (capability) {
        AssistantCapability.TRANSLATOR -> "Translate"
        AssistantCapability.MEETING_NOTES -> "Soundbites"
        AssistantCapability.LIVE_CAPTIONS -> "Live Captions"
        AssistantCapability.ERRAND_BRAIN -> "Cron"
        AssistantCapability.AUTO_DIARY -> "DayNote"
        AssistantCapability.AUTO_AUDIO -> "Auto Capture"
        AssistantCapability.VISUAL_DIARY -> "Timeline"
        AssistantCapability.LOCAL_AGENT -> "Automation"
    }

    fun execute(command: AssistantCapabilityCommand): AssistantResult {
        return runCatching {
            when (command.capability) {
                AssistantCapability.TRANSLATOR -> toggleVoice(
                    command,
                    start = { HandsFreeTranslatorService.start(context) },
                    stop = { HandsFreeTranslatorService.stop(context) },
                    started = "Translate started.",
                    stopped = "Translate stopped.",
                )

                AssistantCapability.MEETING_NOTES -> toggleVoice(
                    command,
                    start = { MeetingSparkNotesService.start(context) },
                    stop = { MeetingSparkNotesService.stop(context) },
                    started = "Soundbites started.",
                    stopped = "Soundbites stopped. I’ll keep the captured notes on your phone.",
                )

                AssistantCapability.LIVE_CAPTIONS -> toggleVoice(
                    command,
                    start = { LiveCaptionRelayService.start(context) },
                    stop = { LiveCaptionRelayService.stop(context) },
                    started = "Live Captions started.",
                    stopped = "Live Captions stopped.",
                )

                AssistantCapability.ERRAND_BRAIN -> toggleVoice(
                    command,
                    start = { ErrandBrainService.start(context) },
                    stop = { ErrandBrainService.stop(context) },
                    started = "Cron listening started.",
                    stopped = "Cron listening stopped.",
                )

                AssistantCapability.AUTO_AUDIO -> toggle(
                    command,
                    start = { AutoAudioCaptureService.start(context) },
                    stop = { AutoAudioCaptureService.stop(context) },
                    started = "Auto Capture started.",
                    stopped = "Auto Capture stopped.",
                )

                AssistantCapability.AUTO_DIARY -> when (command.action) {
                    AssistantCapabilityAction.START -> {
                        val enabled = AutoDiaryService.enable(context)
                        AssistantResult(
                            spokenText = if (enabled) "DayNote started." else "DayNote needs permission setup on your phone.",
                        )
                    }
                    AssistantCapabilityAction.STOP -> {
                        AutoDiaryService.disable(context)
                        AssistantResult("DayNote stopped.")
                    }
                }

                AssistantCapability.VISUAL_DIARY -> when (command.action) {
                    AssistantCapabilityAction.START -> {
                        val enabled = VisualDiaryService.enable(context)
                        AssistantResult(
                            spokenText = if (enabled) "Timeline started." else "Timeline needs permission setup on your phone.",
                        )
                    }
                    AssistantCapabilityAction.STOP -> {
                        VisualDiaryService.disable(context)
                        AssistantResult("Timeline stopped.")
                    }
                }

                AssistantCapability.LOCAL_AGENT -> when (command.action) {
                    AssistantCapabilityAction.START -> {
                        LocalAgentPlugin.setEnabled(context, true)
                        AssistantResult("Automation is enabled. Tell me what you want done.")
                    }
                    AssistantCapabilityAction.STOP -> {
                        LocalAgentPlugin.setEnabled(context, false)
                        AssistantResult("Automation is disabled.")
                    }
                }
            }
        }.getOrElse { error ->
            AssistantResult(
                spokenText = "I couldn’t change that capability. Check its setup on your phone.",
                richText = "Capability command failed: ${error.message ?: error::class.java.simpleName}",
            )
        }
    }

    private fun toggleVoice(
        command: AssistantCapabilityCommand,
        start: () -> Unit,
        stop: () -> Unit,
        started: String,
        stopped: String,
    ): AssistantResult = when (command.action) {
        AssistantCapabilityAction.START -> {
            if (!PluginVoicePermissions.hasRequiredPermissions(context)) {
                return AssistantResult("${displayName(command.capability)} needs microphone and notification permission setup on your phone.")
            }

            val previous = activeVoiceCapability(excluding = command.capability)
            if (previous != null) stopVoiceCapability(previous)

            setVoiceCapabilityEnabled(command.capability, true)
            start()
            AssistantResult(
                if (previous == null) started
                else "$started ${displayName(previous)} was stopped because only one live-listening capability can use speech recognition at a time.",
            )
        }

        AssistantCapabilityAction.STOP -> {
            setVoiceCapabilityEnabled(command.capability, false)
            stop()
            AssistantResult(stopped)
        }
    }

    private fun stopVoiceCapability(capability: AssistantCapability) {
        setVoiceCapabilityEnabled(capability, false)
        when (capability) {
            AssistantCapability.TRANSLATOR -> HandsFreeTranslatorService.stop(context)
            AssistantCapability.MEETING_NOTES -> MeetingSparkNotesService.stop(context)
            AssistantCapability.LIVE_CAPTIONS -> LiveCaptionRelayService.stop(context)
            AssistantCapability.ERRAND_BRAIN -> ErrandBrainService.stop(context)
            else -> Unit
        }
    }

    private fun setVoiceCapabilityEnabled(capability: AssistantCapability, enabled: Boolean) {
        when (capability) {
            AssistantCapability.TRANSLATOR -> HandsFreeTranslatorPreferences.setEnabled(context, enabled)
            AssistantCapability.MEETING_NOTES -> MeetingSparkNotesPreferences.setEnabled(context, enabled)
            AssistantCapability.LIVE_CAPTIONS -> LiveCaptionRelayPreferences.setEnabled(context, enabled)
            AssistantCapability.ERRAND_BRAIN -> ErrandBrainPreferences.setEnabled(context, enabled)
            else -> Unit
        }
    }

    private inline fun toggle(
        command: AssistantCapabilityCommand,
        start: () -> Unit,
        stop: () -> Unit,
        started: String,
        stopped: String,
    ): AssistantResult = when (command.action) {
        AssistantCapabilityAction.START -> {
            start()
            AssistantResult(started)
        }
        AssistantCapabilityAction.STOP -> {
            stop()
            AssistantResult(stopped)
        }
    }

    private companion object {
        val EXCLUSIVE_VOICE_CAPABILITIES = listOf(
            AssistantCapability.TRANSLATOR,
            AssistantCapability.MEETING_NOTES,
            AssistantCapability.LIVE_CAPTIONS,
            AssistantCapability.ERRAND_BRAIN,
        )
    }
}

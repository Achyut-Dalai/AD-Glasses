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
class AndroidModeCommandExecutor(
    private val context: Context,
) {
    /** Read persisted runtime state instead of deriving UI state from a selected shortcut. */
    fun isActive(mode: AssistantMode): Boolean = when (mode) {
        AssistantMode.TRANSLATOR -> HandsFreeTranslatorPreferences.isEnabled(context)
        AssistantMode.MEETING_NOTES -> MeetingSparkNotesPreferences.isEnabled(context)
        AssistantMode.LIVE_CAPTIONS -> LiveCaptionRelayPreferences.isEnabled(context)
        AssistantMode.ERRAND_BRAIN -> ErrandBrainPreferences.isEnabled(context)
        AssistantMode.AUTO_AUDIO -> AutoAudioCapturePrefs.isEnabled(context)
        AssistantMode.AUTO_DIARY -> AutoDiaryService.isEnabled(context)
        AssistantMode.VISUAL_DIARY -> VisualDiaryPreferences.isEnabled(context)
        AssistantMode.LOCAL_AGENT -> LocalAgentPlugin.isEnabled(context)
    }

    fun activeModes(): Set<AssistantMode> = AssistantMode.entries.filterTo(linkedSetOf(), ::isActive)

    fun isExclusiveVoiceMode(mode: AssistantMode): Boolean = mode in EXCLUSIVE_VOICE_MODES

    fun activeVoiceMode(excluding: AssistantMode? = null): AssistantMode? =
        EXCLUSIVE_VOICE_MODES.firstOrNull { it != excluding && isActive(it) }

    fun displayName(mode: AssistantMode): String = when (mode) {
        AssistantMode.TRANSLATOR -> "Translate"
        AssistantMode.MEETING_NOTES -> "Soundbites"
        AssistantMode.LIVE_CAPTIONS -> "Live Captions"
        AssistantMode.ERRAND_BRAIN -> "Cron"
        AssistantMode.AUTO_DIARY -> "DayNote"
        AssistantMode.AUTO_AUDIO -> "Auto Capture"
        AssistantMode.VISUAL_DIARY -> "Timeline"
        AssistantMode.LOCAL_AGENT -> "Automation"
    }

    fun execute(command: AssistantModeCommand): AssistantResult {
        return runCatching {
            when (command.mode) {
                AssistantMode.TRANSLATOR -> toggleVoice(
                    command,
                    start = { HandsFreeTranslatorService.start(context) },
                    stop = { HandsFreeTranslatorService.stop(context) },
                    started = "Translate started.",
                    stopped = "Translate stopped.",
                )

                AssistantMode.MEETING_NOTES -> toggleVoice(
                    command,
                    start = { MeetingSparkNotesService.start(context) },
                    stop = { MeetingSparkNotesService.stop(context) },
                    started = "Soundbites started.",
                    stopped = "Soundbites stopped. I’ll keep the captured notes on your phone.",
                )

                AssistantMode.LIVE_CAPTIONS -> toggleVoice(
                    command,
                    start = { LiveCaptionRelayService.start(context) },
                    stop = { LiveCaptionRelayService.stop(context) },
                    started = "Live Captions started.",
                    stopped = "Live Captions stopped.",
                )

                AssistantMode.ERRAND_BRAIN -> toggleVoice(
                    command,
                    start = { ErrandBrainService.start(context) },
                    stop = { ErrandBrainService.stop(context) },
                    started = "Cron listening started.",
                    stopped = "Cron listening stopped.",
                )

                AssistantMode.AUTO_AUDIO -> toggle(
                    command,
                    start = { AutoAudioCaptureService.start(context) },
                    stop = { AutoAudioCaptureService.stop(context) },
                    started = "Auto Capture started.",
                    stopped = "Auto Capture stopped.",
                )

                AssistantMode.AUTO_DIARY -> when (command.action) {
                    AssistantModeAction.START -> {
                        val enabled = AutoDiaryService.enable(context)
                        AssistantResult(
                            spokenText = if (enabled) "DayNote started." else "DayNote needs permission setup on your phone.",
                        )
                    }
                    AssistantModeAction.STOP -> {
                        AutoDiaryService.disable(context)
                        AssistantResult("DayNote stopped.")
                    }
                }

                AssistantMode.VISUAL_DIARY -> when (command.action) {
                    AssistantModeAction.START -> {
                        val enabled = VisualDiaryService.enable(context)
                        AssistantResult(
                            spokenText = if (enabled) "Timeline started." else "Timeline needs permission setup on your phone.",
                        )
                    }
                    AssistantModeAction.STOP -> {
                        VisualDiaryService.disable(context)
                        AssistantResult("Timeline stopped.")
                    }
                }

                AssistantMode.LOCAL_AGENT -> when (command.action) {
                    AssistantModeAction.START -> {
                        LocalAgentPlugin.setEnabled(context, true)
                        AssistantResult("Automation is enabled. Tell me what you want done.")
                    }
                    AssistantModeAction.STOP -> {
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
        command: AssistantModeCommand,
        start: () -> Unit,
        stop: () -> Unit,
        started: String,
        stopped: String,
    ): AssistantResult = when (command.action) {
        AssistantModeAction.START -> {
            if (!PluginVoicePermissions.hasRequiredPermissions(context)) {
                return AssistantResult("${displayName(command.mode)} needs microphone and notification permission setup on your phone.")
            }

            val previous = activeVoiceMode(excluding = command.mode)
            if (previous != null) stopVoiceMode(previous)

            setVoiceModeEnabled(command.mode, true)
            start()
            AssistantResult(
                if (previous == null) started
                else "$started ${displayName(previous)} was stopped because only one live-listening capability can use speech recognition at a time.",
            )
        }

        AssistantModeAction.STOP -> {
            setVoiceModeEnabled(command.mode, false)
            stop()
            AssistantResult(stopped)
        }
    }

    private fun stopVoiceMode(mode: AssistantMode) {
        setVoiceModeEnabled(mode, false)
        when (mode) {
            AssistantMode.TRANSLATOR -> HandsFreeTranslatorService.stop(context)
            AssistantMode.MEETING_NOTES -> MeetingSparkNotesService.stop(context)
            AssistantMode.LIVE_CAPTIONS -> LiveCaptionRelayService.stop(context)
            AssistantMode.ERRAND_BRAIN -> ErrandBrainService.stop(context)
            else -> Unit
        }
    }

    private fun setVoiceModeEnabled(mode: AssistantMode, enabled: Boolean) {
        when (mode) {
            AssistantMode.TRANSLATOR -> HandsFreeTranslatorPreferences.setEnabled(context, enabled)
            AssistantMode.MEETING_NOTES -> MeetingSparkNotesPreferences.setEnabled(context, enabled)
            AssistantMode.LIVE_CAPTIONS -> LiveCaptionRelayPreferences.setEnabled(context, enabled)
            AssistantMode.ERRAND_BRAIN -> ErrandBrainPreferences.setEnabled(context, enabled)
            else -> Unit
        }
    }

    private inline fun toggle(
        command: AssistantModeCommand,
        start: () -> Unit,
        stop: () -> Unit,
        started: String,
        stopped: String,
    ): AssistantResult = when (command.action) {
        AssistantModeAction.START -> {
            start()
            AssistantResult(started)
        }
        AssistantModeAction.STOP -> {
            stop()
            AssistantResult(stopped)
        }
    }

    private companion object {
        val EXCLUSIVE_VOICE_MODES = listOf(
            AssistantMode.TRANSLATOR,
            AssistantMode.MEETING_NOTES,
            AssistantMode.LIVE_CAPTIONS,
            AssistantMode.ERRAND_BRAIN,
        )
    }
}

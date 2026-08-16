package com.fersaiyan.cyanbridge.ai.orchestrator

import android.content.Context
import com.fersaiyan.cyanbridge.media.autocapture.AutoAudioCaptureService
import com.fersaiyan.cyanbridge.plugins.autodiary.AutoDiaryService
import com.fersaiyan.cyanbridge.plugins.errandbrain.ErrandBrainService
import com.fersaiyan.cyanbridge.plugins.handsfreetranslator.HandsFreeTranslatorService
import com.fersaiyan.cyanbridge.plugins.livecaptionrelay.LiveCaptionRelayService
import com.fersaiyan.cyanbridge.plugins.localagent.LocalAgentPlugin
import com.fersaiyan.cyanbridge.plugins.meetingsparknotes.MeetingSparkNotesService
import com.fersaiyan.cyanbridge.plugins.visualdiary.VisualDiaryService

/** Native start/stop bridge for modes that AD can control without UI automation. */
class AndroidModeCommandExecutor(
    private val context: Context,
) {
    fun execute(command: AssistantModeCommand): AssistantResult {
        return runCatching {
            when (command.mode) {
                AssistantMode.TRANSLATOR -> toggle(
                    command,
                    start = { HandsFreeTranslatorService.start(context) },
                    stop = { HandsFreeTranslatorService.stop(context) },
                    started = "Translator started.",
                    stopped = "Translator stopped.",
                )

                AssistantMode.MEETING_NOTES -> toggle(
                    command,
                    start = { MeetingSparkNotesService.start(context) },
                    stop = { MeetingSparkNotesService.stop(context) },
                    started = "Meeting notes started.",
                    stopped = "Meeting notes stopped. I’ll keep the captured notes on your phone.",
                )

                AssistantMode.LIVE_CAPTIONS -> toggle(
                    command,
                    start = { LiveCaptionRelayService.start(context) },
                    stop = { LiveCaptionRelayService.stop(context) },
                    started = "Live captions started.",
                    stopped = "Live captions stopped.",
                )

                AssistantMode.ERRAND_BRAIN -> toggle(
                    command,
                    start = { ErrandBrainService.start(context) },
                    stop = { ErrandBrainService.stop(context) },
                    started = "Errand mode started.",
                    stopped = "Errand mode stopped.",
                )

                AssistantMode.AUTO_AUDIO -> toggle(
                    command,
                    start = { AutoAudioCaptureService.start(context) },
                    stop = { AutoAudioCaptureService.stop(context) },
                    started = "Auto audio started.",
                    stopped = "Auto audio stopped.",
                )

                AssistantMode.AUTO_DIARY -> when (command.action) {
                    AssistantModeAction.START -> {
                        val enabled = AutoDiaryService.enable(context)
                        AssistantResult(
                            spokenText = if (enabled) "Auto Diary started." else "Auto Diary needs permission setup on your phone.",
                        )
                    }
                    AssistantModeAction.STOP -> {
                        AutoDiaryService.disable(context)
                        AssistantResult("Auto Diary stopped.")
                    }
                }

                AssistantMode.VISUAL_DIARY -> when (command.action) {
                    AssistantModeAction.START -> {
                        val enabled = VisualDiaryService.enable(context)
                        AssistantResult(
                            spokenText = if (enabled) "Visual Diary started." else "Visual Diary needs permission setup on your phone.",
                        )
                    }
                    AssistantModeAction.STOP -> {
                        VisualDiaryService.disable(context)
                        AssistantResult("Visual Diary stopped.")
                    }
                }

                AssistantMode.LOCAL_AGENT -> when (command.action) {
                    AssistantModeAction.START -> {
                        LocalAgentPlugin.setEnabled(context, true)
                        AssistantResult("Phone actions are enabled. Tell me what you want done.")
                    }
                    AssistantModeAction.STOP -> {
                        LocalAgentPlugin.setEnabled(context, false)
                        AssistantResult("Phone actions are disabled.")
                    }
                }
            }
        }.getOrElse { error ->
            AssistantResult(
                spokenText = "I couldn’t change that mode. Check its setup on your phone.",
                richText = "Mode command failed: ${error.message ?: error::class.java.simpleName}",
            )
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
}

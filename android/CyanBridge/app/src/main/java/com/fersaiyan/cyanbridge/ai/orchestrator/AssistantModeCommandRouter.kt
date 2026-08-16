package com.fersaiyan.cyanbridge.ai.orchestrator

enum class AssistantMode {
    TRANSLATOR,
    MEETING_NOTES,
    LIVE_CAPTIONS,
    ERRAND_BRAIN,
    AUTO_DIARY,
    AUTO_AUDIO,
    VISUAL_DIARY,
    LOCAL_AGENT,
}

enum class AssistantModeAction {
    START,
    STOP,
}

data class AssistantModeCommand(
    val mode: AssistantMode,
    val action: AssistantModeAction,
)

/**
 * Fast-path for commands that should never need an LLM round-trip.
 * Configuration still belongs on the phone; this only starts/stops an already configured mode.
 */
object AssistantModeCommandRouter {
    fun parse(text: String): AssistantModeCommand? {
        val normalized = text.trim().lowercase()
        if (normalized.isBlank()) return null

        val action = when {
            START.containsMatchIn(normalized) -> AssistantModeAction.START
            STOP.containsMatchIn(normalized) -> AssistantModeAction.STOP
            else -> return null
        }

        val mode = when {
            TRANSLATOR.containsMatchIn(normalized) -> AssistantMode.TRANSLATOR
            MEETING.containsMatchIn(normalized) -> AssistantMode.MEETING_NOTES
            CAPTIONS.containsMatchIn(normalized) -> AssistantMode.LIVE_CAPTIONS
            ERRANDS.containsMatchIn(normalized) -> AssistantMode.ERRAND_BRAIN
            AUTO_DIARY.containsMatchIn(normalized) -> AssistantMode.AUTO_DIARY
            AUTO_AUDIO.containsMatchIn(normalized) -> AssistantMode.AUTO_AUDIO
            VISUAL_DIARY.containsMatchIn(normalized) -> AssistantMode.VISUAL_DIARY
            LOCAL_AGENT.containsMatchIn(normalized) -> AssistantMode.LOCAL_AGENT
            else -> return null
        }

        return AssistantModeCommand(mode = mode, action = action)
    }

    private val START = Regex("\\b(start|begin|enable|turn on|resume)\\b")
    private val STOP = Regex("\\b(stop|end|disable|turn off|pause)\\b")

    private val TRANSLATOR = Regex("\\b(translat(?:e|or|ion)|interpreter)\\b")
    private val MEETING = Regex("\\b(meeting notes?|meeting mode|spark notes?|take notes?)\\b")
    private val CAPTIONS = Regex("\\b(live captions?|captions?|caption mode)\\b")
    private val ERRANDS = Regex("\\b(errand brain|errand mode|errands?)\\b")
    private val AUTO_DIARY = Regex("\\b(auto diary|automatic diary|diary mode)\\b")
    private val AUTO_AUDIO = Regex("\\b(auto audio|automatic audio|audio capture)\\b")
    private val VISUAL_DIARY = Regex("\\b(visual diary|visual memory|visual timeline)\\b")
    private val LOCAL_AGENT = Regex("\\b(local agent|phone agent|phone control)\\b")
}

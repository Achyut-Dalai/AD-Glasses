package com.fersaiyan.cyanbridge.ai.orchestrator

enum class AssistantCapability {
    TRANSLATOR,
    MEETING_NOTES,
    LIVE_CAPTIONS,
    ERRAND_BRAIN,
    AUTO_DIARY,
    VISUAL_DIARY,
    LOCAL_AGENT,
}

enum class AssistantCapabilityAction {
    START,
    STOP,
}

data class AssistantCapabilityCommand(
    val capability: AssistantCapability,
    val action: AssistantCapabilityAction,
)

/** Fast-path for starting/stopping product capabilities and phone control without an LLM round-trip. */
object AssistantCapabilityCommandRouter {
    fun parse(text: String): AssistantCapabilityCommand? {
        val normalized = text.trim().lowercase()
        if (normalized.isBlank()) return null

        val action = when {
            START.containsMatchIn(normalized) -> AssistantCapabilityAction.START
            STOP.containsMatchIn(normalized) -> AssistantCapabilityAction.STOP
            else -> return null
        }

        val capability = when {
            TRANSLATOR.containsMatchIn(normalized) -> AssistantCapability.TRANSLATOR
            MEETING.containsMatchIn(normalized) -> AssistantCapability.MEETING_NOTES
            ERRANDS.containsMatchIn(normalized) -> AssistantCapability.ERRAND_BRAIN
            AUTO_DIARY.containsMatchIn(normalized) -> AssistantCapability.AUTO_DIARY
            VISUAL_DIARY.containsMatchIn(normalized) -> AssistantCapability.VISUAL_DIARY
            LOCAL_AGENT.containsMatchIn(normalized) -> AssistantCapability.LOCAL_AGENT
            else -> return null
        }

        return AssistantCapabilityCommand(capability = capability, action = action)
    }

    private val START = Regex("\\b(start|begin|enable|turn on|resume)\\b")
    private val STOP = Regex("\\b(stop|end|disable|turn off|pause)\\b")

    private val TRANSLATOR = Regex("\\b(translat(?:e|or|ion)|interpreter)\\b")
    // Older spoken names remain accepted as input compatibility, but are not product/UI labels.
    private val MEETING = Regex("\\b(soundbites?|meeting notes?|meeting mode|spark notes?|take notes?)\\b")
    private val ERRANDS = Regex("\\b(cron|errand brain|errand mode|errands?)\\b")
    private val AUTO_DIARY = Regex("\\b(daynote|auto diary|automatic diary|diary mode)\\b")
    private val VISUAL_DIARY = Regex("\\b(timeline|visual diary|visual memory|visual timeline)\\b")
    private val LOCAL_AGENT = Regex("\\b(automation|local agent|phone agent|phone control)\\b")
}

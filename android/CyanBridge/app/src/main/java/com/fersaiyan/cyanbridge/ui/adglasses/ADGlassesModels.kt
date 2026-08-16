package com.fersaiyan.cyanbridge.ui.adglasses

enum class ADTab(val label: String) {
    HOME("Home"),
    ASSISTANT("Assistant"),
    LIBRARY("Library"),
    AUTOMATIONS("Automations"),
}

enum class ADRoute {
    MAIN,
    DEVICE_CENTER,
    SYNC,
    SETTINGS,
    AI_SERVICES,
    PRIVACY,
    ADVANCED,
    FIRMWARE,
    AUTOMATION_DETAIL,
}

enum class ADAutomation(
    val title: String,
    val summary: String,
    val outcome: String,
    val boundary: String,
) {
    LOCAL_AGENT(
        "Local Agent",
        "Phone actions with approval.",
        "Phone actions",
        "On device",
    ),
    MEETING_NOTES(
        "Meeting Spark Notes",
        "Record, transcribe, and summarize meetings.",
        "Meetings",
        "Automatic",
    ),
    LIVE_CAPTIONS(
        "Live Caption Relay",
        "Live speech into readable captions.",
        "Accessibility",
        "On device",
    ),
    TRANSLATOR(
        "Hands-Free Translator",
        "Conversation translation with spoken output.",
        "Language",
        "Automatic",
    ),
    ERRAND_BRAIN(
        "Errand Brain",
        "Spoken errands into tasks and reminders.",
        "Planning",
        "Your cloud",
    ),
    AUTO_DIARY(
        "Auto Diary",
        "Private daily context summary.",
        "Memory",
        "On device",
    ),
    AUTO_AUDIO(
        "Auto Audio",
        "Scheduled capture, sync, and transcription.",
        "Capture",
        "Automatic",
    ),
    VISUAL_DIARY(
        "Visual Diary",
        "Captures into a searchable visual timeline.",
        "Vision",
        "Automatic",
    ),
}

data class ADHostActions(
    val onScan: () -> Unit,
    val onReconnect: () -> Unit,
    val onDisconnect: () -> Unit,
    val onStartSync: () -> Unit,
    val onStopSync: () -> Unit,
    val onCapturePhoto: () -> Unit,
    val onToggleVideo: () -> Unit,
    val onStartRecording: () -> Unit,
    val onStopRecording: () -> Unit,
    val onVoiceQuestion: () -> Unit,
    val onImageQuestion: () -> Unit,
    val onOpenChat: () -> Unit,
    val onOpenChatWithPrompt: (String) -> Unit,
    val onOpenPhotos: () -> Unit,
    val onOpenMedia: () -> Unit,
    val onOpenNotes: () -> Unit,
    val onOpenLegacySettings: () -> Unit,
    val onOpenDeviceSetup: () -> Unit,
    val onChooseFirmwareFiles: () -> Unit,
    val onCancelFirmware: () -> Unit,
    val onOpenAutomationSettings: (ADAutomation) -> Unit,
)

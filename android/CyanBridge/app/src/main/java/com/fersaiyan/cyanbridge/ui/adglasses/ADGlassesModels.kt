package com.fersaiyan.cyanbridge.ui.adglasses

enum class ADTab(val label: String) {
    HOME("Home"),
    CHATS("Chats"),
    LIBRARY("Library"),
    TASKS("Tasks"),
    AI("AI"),
}

enum class ADRoute {
    MAIN,
    DEVICE_CENTER,
    SYNC,
    SETTINGS,
    AI_SERVICES,
    ROUTING,
    PRIVACY,
    STORAGE,
    LANGUAGE,
    PERMISSIONS,
    ADVANCED,
    ABOUT,
    FIRMWARE,
    TASK_DETAIL,
    LIBRARY_CAPTURES,
    LIBRARY_RECORDINGS,
    LIBRARY_NOTES,
}

enum class ADAutomation(
    val title: String,
    val summary: String,
    val outcome: String,
    val boundary: String,
    /** Existing service/plugin title used only to reconcile runtime state. */
    val runtimeTitle: String = title,
    /** Product task list visibility. Runtime compatibility entries may remain hidden. */
    val visibleInTasks: Boolean = true,
) {
    LOCAL_AGENT(
        "Phone Control",
        "Open apps, navigate and complete supported phone actions from the glasses.",
        "Phone actions",
        "On device",
        "Local Agent",
        false,
    ),
    MEETING_NOTES(
        "Meeting Notes",
        "Record, transcribe and summarize a meeting.",
        "Meeting notes",
        "Automatic",
        "Meeting Spark Notes",
    ),
    LIVE_CAPTIONS(
        "Live Captions",
        "Turn nearby speech into readable live captions.",
        "Accessibility",
        "On device",
        "Live Caption Relay",
        false,
    ),
    TRANSLATOR(
        "Live Translator",
        "Translate a conversation and hear the result through the glasses.",
        "Translation",
        "Automatic",
        "Hands-Free Translator",
    ),
    ERRAND_BRAIN(
        "Errands",
        "Turn spoken errands into tasks and reminders.",
        "Planning",
        "Configured AI",
        "Errand Brain",
    ),
    AUTO_DIARY(
        "Daily Diary",
        "Create a private daily context summary.",
        "Memory",
        "On device",
        "Auto Diary",
    ),
    AUTO_AUDIO(
        "Auto Capture",
        "Capture, sync and transcribe audio on a schedule.",
        "Capture",
        "Automatic",
        "Auto Audio",
        false,
    ),
    VISUAL_DIARY(
        "Visual Diary",
        "Turn captures into a searchable visual timeline.",
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

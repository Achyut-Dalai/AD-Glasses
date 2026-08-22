package com.ad_glasses.ui.adglasses

enum class ADTab(val label: String) {
    HOME("Home"),
    CHATS("Chats"),
    AI("AI"),
    LIBRARY("Library"),
}

enum class ADRoute {
    MAIN,
    DEVICE_CENTER,
    SYNC,
    SETTINGS,
    AI_CLOUD,
    AI_LOCAL,
    PRIVACY,
    STORAGE,
    LANGUAGE,
    PERMISSIONS,
    ABOUT,
    FIRMWARE,
    LIBRARY_CAPTURES,
    LIBRARY_RECORDINGS,
    LIBRARY_NOTES,
}

enum class ADAutomation(
    val title: String,
    val summary: String,
    val outcome: String,
    val boundary: String,
    val runtimeTitle: String = title,
    val visibleInAi: Boolean = true,
) {
    /** Legacy runtime token only; phone UI automation is no longer exposed as an AI invocation route. */
    LOCAL_AGENT(
        "Automation",
        "Retired from AI invocation.",
        "Android actions",
        "Not exposed",
        "Local Agent",
        false,
    ),
    MEETING_NOTES(
        "Soundbites",
        "Capture spoken moments and turn them into concise notes you can revisit later.",
        "Audio notes",
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
        "Translate",
        "Live translation for conversations through your glasses.",
        "Translation",
        "Automatic",
        "Hands-Free Translator",
    ),
    @Deprecated("Cron is removed from the AD Glasses product")
    ERRAND_BRAIN("Removed Cron", "Removed", "Removed", "Removed", "Errand Brain", false),
    @Deprecated("Background audio auto-capture is removed from the AD Glasses product")
    AUTO_AUDIO("Removed audio capture", "Removed", "Removed", "Removed", "Auto Audio", false),
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
    val onAnalyzeMedia: (String) -> Unit,
    val onOpenPhotos: () -> Unit,
    val onOpenMedia: () -> Unit,
    val onOpenNotes: () -> Unit,
    val onOpenLegacySettings: () -> Unit,
    val onOpenDeviceSetup: () -> Unit,
    val onChooseFirmwareFiles: () -> Unit,
    val onCancelFirmware: () -> Unit,
    val onOpenAutomationSettings: (ADAutomation) -> Unit,
)

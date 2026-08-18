package com.fersaiyan.cyanbridge.ui.adglasses

enum class ADTab(val label: String) {
    HOME("Home"),
    CHATS("Prompt"),
    AI("AI"),
    LIBRARY("Library"),
}

enum class ADRoute {
    MAIN,
    DEVICE_CENTER,
    SYNC,
    SETTINGS,
    AI_RELAY,
    AI_LOCAL,
    AI_ASSISTANT_APPS,
    PRIVACY,
    STORAGE,
    LANGUAGE,
    PERMISSIONS,
    ADVANCED,
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
    /** Existing service/plugin title used only to reconcile runtime state. */
    val runtimeTitle: String = title,
    /** Product capability visibility on the primary AI surface. */
    val visibleInAi: Boolean = true,
) {
    LOCAL_AGENT(
        "Automation",
        "Open apps, navigate and complete supported Android actions from the glasses.",
        "Android actions",
        "On device",
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
    /**
     * Compatibility token for the inherited MainActivity settings dispatcher only.
     * Cron is retired from the product and cannot be enabled through AD Glasses UI or assistant commands.
     */
    @Deprecated("Cron is removed from the AD Glasses product")
    ERRAND_BRAIN(
        "Removed Cron",
        "Removed",
        "Removed",
        "Removed",
        "Errand Brain",
        false,
    ),
    AUTO_DIARY(
        "DayNote",
        "Distill the moments that matter into a private note for each day.",
        "Daily memory",
        "On device",
        "Auto Diary",
    ),
    /**
     * Not a product capability. Kept temporarily so the inherited MainActivity host can compile
     * until its old audio-capture switch is removed; it is never shown on the AD Glasses AI UI.
     */
    @Deprecated("Background audio auto-capture is removed from the AD Glasses product")
    AUTO_AUDIO(
        "Removed audio capture",
        "Removed",
        "Removed",
        "Removed",
        "Auto Audio",
        false,
    ),
    VISUAL_DIARY(
        "Timeline",
        "Turn visual captures into a searchable timeline you can revisit by moment.",
        "Visual memory",
        "Automatic",
        "Visual Diary",
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

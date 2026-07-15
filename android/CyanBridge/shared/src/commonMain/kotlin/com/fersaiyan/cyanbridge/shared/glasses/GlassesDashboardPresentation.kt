package com.fersaiyan.cyanbridge.shared.glasses

import com.fersaiyan.cyanbridge.shared.navigation.AppDestination

/**
 * Platform-neutral presentation state for the glasses dashboard. Android BLE,
 * Wi-Fi Direct, media, and service work stay behind the screen's callbacks.
 */
data class GlassesDashboardUiState(
    val connectionLabel: String = "Disconnected",
    val deviceClassLabel: String = "Unknown",
    val batteryPercent: Int? = null,
    val showBattery: Boolean = false,
    val storageLabel: String = "--",
    val showStorage: Boolean = false,
    val transfer: GlassesTransferUiState = GlassesTransferUiState(),
    val meeting: GlassesMeetingUiState = GlassesMeetingUiState(),
    val assistantMode: GlassesAssistantMode = GlassesAssistantMode.GEMINI,
    val imageQueryEnabled: Boolean = true,
    val imageQueryLabel: String = "Test image AI description",
    val showHeyCyanControls: Boolean = false,
    val showMetaRaybanControls: Boolean = false,
    val advancedExpanded: Boolean = false,
    val agentStatus: String = "Unknown",
    val agentLastError: String = "(none)",
    val metaRayban: MetaRaybanUiState = MetaRaybanUiState(),
    val ota: OtaSectionUiState = OtaSectionUiState(),
)

data class GlassesTransferUiState(
    val isVisible: Boolean = false,
    val flowLabel: String = "--",
    val countsLabel: String = "Photos: --  Videos: --  Audio: --",
    val detail: String = "Idle",
    /** Null represents indeterminate progress. */
    val progress: Float? = null,
)

/**
 * Presentation-only choices for a glasses media sync. Platform adapters retain
 * the BLE, Wi-Fi Direct, and HTTP implementation for each selection.
 */
enum class GlassesSyncFlow(
    val label: String,
    val description: String,
) {
    OFFICIAL_HEYCYAN(
        label = "HeyCyan app flow",
        description = "Vendor-like strict BLE + P2P sync",
    ),
    CUSTOM(
        label = "Custom flow",
        description = "CyanBridge resolver with fallback scanning",
    ),
}

data class GlassesMeetingUiState(
    val isRecording: Boolean = false,
    val sourceLabel: String = "(not recording)",
    val timerIndex: Int = 0,
    val bannerLabel: String = "",
)

enum class GlassesAssistantMode {
    GEMINI,
    CHAT_GPT,
    CHOSEN_PROVIDER,
}

data class MetaRaybanUiState(
    val registrationLabel: String = "Not registered",
    val sessionLabel: String = "Idle",
    val streamLabel: String = "Stopped",
    val displayActive: Boolean = false,
    val canRegister: Boolean = true,
    val canUnregister: Boolean = false,
    val canStartSession: Boolean = true,
    val canStopSession: Boolean = false,
    val canStartStream: Boolean = true,
    val canStopStream: Boolean = false,
    val canCapturePhoto: Boolean = false,
    val hasCapturedPhoto: Boolean = false,
)

data class OtaSectionUiState(
    val stateLabel: String = "Idle",
    val detail: String = "",
    val progress: Int? = null,
    val canStart: Boolean = true,
    val canCancel: Boolean = false,
)

/** User intents emitted by the portable dashboard presentation. */
sealed interface GlassesDashboardAction {
    data class Navigate(val destination: AppDestination) : GlassesDashboardAction
    data object Scan : GlassesDashboardAction
    data object Reconnect : GlassesDashboardAction
    data object Disconnect : GlassesDashboardAction
    data class SelectMeetingTimer(val index: Int) : GlassesDashboardAction
    data object StartMeetingCapture : GlassesDashboardAction
    data object StopMeetingCapture : GlassesDashboardAction
    data class SelectAssistantMode(val mode: GlassesAssistantMode) : GlassesDashboardAction
    data object TestVoiceQuestion : GlassesDashboardAction
    data object TestImageQuestion : GlassesDashboardAction
    data object CapturePhoto : GlassesDashboardAction
    data object ToggleVideo : GlassesDashboardAction
    data object StartAudioRecording : GlassesDashboardAction
    data object RequestMediaCount : GlassesDashboardAction
    data object StartSync : GlassesDashboardAction
    data object StopSync : GlassesDashboardAction
    data object ToggleAdvanced : GlassesDashboardAction
    data object StartAgent : GlassesDashboardAction
    data object StopAgent : GlassesDashboardAction
    data object RunAgentDemo : GlassesDashboardAction
    data object RequestBattery : GlassesDashboardAction
    data object RequestVersion : GlassesDashboardAction
    data object SyncTime : GlassesDashboardAction
    data object RequestVolume : GlassesDashboardAction
    data object AddDeviceListener : GlassesDashboardAction
    data object StartClassicBluetoothScan : GlassesDashboardAction
    data object DumpOtaInfo : GlassesDashboardAction
    data object TestPullOta : GlassesDashboardAction
    data object StartOta : GlassesDashboardAction
    data object CancelOta : GlassesDashboardAction
    data object MetaRegister : GlassesDashboardAction
    data object MetaUnregister : GlassesDashboardAction
    data object MetaStartSession : GlassesDashboardAction
    data object MetaStopSession : GlassesDashboardAction
    data object MetaStartStream : GlassesDashboardAction
    data object MetaStopStream : GlassesDashboardAction
    data object MetaCapturePhoto : GlassesDashboardAction
    data object MetaViewPhoto : GlassesDashboardAction
    data object MetaStartDisplay : GlassesDashboardAction
    data object MetaStopDisplay : GlassesDashboardAction
}

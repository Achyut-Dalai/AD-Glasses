package com.fersaiyan.cyanbridge.databinding

import android.view.LayoutInflater
import android.view.View

/**
 * Non-visual compatibility state for MainActivity's remaining Android/device handlers.
 *
 * AD Glasses is rendered entirely by Compose. These slots preserve callback identity and
 * transient controller state while the inherited MainActivity handlers are extracted into
 * dedicated runtime controllers. No XML is inflated and no hidden View hierarchy is created.
 */
class AcitivytMainBinding private constructor() {
    val meetingRecordingBanner = ControlSlot(visibility = View.GONE)
    val tvMeetingBanner = ControlSlot(text = "Recording active")
    val btnMeetingBannerStop = ControlSlot(text = "Stop")

    val statusText = ControlSlot(text = "Disconnected")
    val tvDeviceClass = ControlSlot(text = "Class: Unknown")
    val layoutStatusMetrics = ControlSlot()
    val layoutBattery = ControlSlot()
    val batteryText = ControlSlot(text = "--%")
    val layoutStorage = ControlSlot()
    val storageText = ControlSlot(text = "--")

    val cardTransferProgress = ControlSlot(visibility = View.GONE)
    val tvTransferFlow = ControlSlot(text = "Flow: --")
    val tvTransferCounts = ControlSlot(text = "Photos: --  Videos: --  Audio: --")
    val progressTransfer = ProgressSlot(isIndeterminate = true, max = 100)
    val tvTransferDetail = ControlSlot(text = "Idle")
    val btnTransferStop = ControlSlot(text = "Stop sync")

    val btnScan = ControlSlot(text = "Scan")
    val btnConnect = ControlSlot(text = "Reconnect")
    val btnDisconnect = ControlSlot(text = "Disconnect")

    val btnMeetingStart = ControlSlot(text = "Start")
    val btnMeetingStop = ControlSlot(text = "Stop")
    val spinnerMeetingTimer = SpinnerSlot()
    val tvMeetingSource = ControlSlot(text = "Source: (not recording)")

    val layoutMetaRayban = ControlSlot(visibility = View.GONE)
    val tvMetaRegistrationStatus = ControlSlot(text = "Registration: Not registered")
    val btnMetaRegister = ControlSlot(text = "Register")
    val btnMetaUnregister = ControlSlot(text = "Unregister")
    val tvMetaSessionState = ControlSlot(text = "Session: Idle")
    val btnMetaSessionStart = ControlSlot(text = "Start Session")
    val btnMetaSessionStop = ControlSlot(text = "Stop Session")
    val tvMetaStreamState = ControlSlot(text = "Stream: Stopped")
    val btnMetaStreamStart = ControlSlot(text = "Start Stream")
    val btnMetaStreamStop = ControlSlot(text = "Stop Stream")
    val btnMetaCapturePhoto = ControlSlot(text = "Capture Photo")
    val btnMetaViewPhoto = ControlSlot(text = "View Last Photo", isEnabled = false)
    val layoutMetaDisplay = ControlSlot()
    val tvMetaDisplayState = ControlSlot(text = "Display: Inactive")
    val btnMetaDisplayStart = ControlSlot(text = "Start Display")
    val btnMetaDisplayStop = ControlSlot(text = "Stop Display")

    val layoutHeycyanExtras = ControlSlot()
    val cbHijackEnabled = CheckSlot(text = "Enable Hijack", initialChecked = true)
    val cbImageAsAssistant = CheckSlot(text = "Direct Assistant", initialChecked = true)
    val btnModeGemini = ControlSlot(text = "Phone Assistant")
    val btnModeChatgpt = ControlSlot(text = "Phone Assistant", visibility = View.GONE)
    val btnModeInternal = ControlSlot(text = "AD Local / Cloud")
    val btnTestHijackVoice = ControlSlot(text = "Test AI Voice Question")
    val btnTestHijackImage = ControlSlot(text = "Test Image AI description")

    val btnCamera = ControlSlot(text = "Photo")
    val btnVideo = ControlSlot(text = "Video")
    val btnRecord = ControlSlot(text = "Audio")
    val btnMediaCount = ControlSlot(text = "Count")
    val btnDataDownload = ControlSlot(text = "Sync Data (P2P)")

    val btnToggleAdvanced = ControlSlot(text = "Advanced ▼")
    val layoutAdvancedContainer = ControlSlot(visibility = View.GONE)
    val tvAgentStatus = ControlSlot(text = "Status: Unknown")
    val tvAgentLastError = ControlSlot(text = "Last error: (none)")
    val btnAgentStart = ControlSlot(text = "Start")
    val btnAgentStop = ControlSlot(text = "Stop")
    val btnAgentDemo = ControlSlot(text = "Demo")

    val btnBattery = ControlSlot(text = "Battery")
    val btnVersion = ControlSlot(text = "Version")
    val btnSetTime = ControlSlot(text = "Sync Time")
    val btnVolume = ControlSlot(text = "Volume")
    val btnAddListener = ControlSlot()
    val btnBt = ControlSlot(isEnabled = false, alpha = 0.5f)
    val btnOtaInfo = ControlSlot(isEnabled = false, alpha = 0.5f)
    val btnPullOtaTest = ControlSlot(isEnabled = false, alpha = 0.5f)

    val bottomNavigation = ControlSlot(visibility = View.GONE)

    companion object {
        @Suppress("UNUSED_PARAMETER")
        fun inflate(layoutInflater: LayoutInflater): AcitivytMainBinding = AcitivytMainBinding()
    }
}

open class ControlSlot(
    var text: CharSequence = "",
    var visibility: Int = View.VISIBLE,
    var isEnabled: Boolean = true,
    var alpha: Float = 1f,
) {
    private var clickListener: ((ControlSlot) -> Unit)? = null
    var textColor: Int? = null
        private set

    fun setOnClickListener(listener: ((ControlSlot) -> Unit)?) {
        clickListener = listener
    }

    fun performClick(): Boolean {
        val listener = clickListener ?: return false
        listener(this)
        return true
    }

    fun setTextColor(color: Int) {
        textColor = color
    }
}

class CheckSlot(
    text: CharSequence = "",
    initialChecked: Boolean = false,
) : ControlSlot(text = text, visibility = View.GONE) {
    private var checkedChangeListener: ((CheckSlot, Boolean) -> Unit)? = null

    var isChecked: Boolean = initialChecked
        set(value) {
            if (field == value) return
            field = value
            checkedChangeListener?.invoke(this, value)
        }

    fun setOnCheckedChangeListener(listener: ((CheckSlot, Boolean) -> Unit)?) {
        checkedChangeListener = listener
    }
}

class SpinnerSlot : ControlSlot() {
    var adapter: Any? = null
    var selectedItemPosition: Int = 0
        private set

    fun setSelection(position: Int) {
        selectedItemPosition = position.coerceAtLeast(0)
    }
}

class ProgressSlot(
    var isIndeterminate: Boolean = true,
    var max: Int = 100,
    var progress: Int = 0,
) : ControlSlot()

package com.fersaiyan.cyanbridge.glasses.runtime

import android.content.Context
import com.fersaiyan.cyanbridge.audio.MeetingCapturePrefs
import com.fersaiyan.cyanbridge.audio.MeetingCaptureService
import com.fersaiyan.cyanbridge.devices.DeviceProfileStore
import com.fersaiyan.cyanbridge.devices.eyevue.EyevueManager
import com.fersaiyan.cyanbridge.shared.glasses.GlassesDashboardAction
import com.fersaiyan.cyanbridge.shared.glasses.GlassesDashboardUiState
import com.fersaiyan.cyanbridge.shared.glasses.GlassesMeetingUiState

/**
 * Activity-independent slice of the glasses runtime.
 *
 * Only commands that already have application-context/singleton ownership are handled here.
 * Returning false deliberately falls through to the temporary MainActivity adapter while the
 * remaining HeyCyan/OTA/sync transports are extracted. This keeps one source of truth per
 * protocol instead of creating a competing BLE stack.
 */
class ADPersistentGlassesRuntime(context: Context) : ADGlassesCommandGateway.Runtime {
    private val appContext = context.applicationContext
    private val eyevue by lazy { EyevueManager.getInstance(appContext) }

    override fun dispatch(action: GlassesDashboardAction): Boolean {
        return when (action) {
            GlassesDashboardAction.StartMeetingCapture -> {
                MeetingCaptureService.start(
                    context = appContext,
                    timerDurationSec = null,
                    deviceClass = DeviceProfileStore.selectedClass(appContext).name,
                )
                true
            }
            GlassesDashboardAction.StopMeetingCapture -> {
                MeetingCaptureService.stop(appContext)
                true
            }
            GlassesDashboardAction.CapturePhoto -> ifEyevueConnected { eyevue.takePhoto(highQuality = true) }
            GlassesDashboardAction.ToggleVideo -> ifEyevueConnected { eyevue.toggleVideo() }
            GlassesDashboardAction.StartAudioRecording -> ifEyevueConnected {
                if (!eyevue.state.value.isAudioRecording) eyevue.toggleAudio()
            }
            GlassesDashboardAction.RequestBattery -> ifEyevueConnected { eyevue.requestBattery() }
            GlassesDashboardAction.RequestMediaCount -> ifEyevueConnected { eyevue.requestMediaCount() }
            GlassesDashboardAction.SyncTime -> ifEyevueConnected { eyevue.syncTime() }
            GlassesDashboardAction.RequestVolume -> ifEyevueConnected { eyevue.requestVolume() }
            GlassesDashboardAction.Disconnect -> ifEyevueSelected { eyevue.disconnect() }
            GlassesDashboardAction.Reconnect -> ifEyevueSelected {
                val profile = DeviceProfileStore.loadLastSelected(appContext)
                if (profile != null && !eyevue.isConnected()) {
                    eyevue.connect(profile.macAddress, profile.advertisedName)
                }
            }
            is GlassesDashboardAction.SetWearingDetection -> ifEyevueConnected {
                eyevue.setWearingDetection(action.enabled)
            }
            is GlassesDashboardAction.SetVideoRecordingDuration -> ifEyevueConnected {
                eyevue.setRecordingDuration(action.seconds)
            }
            is GlassesDashboardAction.SetAudioRecordingDuration -> ifEyevueConnected {
                eyevue.setRecordingDuration(action.seconds)
            }
            else -> false
        }
    }

    override fun snapshot(): GlassesDashboardUiState? {
        if (!DeviceProfileStore.isEyevueSelected(appContext)) return null
        val state = eyevue.state.value
        val meeting = MeetingCapturePrefs.getState(appContext)
        return GlassesDashboardUiState(
            connectionLabel = state.connectionLabel,
            deviceClassLabel = "Eyevue",
            batteryPercent = state.batteryPercent,
            showBattery = state.batteryPercent != null,
            storageLabel = state.storageCount?.let { "$it items" } ?: "--",
            showStorage = state.storageCount != null,
            meeting = GlassesMeetingUiState(
                isRecording = meeting.isRecording,
                sourceLabel = meeting.source?.name?.replace('_', ' ')?.lowercase() ?: "(not recording)",
            ),
            showEyevueControls = true,
        )
    }

    private inline fun ifEyevueConnected(block: () -> Unit): Boolean {
        if (!DeviceProfileStore.isEyevueSelected(appContext) || !eyevue.isConnected()) return false
        block()
        return true
    }

    private inline fun ifEyevueSelected(block: () -> Unit): Boolean {
        if (!DeviceProfileStore.isEyevueSelected(appContext)) return false
        block()
        return true
    }
}

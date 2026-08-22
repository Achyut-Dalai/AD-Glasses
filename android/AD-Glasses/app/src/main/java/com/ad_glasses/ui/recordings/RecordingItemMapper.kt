package com.ad_glasses.ui.recordings

import com.ad_glasses.data.local.entity.CaptureSession
import com.ad_glasses.shared.recordings.RecordingItem

internal fun CaptureSession.toRecordingItem(): RecordingItem {
    val timestamp = java.text.DateFormat.getDateTimeInstance().format(java.util.Date(startedAt))
    val titlePrefix = if (captureSource == GLASSES_SYNC_CAPTURE_SOURCE) "Glasses audio" else "Meeting"
    val metadata = buildString {
        append("${durationSec}s")
        if (captureSource.isNotBlank()) append(" · $captureSource")
        if (deviceClass.isNotBlank()) append(" · $deviceClass")
    }
    return RecordingItem(
        id = id,
        title = "$titlePrefix · $timestamp",
        metadata = metadata,
        stopReason = stopReason,
        durationSec = durationSec,
        captureSource = captureSource,
        deviceClass = deviceClass,
        startedAt = startedAt,
    )
}

private const val GLASSES_SYNC_CAPTURE_SOURCE = "GLASSES_SYNC_P2P"

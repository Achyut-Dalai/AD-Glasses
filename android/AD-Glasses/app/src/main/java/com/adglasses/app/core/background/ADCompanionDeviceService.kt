package com.adglasses.app.core.background

import android.companion.AssociationInfo
import android.companion.CompanionDeviceService
import android.companion.DevicePresenceEvent
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * System-bound companion presence service. Android can bind this service when the user-associated
 * glasses enter BLE range even if the app process was not already alive. The actual connection is
 * still owned by AccessoryService + the verified HeyCyan BLE repository.
 */
class ADCompanionDeviceService : CompanionDeviceService() {
    @Suppress("DEPRECATION")
    override fun onDeviceAppeared(associationInfo: AssociationInfo) {
        startAccessoryConnectionService()
    }

    override fun onDevicePresenceEvent(event: DevicePresenceEvent) {
        when (event.event) {
            DevicePresenceEvent.EVENT_BLE_APPEARED,
            DevicePresenceEvent.EVENT_BT_CONNECTED -> startAccessoryConnectionService()
        }
    }

    private fun startAccessoryConnectionService() {
        runCatching {
            ContextCompat.startForegroundService(
                this,
                Intent(this, AccessoryService::class.java),
            )
        }
    }
}

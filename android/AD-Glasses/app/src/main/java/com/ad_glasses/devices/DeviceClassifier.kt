package com.ad_glasses.devices

import android.os.ParcelUuid
import com.ad_glasses.devices.eyevue.EyevueProtocol
import com.ad_glasses.shared.devices.DeviceClass
import java.util.UUID

/**
 * Device classification is deliberately conservative. Prefer vendor evidence
 * (service UUIDs) over names, then fall back to the advertised name when the
 * transport does not expose enough metadata.
 */
object DeviceClassifier {

    private val MYVU_ADVERTISED_SERVICE_UUID: UUID =
        UUID.fromString("00000bd3-0000-1000-8000-00805f9b34fb")
    private val MYVU_GATT_SERVICE_UUID: UUID =
        UUID.fromString("00000bd1-0000-1000-8000-00805f9b34fb")

    fun guessDeviceClass(
        advertisedName: String?,
        serviceUuids: List<ParcelUuid> = emptyList(),
    ): DeviceClass {
        val name = advertisedName?.trim().orEmpty()
        val lower = name.lowercase()

        // Eyevue may omit its local name while still advertising the vendor service.
        if (serviceUuids.any { it.uuid == EyevueProtocol.SERVICE_UUID }) {
            return DeviceClass.EYEVUE
        }

        // MYVU / Star Air may likewise be identifiable from the advertised or
        // GATT service even when Android does not expose a useful local name.
        if (serviceUuids.any {
                it.uuid == MYVU_ADVERTISED_SERVICE_UUID || it.uuid == MYVU_GATT_SERVICE_UUID
            }
        ) {
            return DeviceClass.MEIZU_MYVU
        }

        if (name.isEmpty()) return DeviceClass.UNKNOWN

        if (lower.contains("eyevue")) {
            return DeviceClass.EYEVUE
        }

        if (
            lower.contains("heycyan") ||
            lower.contains("cyan") ||
            name.startsWith("O_") ||
            name.startsWith("Q_")
        ) {
            return DeviceClass.HEY_CYAN
        }

        if (
            lower.contains("ray-ban") ||
            lower.contains("rayban") ||
            (lower.contains("ray") && lower.contains("ban")) ||
            lower.contains("ray-ban meta") ||
            lower.contains("meta ray")
        ) {
            return DeviceClass.META_RAYBAN
        }

        if (lower.contains("myvu") || lower.contains("star air") || lower.contains("starair")) {
            return DeviceClass.MEIZU_MYVU
        }

        if (
            lower.contains("airpods") ||
            lower.contains("headset") ||
            lower.contains("headphones") ||
            lower.contains("earbuds") ||
            lower.contains("buds")
        ) {
            return DeviceClass.GENERIC_AUDIO
        }

        return DeviceClass.UNKNOWN
    }
}

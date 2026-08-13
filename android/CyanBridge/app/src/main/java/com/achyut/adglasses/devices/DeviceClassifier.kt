package com.achyut.adglasses.devices

import android.os.ParcelUuid
import com.achyut.adglasses.devices.eyevue.EyevueProtocol
import com.achyut.adglasses.shared.devices.DeviceClass

/**
 * Heuristics for classifying smart glasses by advertised name or service UUID.
 */
object DeviceClassifier {

    fun guessDeviceClass(
        advertisedName: String?,
        serviceUuids: List<ParcelUuid> = emptyList()
    ): DeviceClass {
        val name = advertisedName?.trim().orEmpty()
        val lower = name.lowercase()

        // Eyevue devices may omit their local name while still advertising the
        // vendor service. Check the UUID before name-based fallbacks.
        if (serviceUuids.any { it.uuid == EyevueProtocol.SERVICE_UUID }) {
            return DeviceClass.EYEVUE
        }

        if (name.isEmpty()) return DeviceClass.UNKNOWN

        if (lower.contains("eyevue")) {
            return DeviceClass.EYEVUE
        }

        // HeyCyan-class heuristics.
        if (
            lower.contains("heycyan") ||
            lower.contains("cyan") ||
            name.startsWith("O_") ||
            name.startsWith("Q_") ||
            lower.contains("ad glasses")
        ) {
            return DeviceClass.HEY_CYAN
        }

        // Meta Ray-Ban heuristics.
        if (
            lower.contains("ray-ban") ||
            lower.contains("rayban") ||
            (lower.contains("ray") && lower.contains("ban")) ||
            lower.contains("ray-ban meta") ||
            lower.contains("meta ray")
        ) {
            return DeviceClass.META_RAYBAN
        }

        // Meizu MYVU / Star Air (XGA010C) advertises as MYVU on the BLE link.
        if (lower.contains("myvu") || lower.contains("star air") || lower.contains("starair")) {
            return DeviceClass.MEIZU_MYVU
        }

        // Generic audio heuristics (best-effort).
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

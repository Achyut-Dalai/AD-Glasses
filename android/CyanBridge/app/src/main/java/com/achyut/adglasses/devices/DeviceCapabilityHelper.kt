package com.achyut.adglasses.devices

import android.content.Context
import com.achyut.adglasses.shared.devices.DeviceClass

/**
 * Utility helper to query hardware capabilities for the currently selected device profile.
 */
object DeviceCapabilityHelper {

    fun selectedClass(context: Context): DeviceClass {
        return DeviceProfileStore.selectedClass(context)
    }

    fun hasCamera(context: Context): Boolean {
        val selected = selectedClass(context)
        return selected in setOf(DeviceClass.HEY_CYAN, DeviceClass.META_RAYBAN, DeviceClass.UNKNOWN)
    }

    fun hasOnboardStorage(context: Context): Boolean {
        val selected = selectedClass(context)
        return selected == DeviceClass.HEY_CYAN || selected == DeviceClass.UNKNOWN
    }

    fun unavailableCameraReason(context: Context): String? {
        return when (selectedClass(context)) {
            DeviceClass.MEIZU_MYVU -> "Selected device profile (Meizu MYVU) has no camera."
            DeviceClass.GENERIC_AUDIO -> "Selected device profile (Earbuds / Audio-only glasses) has no camera."
            else -> null
        }
    }
}

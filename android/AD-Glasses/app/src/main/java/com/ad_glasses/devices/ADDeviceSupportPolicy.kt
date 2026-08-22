package com.ad_glasses.devices

import com.ad_glasses.shared.devices.DeviceClass

/**
 * Product support boundary for AD Glasses.
 *
 * Upstream may understand many hardware families. AD Glasses keeps those classes
 * internally for compatibility, but only hardware we actually validate is allowed
 * through the normal pairing flow.
 */
object ADDeviceSupportPolicy {
    /** Hardware currently owned and expected to be the primary validated path. */
    val validated: Set<DeviceClass> = setOf(DeviceClass.HEY_CYAN)

    /** Reserved product family for a future dedicated integration. */
    val planned: Set<DeviceClass> = setOf(DeviceClass.META_RAYBAN)

    fun isValidated(deviceClass: DeviceClass): Boolean = deviceClass in validated

    fun isPlanned(deviceClass: DeviceClass): Boolean = deviceClass in planned

    /** Only validated hardware is pairable from the current AD Glasses setup flow. */
    fun isPairable(deviceClass: DeviceClass): Boolean = isValidated(deviceClass)

    /**
     * Generic BLE setup should contain only devices we can confidently identify and
     * support. Unknown devices are not promoted into a manual type-selection flow.
     * Meta will use its own dedicated pairing path when enabled.
     */
    fun shouldShowScanResult(deviceClass: DeviceClass): Boolean = isPairable(deviceClass)

    fun supportLabel(deviceClass: DeviceClass): String = when {
        isValidated(deviceClass) -> "Supported"
        isPlanned(deviceClass) -> "Planned"
        else -> "Internal compatibility only"
    }
}

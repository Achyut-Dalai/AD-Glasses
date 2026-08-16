package com.fersaiyan.cyanbridge.devices

import com.fersaiyan.cyanbridge.shared.devices.DeviceClass

/**
 * Product support boundary for AD Glasses.
 *
 * The upstream codebase knows about more hardware than this personal app intends
 * to expose. Keep those classes in the shared/device layers for compatibility and
 * future upstream ports, but only HeyCyan and Meta are valid AD Glasses product
 * choices.
 */
object ADDeviceSupportPolicy {
    /** Hardware currently owned and expected to be the primary validated path. */
    val validated: Set<DeviceClass> = setOf(DeviceClass.HEY_CYAN)

    /** Reserved product path we may validate later without reopening the taxonomy. */
    val planned: Set<DeviceClass> = setOf(DeviceClass.META_RAYBAN)

    /** Device classes AD Glasses is willing to expose or connect as product choices. */
    val selectable: List<DeviceClass> = listOf(
        DeviceClass.HEY_CYAN,
        DeviceClass.META_RAYBAN,
    )

    fun isValidated(deviceClass: DeviceClass): Boolean = deviceClass in validated

    fun isPlanned(deviceClass: DeviceClass): Boolean = deviceClass in planned

    fun isSelectable(deviceClass: DeviceClass): Boolean = deviceClass in selectable

    /**
     * Unknown scan results stay visible so an oddly-advertising HeyCyan can still be
     * manually identified. A positively recognized unsupported product stays hidden.
     */
    fun shouldShowScanResult(deviceClass: DeviceClass): Boolean =
        deviceClass == DeviceClass.UNKNOWN || isSelectable(deviceClass)

    fun defaultSelection(detectedClass: DeviceClass): DeviceClass =
        detectedClass.takeIf(::isSelectable) ?: DeviceClass.HEY_CYAN

    fun supportLabel(deviceClass: DeviceClass): String = when {
        isValidated(deviceClass) -> "Supported"
        isPlanned(deviceClass) -> "Planned"
        else -> "Not enabled in AD Glasses"
    }
}

package com.fersaiyan.cyanbridge.devices

import com.fersaiyan.cyanbridge.shared.devices.DeviceClass
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ADDeviceSupportPolicyTest {

    @Test
    fun heyCyanIsValidatedAndPairable() {
        assertTrue(ADDeviceSupportPolicy.isValidated(DeviceClass.HEY_CYAN))
        assertTrue(ADDeviceSupportPolicy.isPairable(DeviceClass.HEY_CYAN))
        assertTrue(ADDeviceSupportPolicy.shouldShowScanResult(DeviceClass.HEY_CYAN))
    }

    @Test
    fun metaIsPlannedButNotGenericPairable() {
        assertTrue(ADDeviceSupportPolicy.isPlanned(DeviceClass.META_RAYBAN))
        assertFalse(ADDeviceSupportPolicy.isPairable(DeviceClass.META_RAYBAN))
        assertFalse(ADDeviceSupportPolicy.shouldShowScanResult(DeviceClass.META_RAYBAN))
    }

    @Test
    fun unknownNeverAppearsAsPairableScanResult() {
        assertFalse(ADDeviceSupportPolicy.isPairable(DeviceClass.UNKNOWN))
        assertFalse(ADDeviceSupportPolicy.shouldShowScanResult(DeviceClass.UNKNOWN))
    }

    @Test
    fun upstreamCompatibilityDevicesStayOutOfProductPairing() {
        listOf(
            DeviceClass.EYEVUE,
            DeviceClass.MEIZU_MYVU,
            DeviceClass.GENERIC_AUDIO,
        ).forEach { deviceClass ->
            assertFalse(ADDeviceSupportPolicy.isPairable(deviceClass))
            assertFalse(ADDeviceSupportPolicy.shouldShowScanResult(deviceClass))
        }
    }
}

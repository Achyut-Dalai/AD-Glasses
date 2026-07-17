package com.fersaiyan.cyanbridge.ota

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OtaFirmwareTargetTest {

    @Test
    fun firmwareArtifactsAreNotSharedAcrossTargets() {
        assertTrue(OtaTarget.V821_WIFI.isExpectedFirmwareFilename("WIFIAM01G1.swu"))
        assertFalse(OtaTarget.V821_WIFI.isExpectedFirmwareFilename("JIELI.bin"))

        assertTrue(OtaTarget.JIELI_BLE.isExpectedFirmwareFilename("JIELI.bin"))
        assertFalse(OtaTarget.JIELI_BLE.isExpectedFirmwareFilename("WIFIAM01G1.swu"))
    }
}

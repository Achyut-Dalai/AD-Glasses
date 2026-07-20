package com.fersaiyan.cyanbridge.ota

import com.fersaiyan.cyanbridge.shared.glasses.OtaFirmwareSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test
    fun `only approved server sources map to catalog channels`() {
        assertNull(OtaFirmwareSource.PERSONAL_FILE.serverChannel())
        assertEquals("stealth", OtaFirmwareSource.STEALTH_CATALOG.serverChannel())
        assertEquals("debug", OtaFirmwareSource.DEBUG_CATALOG.serverChannel())
    }

    @Test
    fun `server artifacts must declare the exact current base firmware`() {
        assertTrue(
            isExactFirmwareBaseMatch(
                "WIFIAM01G1_1.00.28_2603031800",
                "wifiam01g1_1.00.28_2603031800",
            ),
        )
        assertFalse(
            isExactFirmwareBaseMatch(
                "WIFIAM01G1_1.00.28_2603031800",
                "WIFIAM01G1_1.00.23_2510111600",
            ),
        )
    }

    @Test
    fun `server SHA-256 metadata must be a complete hex digest`() {
        assertTrue(isSha256Hex("a".repeat(64)))
        assertFalse(isSha256Hex("a".repeat(63)))
        assertFalse(isSha256Hex("z".repeat(64)))
    }
}

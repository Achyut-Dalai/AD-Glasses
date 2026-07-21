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
    fun combinedWorkflowRequiresBothTransportArtifacts() {
        assertTrue(isCombinedOtaFilenamePair("WIFIAM01G1.swu", "JIELI.bin"))
        assertFalse(isCombinedOtaFilenamePair("WIFIAM01G1.swu", "JIELI.swu"))
        assertFalse(isCombinedOtaFilenamePair("JIELI.bin", "WIFIAM01G1.swu"))
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

    @Test
    fun `only the explicit patch-unavailable response opens a patch request`() {
        assertTrue(
            isFirmwarePatchUnavailableResponse(
                FIRMWARE_PATCH_UNAVAILABLE_STATUS,
                FIRMWARE_PATCH_UNAVAILABLE_ERROR,
            ),
        )
        assertFalse(isFirmwarePatchUnavailableResponse(404, FIRMWARE_PATCH_UNAVAILABLE_ERROR))
        assertFalse(isFirmwarePatchUnavailableResponse(FIRMWARE_PATCH_UNAVAILABLE_STATUS, "server_error"))
    }

    @Test
    fun `patch requests require the complete relay response contract`() {
        assertEquals(
            "No exact patch is approved.",
            firmwarePatchUnavailableMessage(
                FIRMWARE_PATCH_UNAVAILABLE_STATUS,
                FIRMWARE_PATCH_UNAVAILABLE_ERROR,
                "No exact patch is approved.",
            ),
        )
        assertNull(
            firmwarePatchUnavailableMessage(
                404,
                FIRMWARE_PATCH_UNAVAILABLE_ERROR,
                "No exact patch is approved.",
            ),
        )
        assertNull(
            firmwarePatchUnavailableMessage(
                FIRMWARE_PATCH_UNAVAILABLE_STATUS,
                "firmware_not_available",
                "No exact patch is approved.",
            ),
        )
        assertEquals(
            "No approved firmware patch exists for this installed version.",
            firmwarePatchUnavailableMessage(
                FIRMWARE_PATCH_UNAVAILABLE_STATUS,
                FIRMWARE_PATCH_UNAVAILABLE_ERROR,
                "",
            ),
        )
    }
}

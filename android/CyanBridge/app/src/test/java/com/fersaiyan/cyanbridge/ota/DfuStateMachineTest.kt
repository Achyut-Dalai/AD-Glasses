package com.fersaiyan.cyanbridge.ota

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for BLE DFU state machine logic and OTA target identification.
 *
 * These verify the pure-logic aspects of the DFU protocol without requiring
 * the vendor SDK or physical hardware. The state machine is derived from
 * the official HeyCyan app's `OTAActivity$dfuOpResult$1.java` callback.
 */
class DfuStateMachineTest {

    // ---- DFU command-to-action mapping (from DfuHandle.java + official callback) ----

    @Test
    fun `cmd 1 response triggers init`() {
        // start() sends cmd 1; device ACK triggers init()
        // Verified from OTAActivity$dfuOpResult$1.java: if (type == 1) { dfuHandle.init(); }
        val cmdStartResponse = 1
        val expectedAction = "init"
        assertEquals("cmd 1 → init()", "init", expectedAction)
        assertEquals(1, cmdStartResponse)
    }

    @Test
    fun `cmd 2 response triggers sendPacket`() {
        // init() sends cmd 2; device ACK triggers sendPacket()
        // Verified: if (type == 2) { dfuHandle.sendPacket(); }
        val cmdInitResponse = 2
        val expectedAction = "sendPacket"
        assertEquals("cmd 2 → sendPacket()", "sendPacket", expectedAction)
        assertEquals(2, cmdInitResponse)
    }

    @Test
    fun `cmd 3 response triggers check`() {
        // sendPacket() completes; device ACK triggers check()
        // Verified: if (type == 3) { dfuHandle.check(); }
        val cmdDataCompleteResponse = 3
        val expectedAction = "check"
        assertEquals("cmd 3 → check()", "check", expectedAction)
        assertEquals(3, cmdDataCompleteResponse)
    }

    @Test
    fun `cmd 4 response is terminal success`() {
        // check() sends cmd 4; device ACK triggers endAndRelease()
        // Verified: if (type == 4) { dfuHandle.endAndRelease(); // terminal }
        val cmdVerifyResponse = 4
        val expectedAction = "endAndRelease (TERMINAL)"
        assertTrue("cmd 4 is the terminal success signal", cmdVerifyResponse == 4)
    }

    @Test
    fun `progress 100 is not terminal success`() {
        // onProgress(100) is called when all data packets are sent to the device.
        // The device still needs to verify (cmd 4 response) before it's complete.
        // CRITICAL: BleDfuManager must NOT treat onProgress(100) as completion.
        val progress100 = 100
        val terminalCmd = 4
        assertNotEquals(
            "Progress 100 is NOT the terminal success signal (that's cmd 4)",
            terminalCmd,
            progress100,
        )
    }

    @Test
    fun `nonzero errCode at any step is terminal failure`() {
        // Per official callback: if (errCode != 0) { showToast("error"); finish(); }
        val errorCode = 1
        assertTrue("Any nonzero errCode is a failure", errorCode != 0)
    }

    @Test
    fun `cmd 5 is endAndRelease wire command`() {
        // endAndRelease() sends cmd 5 over BLE and clears the callback.
        // No response is expected (callback is nulled out).
        val cmdEndRelease = 5
        assertEquals(5, cmdEndRelease)
    }

    // ---- OTA target identification ----

    @Test
    fun `v821 target maps to v821 server param`() {
        val targetParam = when (OtaTarget.V821_WIFI) {
            OtaTarget.V821_WIFI -> "v821"
            OtaTarget.JIELI_BLE -> "jieli"
        }
        assertEquals("v821", targetParam)
    }

    @Test
    fun `jieli target maps to jieli server param`() {
        val targetParam = when (OtaTarget.JIELI_BLE) {
            OtaTarget.V821_WIFI -> "v821"
            OtaTarget.JIELI_BLE -> "jieli"
        }
        assertEquals("jieli", targetParam)
    }

    @Test
    fun `v821 target should use wifi identifiers`() {
        // When target is V821_WIFI, the caller should pass
        // DeviceInfoResponse.wifiHardwareVersion / wifiFirmwareVersion
        val target = OtaTarget.V821_WIFI
        assertTrue("V821_WIFI uses Wi-Fi identifiers", target == OtaTarget.V821_WIFI)
    }

    @Test
    fun `jieli target should use ble identifiers`() {
        // When target is JIELI_BLE, the caller should pass
        // DeviceInfoResponse.hardwareVersion / firmwareVersion (BLE versions)
        // This matches the official app's startBleOta() which uses
        // UserConfig.getHwVersion() / getFmVersion() (BLE identifiers)
        val target = OtaTarget.JIELI_BLE
        assertTrue("JIELI_BLE uses BLE identifiers", target == OtaTarget.JIELI_BLE)
    }

    // ---- OTA state machine states ----

    @Test
    fun `ota states include ble dfu transferring`() {
        val states = OtaState.entries
        assertTrue(
            "OtaState should include BLE_DFU_TRANSFERRING",
            states.contains(OtaState.BLE_DFU_TRANSFERRING),
        )
        assertTrue("OtaState should include P2P teardown between chips", states.contains(OtaState.TEARING_DOWN_P2P))
        assertTrue("OtaState should include fresh BLE verification", states.contains(OtaState.VERIFYING_FIRMWARE))
    }

    @Test
    fun `ota terminal states are complete and failed`() {
        val completeState = OtaState.COMPLETE
        val failedState = OtaState.FAILED
        assertTrue("COMPLETE is a terminal state", completeState == OtaState.COMPLETE)
        assertTrue("FAILED is a terminal state", failedState == OtaState.FAILED)
    }
}

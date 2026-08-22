package com.ad_glasses.wifiadb

import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiAdbNetworkRulesTest {
    @Test
    fun runtimeStateDefaultsAreUnavailableAndInert() {
        val state = WifiAdbDebugRuntimeState()

        assertFalse(state.isAvailable)
        assertEquals("Idle", state.stateLabel)
        assertTrue(state.canStart)
        assertFalse(state.canStop)
        assertTrue(state.relayEndpoints.isEmpty())
    }

    @Test
    fun strictIpv4ParsingRejectsNamesShortFormsAndInvalidOctets() {
        assertNotNull(WifiAdbNetworkRules.parseIpv4("192.168.49.2"))
        assertNull(WifiAdbNetworkRules.parseIpv4("192.168.49"))
        assertNull(WifiAdbNetworkRules.parseIpv4("glasses.local"))
        assertNull(WifiAdbNetworkRules.parseIpv4("192.168.49.256"))
        assertNull(WifiAdbNetworkRules.parseIpv4("192.168.049.2x"))
    }

    @Test
    fun subnetMatchRequiresIpv4AndAnExact24Prefix() {
        assertTrue(
            WifiAdbNetworkRules.isSame24(
                InetAddress.getByName("192.168.49.1"),
                "192.168.49.87",
            ),
        )
        assertFalse(
            WifiAdbNetworkRules.isSame24(
                InetAddress.getByName("192.168.50.1"),
                "192.168.49.87",
            ),
        )
        assertFalse(
            WifiAdbNetworkRules.isSame24(InetAddress.getByName("::1"), "192.168.49.87"),
        )
    }

    @Test
    fun routeAndPeerIdentityRulesFailClosed() {
        assertTrue(WifiAdbNetworkRules.isP2pInterfaceName("p2p-wlan0-0"))
        assertTrue(WifiAdbNetworkRules.isP2pInterfaceName("wfd0"))
        assertFalse(WifiAdbNetworkRules.isP2pInterfaceName("wlan0"))
        assertFalse(WifiAdbNetworkRules.isP2pInterfaceName(null))

        assertFalse(WifiAdbNetworkRules.isExpectedPeer(null, null, null, null))
        assertFalse(WifiAdbNetworkRules.isExpectedPeer("unknown", "11:22", null, null))
        assertTrue(
            WifiAdbNetworkRules.isExpectedPeer(
                peerName = "QGlasses_A402",
                peerAddress = "11:22:33:44:55:66",
                pairedName = "Cyan",
                pairedAddress = "C4:E3:BF:C3:A4:02",
            ),
        )
        assertTrue(
            WifiAdbNetworkRules.isExpectedPeer(
                peerName = "unknown",
                peerAddress = "c4:e3:bf:c3:a4:02",
                pairedName = null,
                pairedAddress = "C4:E3:BF:C3:A4:02",
            ),
        )
    }

    @Test
    fun relayEndpointsExcludeLoopbackLinkLocalAndIpv6() {
        val endpoints = WifiAdbNetworkRules.relayEndpoints(
            listOf(
                InetAddress.getByName("127.0.0.1"),
                InetAddress.getByName("169.254.1.2"),
                InetAddress.getByName("10.0.0.3"),
                InetAddress.getByName("192.168.49.1"),
                InetAddress.getByName("::1"),
            ),
            15555,
        )

        assertEquals(listOf("10.0.0.3:15555", "192.168.49.1:15555"), endpoints)
    }
}

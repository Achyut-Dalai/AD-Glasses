package com.ad_glasses.shared.network

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.test.Test
import kotlin.test.assertFalse

class WifiP2pManagerContractTest {
    @Test
    fun wifiDirectSupportIsOptInForPlatformAdapters() {
        val manager = object : WifiP2pManager {
            override val isAvailable = MutableStateFlow(false)
            override val connectionState: Flow<P2pConnectionState> = MutableStateFlow(P2pConnectionState.IDLE)
            override val glassesIpAddress = MutableStateFlow<String?>(null)

            override fun discoverPeers() = emptyFlow<P2pPeer>()
            override fun stopDiscovery() = Unit
            override suspend fun connect(peerAddress: String) = Unit
            override suspend fun disconnect() = Unit
            override fun isConnected() = false
            override fun setGlassesIpAddress(ip: String) = Unit
            override suspend fun bindToP2pNetwork() = false
            override fun cancelConnection() = Unit
        }

        assertFalse(manager.supportsTrueWifiDirect)
    }
}

package com.ad_glasses.wifiadb

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.InetAddress

internal object WifiAdbNetworkRules {
    fun parseIpv4(value: String): ByteArray? {
        val parts = value.split('.')
        if (parts.size != 4) return null
        return parts.map { part ->
            if (part.isEmpty() || part.length > 3 || part.any { !it.isDigit() }) return null
            part.toIntOrNull()?.takeIf { it in 0..255 }?.toByte() ?: return null
        }.toByteArray()
    }

    fun isSame24(localAddress: InetAddress, glassesIp: String): Boolean {
        val glasses = parseIpv4(glassesIp) ?: return false
        val local = (localAddress as? Inet4Address)?.address ?: return false
        return local[0] == glasses[0] && local[1] == glasses[1] && local[2] == glasses[2]
    }

    fun isP2pInterfaceName(value: String?): Boolean =
        value?.contains("p2p", ignoreCase = true) == true ||
            value?.contains("wfd", ignoreCase = true) == true

    fun isExpectedPeer(
        peerName: String?,
        peerAddress: String?,
        pairedName: String?,
        pairedAddress: String?,
    ): Boolean {
        val nameMatches = !pairedName.isNullOrBlank() && peerName == pairedName
        val addressMatches = !pairedAddress.isNullOrBlank() &&
            peerAddress?.equals(pairedAddress, ignoreCase = true) == true
        val pairedSuffix = pairedAddress
            ?.takeIf { it.isNotBlank() }
            ?.takeLast(5)
            ?.replace(":", "")
        val suffixMatches = !pairedSuffix.isNullOrBlank() &&
            peerName?.endsWith("_$pairedSuffix", ignoreCase = true) == true
        return nameMatches || addressMatches || suffixMatches
    }

    fun selectP2pNetwork(connectivityManager: ConnectivityManager, glassesIp: String): Network? {
        if (parseIpv4(glassesIp) == null) return null
        val matches = connectivityManager.allNetworks.filter { network ->
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return@filter false
            if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            ) {
                return@filter false
            }
            val linkProperties = connectivityManager.getLinkProperties(network) ?: return@filter false
            isP2pInterfaceName(linkProperties.interfaceName) &&
                linkProperties.linkAddresses.any { isSame24(it.address, glassesIp) }
        }
        return matches.singleOrNull()
    }

    fun relayEndpoints(addresses: Iterable<InetAddress>, port: Int): List<String> =
        addresses
            .filterIsInstance<Inet4Address>()
            .filterNot { it.isAnyLocalAddress || it.isLoopbackAddress || it.isLinkLocalAddress }
            .mapNotNull { it.hostAddress }
            .distinct()
            .sorted()
            .map { "$it:$port" }
}

package com.adglasses.app.integrations.heycyan

import android.net.Network
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class HeyCyanMediaClient {
    suspend fun fetchMediaConfig(network: Network, deviceIp: String): String =
        getText(network, "http://$deviceIp/files/media.config")

    suspend fun fetchFile(network: Network, deviceIp: String, remoteName: String): ByteArray {
        require(remoteName.isNotBlank() && !remoteName.contains("..") && !remoteName.contains('/') && !remoteName.contains('\\'))
        return withContext(Dispatchers.IO) {
            val connection = open(network, "http://$deviceIp/files/${encodePathSegment(remoteName)}")
            try {
                require(connection.responseCode in 200..299) { "Glasses HTTP ${connection.responseCode}" }
                connection.inputStream.use { it.readBytes() }
            } finally {
                connection.disconnect()
            }
        }
    }

    private suspend fun getText(network: Network, url: String): String = withContext(Dispatchers.IO) {
        val connection = open(network, url)
        try {
            require(connection.responseCode in 200..299) { "Glasses HTTP ${connection.responseCode}" }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun open(network: Network, url: String): HttpURLConnection =
        (network.openConnection(URL(url)) as HttpURLConnection).apply {
            connectTimeout = 5_000
            readTimeout = 10_000
            instanceFollowRedirects = false
            useCaches = false
        }

    private fun encodePathSegment(value: String): String = java.net.URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
}

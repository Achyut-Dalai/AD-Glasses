package com.adglasses.app.integrations.heycyan

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class GlassesNetworkLease(
    val network: Network,
    val close: () -> Unit,
)

class HeyCyanWifiCoordinator(context: Context) {
    private val appContext = context.applicationContext
    private val connectivity = appContext.getSystemService(ConnectivityManager::class.java)

    suspend fun joinAccessPoint(preparation: HeyCyanNetworkPreparation): GlassesNetworkLease {
        require(preparation.mode == HeyCyanNetworkMode.AccessPoint) {
            "The glasses selected Wi-Fi Direct; an AP network lease cannot be created for that response"
        }
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(appContext, Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
            error("Nearby Wi-Fi devices permission is required")
        }
        val specifier = WifiNetworkSpecifier.Builder()
            .setSsid(preparation.ssid)
            .setWpa2Passphrase(preparation.passphrase)
            .build()
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(specifier)
            .build()

        return withTimeout(30_000) {
            requestNetwork(request)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestNetwork(request: NetworkRequest): GlassesNetworkLease = suspendCancellableCoroutine { continuation ->
        lateinit var callback: ConnectivityManager.NetworkCallback
        callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (!continuation.isActive) return
                continuation.resume(
                    GlassesNetworkLease(
                        network = network,
                        close = { runCatching { connectivity.unregisterNetworkCallback(callback) } },
                    )
                )
            }

            override fun onUnavailable() {
                if (continuation.isActive) continuation.resumeWithException(IllegalStateException("Could not join the glasses Wi-Fi network"))
            }
        }
        connectivity.requestNetwork(request, callback)
        continuation.invokeOnCancellation { runCatching { connectivity.unregisterNetworkCallback(callback) } }
    }
}

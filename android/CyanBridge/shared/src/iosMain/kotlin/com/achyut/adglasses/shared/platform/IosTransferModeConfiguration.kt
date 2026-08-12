package com.achyut.adglasses.shared.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Host seam for the proprietary iOS transfer-mode call.
 *
 * The KMP host intentionally does not link QCSDK.framework, so it cannot safely
 * call QCSDKCmdCreator.openWifiWithMode itself. A device host that does link the
 * vendor SDK should call [configurePreparedHotspot] from that command's success
 * callback. The shared sync flow then performs the iOS hotspot join without
 * sending a second vendor command.
 */
internal data class IosTransferHotspotCredentials(
    val ssid: String,
    val passphrase: String,
    val transferModePrepared: Boolean,
    val deviceIp: String?,
)

object IosTransferModeConfiguration {
    private val configuredCredentials = MutableStateFlow<IosTransferHotspotCredentials?>(null)

    /**
     * Configure credentials for a future sync. The fallback BLE transfer command
     * remains enabled because the host has not confirmed that it already ran the
     * proprietary SDK readiness call.
     */
    fun configureHotspot(ssid: String, passphrase: String) {
        configuredCredentials.value = credentials(
            ssid = ssid,
            passphrase = passphrase,
            transferModePrepared = false,
            deviceIp = null,
        )
    }

    /**
     * Configure credentials after the host has successfully called the vendor
     * SDK's transfer-mode command. This is the preferred physical-device path.
     */
    fun configurePreparedHotspot(ssid: String, passphrase: String) {
        configurePreparedHotspot(ssid, passphrase, deviceIp = null)
    }

    /**
     * Variant for hosts that also receive the IP from QCSDK's
     * getDeviceWifiIPSuccess callback.
     */
    fun configurePreparedHotspot(ssid: String, passphrase: String, deviceIp: String?) {
        configuredCredentials.value = credentials(
            ssid = ssid,
            passphrase = passphrase,
            transferModePrepared = true,
            deviceIp = deviceIp,
        )
    }

    /** Clear credentials when the host disconnects or changes devices. */
    fun clearHotspot() {
        configuredCredentials.value = null
    }

    internal fun current(): IosTransferHotspotCredentials? = configuredCredentials.value

    internal suspend fun awaitCredentials(timeoutMs: Long): IosTransferHotspotCredentials? {
        if (timeoutMs <= 0L) return configuredCredentials.value
        return withTimeoutOrNull(timeoutMs) {
            configuredCredentials.filterNotNull().first()
        }
    }

    private fun credentials(
        ssid: String,
        passphrase: String,
        transferModePrepared: Boolean,
        deviceIp: String?,
    ): IosTransferHotspotCredentials {
        val normalizedSsid = ssid.trim()
        require(normalizedSsid.isNotEmpty()) { "An iOS hotspot SSID is required" }
        val normalizedDeviceIp = deviceIp?.trim()?.takeIf { it.isNotEmpty() }?.also { value ->
            val octets = value.split('.')
            require(octets.size == 4 && octets.all { it.toIntOrNull()?.let { octet -> octet in 0..255 } == true }) {
                "A valid glasses IPv4 address is required"
            }
        }
        return IosTransferHotspotCredentials(
            ssid = normalizedSsid,
            passphrase = passphrase,
            transferModePrepared = transferModePrepared,
            deviceIp = normalizedDeviceIp,
        )
    }
}

package com.adglasses.app.core.background

import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothLeDeviceFilter
import android.companion.CompanionDeviceManager
import android.companion.ObservingDevicePresenceRequest
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.bluetooth.le.ScanFilter
import android.os.Build
import com.adglasses.app.core.model.GlassesConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface CompanionLinkState {
    data object Unsupported : CompanionLinkState
    data object Available : CompanionLinkState
    data object Associating : CompanionLinkState
    data class Linked(val address: String) : CompanionLinkState
    data class Failed(val reason: String) : CompanionLinkState
}

/**
 * Android-only companion association layer.
 *
 * The BLE/GATT transport remains our verified HeyCyan implementation. CompanionDeviceManager does
 * not replace it; it gives Android a user-approved device association and presence signal so the
 * system can revive/bind the app around the glasses more reliably in the background.
 */
class CompanionPresenceManager(context: Context) {
    private val appContext = context.applicationContext
    private val manager: CompanionDeviceManager? =
        appContext.getSystemService(CompanionDeviceManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var previousRememberedAddress: String? = null

    private val _state = MutableStateFlow<CompanionLinkState>(initialState())
    val state: StateFlow<CompanionLinkState> = _state.asStateFlow()

    fun bindGlassesState(glassesState: StateFlow<GlassesConnectionState>) {
        scope.launch {
            glassesState.collect { state ->
                val address = state.address
                val previous = previousRememberedAddress
                previousRememberedAddress = address

                if (address == null && previous != null) {
                    // A normal Disconnect preserves the remembered address. Only Forget clears it,
                    // so remove the OS association too rather than leaving stale presence work.
                    disassociate(previous)
                } else if (address != null) {
                    refresh(address)
                }
            }
        }
    }

    fun refresh(address: String?) {
        if (!isSupported()) {
            _state.value = CompanionLinkState.Unsupported
            return
        }
        val normalized = address?.trim()?.takeIf { it.isNotEmpty() }
        if (normalized == null) {
            _state.value = CompanionLinkState.Available
            return
        }
        val association = findAssociation(normalized)
        if (association == null) {
            if (_state.value !is CompanionLinkState.Associating) {
                _state.value = CompanionLinkState.Available
            }
            return
        }
        ensurePresenceObservation(association, normalized)
        _state.value = CompanionLinkState.Linked(normalized)
    }

    fun requestAssociation(
        address: String,
        launchConsent: (IntentSender) -> Unit,
    ) {
        val normalized = address.trim()
        require(normalized.isNotEmpty()) { "Connect or select the glasses first" }
        check(isSupported()) { "Android companion-device setup is unavailable on this device" }
        check(Build.VERSION.SDK_INT >= 33) { "Background link requires Android 13 or newer" }
        val deviceManager = checkNotNull(manager)

        findAssociation(normalized)?.let { association ->
            ensurePresenceObservation(association, normalized)
            _state.value = CompanionLinkState.Linked(normalized)
            return
        }

        _state.value = CompanionLinkState.Associating
        val scanFilter = ScanFilter.Builder()
            .setDeviceAddress(normalized)
            .build()
        val deviceFilter = BluetoothLeDeviceFilter.Builder()
            .setScanFilter(scanFilter)
            .build()
        val request = AssociationRequest.Builder()
            .addDeviceFilter(deviceFilter)
            .setSingleDevice(true)
            .build()

        deviceManager.associate(
            request,
            appContext.mainExecutor,
            object : CompanionDeviceManager.Callback() {
                override fun onAssociationPending(intentSender: IntentSender) {
                    launchConsent(intentSender)
                }

                override fun onAssociationCreated(associationInfo: AssociationInfo) {
                    val associatedAddress = associationInfo.deviceMacAddress?.toString() ?: normalized
                    ensurePresenceObservation(associationInfo, associatedAddress)
                    _state.value = CompanionLinkState.Linked(associatedAddress)
                }

                override fun onFailure(errorMessage: CharSequence?) {
                    _state.value = CompanionLinkState.Failed(
                        errorMessage?.toString()?.takeIf { it.isNotBlank() }
                            ?: "Android could not create the glasses background link"
                    )
                }
            },
        )
    }

    fun disassociate(address: String) {
        if (!isSupported() || Build.VERSION.SDK_INT < 33) return
        val deviceManager = manager ?: return
        val matches = deviceManager.myAssociations.filter { association ->
            association.deviceMacAddress?.toString().equals(address, ignoreCase = true)
        }
        matches.forEach { association ->
            runCatching { deviceManager.disassociate(association.id) }
        }
        _state.value = CompanionLinkState.Available
    }

    private fun ensurePresenceObservation(association: AssociationInfo, address: String) {
        if (Build.VERSION.SDK_INT < 31) return
        val deviceManager = manager ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= 36) {
                val request = ObservingDevicePresenceRequest.Builder()
                    .setAssociationId(association.id)
                    .build()
                deviceManager.startObservingDevicePresence(request)
            } else {
                @Suppress("DEPRECATION")
                deviceManager.startObservingDevicePresence(address)
            }
        }.onFailure { error ->
            _state.value = CompanionLinkState.Failed(
                error.message ?: "Could not enable Android companion presence"
            )
        }
    }

    private fun findAssociation(address: String): AssociationInfo? {
        if (Build.VERSION.SDK_INT < 33) return null
        return manager?.myAssociations?.firstOrNull { association ->
            association.deviceMacAddress?.toString().equals(address, ignoreCase = true)
        }
    }

    private fun initialState(): CompanionLinkState =
        if (isSupported()) CompanionLinkState.Available else CompanionLinkState.Unsupported

    private fun isSupported(): Boolean =
        Build.VERSION.SDK_INT >= 33 &&
            manager != null &&
            appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP)
}

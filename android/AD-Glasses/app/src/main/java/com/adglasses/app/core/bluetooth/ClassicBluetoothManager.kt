package com.adglasses.app.core.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

sealed interface ClassicBluetoothState {
    data object Idle : ClassicBluetoothState
    data object Searching : ClassicBluetoothState
    data class Pairing(val name: String) : ClassicBluetoothState
    data class Paired(val name: String) : ClassicBluetoothState
    data class Connecting(val name: String) : ClassicBluetoothState
    data class Connected(
        val name: String,
        val calls: Boolean,
        val media: Boolean,
    ) : ClassicBluetoothState
    data class Failed(val reason: String) : ClassicBluetoothState
}

/**
 * Android-only companion to the verified HeyCyan BLE session.
 *
 * BLE remains the application-control transport. After the glasses receive their captured 0x49
 * request, this manager owns the OS-visible Classic Bluetooth side used by HFP/A2DP/AVRCP. It does
 * not use hidden profile APIs: Android's public bonding UI remains the security boundary.
 */
class ClassicBluetoothManager(context: Context) {
    companion object {
        private const val DISCOVERY_TIMEOUT_MS = 25_000L
    }

    private val appContext = context.applicationContext
    private val bluetoothManager = appContext.getSystemService(BluetoothManager::class.java)
    private val adapter: BluetoothAdapter? get() = bluetoothManager.adapter
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow<ClassicBluetoothState>(ClassicBluetoothState.Idle)
    val state: StateFlow<ClassicBluetoothState> = _state.asStateFlow()

    private var targetName: String? = null
    private var targetAddress: String? = null
    private var discoveryTimeout: Job? = null
    private var a2dpConnected = false
    private var headsetConnected = false

    init {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
        }
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    /**
     * Ensure the system Classic link exists for the currently verified BLE glasses.
     *
     * If a matching JS-01/HeyCyan device is already bonded, no pairing UI is shown. Otherwise a
     * Classic discovery is started and Android presents its normal pairing confirmation when the
     * matching radio is found.
     */
    fun ensureLink(bleName: String?) {
        targetName = bleName?.trim()?.takeIf { it.isNotBlank() }
        if (!hasConnectPermission() || !hasScanPermission()) {
            _state.value = ClassicBluetoothState.Failed("Bluetooth nearby-device permission is required for calls and audio")
            return
        }

        val currentAdapter = adapter ?: run {
            _state.value = ClassicBluetoothState.Failed("Bluetooth is unavailable on this phone")
            return
        }
        if (!currentAdapter.isEnabled) {
            _state.value = ClassicBluetoothState.Failed("Turn on Bluetooth to pair glasses calls and audio")
            return
        }

        val bonded = runCatching {
            currentAdapter.bondedDevices.firstOrNull(::matchesTarget)
        }.getOrNull()
        if (bonded != null) {
            targetAddress = bonded.address
            val name = safeName(bonded)
            _state.value = ClassicBluetoothState.Paired(name)
            inspectConnectedProfiles()
            connectProfilesIfSupported(bonded)
            return
        }

        startDiscovery(currentAdapter)
    }

    fun refresh() {
        if (!hasConnectPermission()) return
        inspectConnectedProfiles()
    }

    @SuppressLint("MissingPermission")
    private fun startDiscovery(currentAdapter: BluetoothAdapter) {
        discoveryTimeout?.cancel()
        if (currentAdapter.isDiscovering) runCatching { currentAdapter.cancelDiscovery() }
        targetAddress = null
        a2dpConnected = false
        headsetConnected = false
        _state.value = ClassicBluetoothState.Searching

        if (!currentAdapter.startDiscovery()) {
            _state.value = ClassicBluetoothState.Failed("Android could not start Classic Bluetooth discovery")
            return
        }

        discoveryTimeout = scope.launch {
            delay(DISCOVERY_TIMEOUT_MS)
            if (_state.value is ClassicBluetoothState.Searching) {
                runCatching { currentAdapter.cancelDiscovery() }
                _state.value = ClassicBluetoothState.Failed("Could not find the JS-01 calls/audio radio. Keep the glasses awake and retry.")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleFound(device: BluetoothDevice) {
        if (!matchesTarget(device)) return
        val type = runCatching { device.type }.getOrDefault(BluetoothDevice.DEVICE_TYPE_UNKNOWN)
        if (type == BluetoothDevice.DEVICE_TYPE_LE) return

        adapter?.let { current ->
            if (current.isDiscovering) runCatching { current.cancelDiscovery() }
        }
        discoveryTimeout?.cancel()
        targetAddress = device.address
        val name = safeName(device)

        when (device.bondState) {
            BluetoothDevice.BOND_BONDED -> {
                _state.value = ClassicBluetoothState.Paired(name)
                inspectConnectedProfiles()
                connectProfilesIfSupported(device)
            }
            BluetoothDevice.BOND_BONDING -> {
                _state.value = ClassicBluetoothState.Pairing(name)
            }
            else -> {
                _state.value = ClassicBluetoothState.Pairing(name)
                val started = runCatching {
                    if (Build.VERSION.SDK_INT >= 37) {
                        device.createBond(BluetoothDevice.TRANSPORT_BREDR)
                    } else {
                        device.createBond()
                    }
                }.getOrDefault(false)
                if (!started) {
                    _state.value = ClassicBluetoothState.Failed("Android could not start pairing with $name")
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleBondChange(device: BluetoothDevice) {
        if (!matchesTarget(device)) return
        targetAddress = device.address
        val name = safeName(device)
        when (device.bondState) {
            BluetoothDevice.BOND_BONDING -> _state.value = ClassicBluetoothState.Pairing(name)
            BluetoothDevice.BOND_BONDED -> {
                _state.value = ClassicBluetoothState.Paired(name)
                inspectConnectedProfiles()
                connectProfilesIfSupported(device)
            }
            BluetoothDevice.BOND_NONE -> {
                if (_state.value is ClassicBluetoothState.Pairing) {
                    _state.value = ClassicBluetoothState.Failed("Pairing with $name was not completed")
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectProfilesIfSupported(device: BluetoothDevice) {
        if (Build.VERSION.SDK_INT < 37) return
        if (device.bondState != BluetoothDevice.BOND_BONDED) return
        val name = safeName(device)
        val status = runCatching { device.connect() }.getOrElse {
            _state.value = ClassicBluetoothState.Failed(it.message ?: "Could not connect glasses audio profiles")
            return
        }
        if (status == BluetoothStatusCodes.SUCCESS) {
            _state.value = ClassicBluetoothState.Connecting(name)
        } else {
            _state.value = ClassicBluetoothState.Paired(name)
        }
    }

    @SuppressLint("MissingPermission")
    private fun inspectConnectedProfiles() {
        val currentAdapter = adapter ?: return
        if (!hasConnectPermission()) return
        a2dpConnected = false
        headsetConnected = false
        currentAdapter.getProfileProxy(appContext, profileListener, BluetoothProfile.A2DP)
        currentAdapter.getProfileProxy(appContext, profileListener, BluetoothProfile.HEADSET)
    }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        @SuppressLint("MissingPermission")
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            val matching = runCatching { proxy.connectedDevices.firstOrNull(::matchesTarget) }.getOrNull()
            if (matching != null) {
                targetAddress = matching.address
                when (profile) {
                    BluetoothProfile.A2DP -> a2dpConnected = true
                    BluetoothProfile.HEADSET -> headsetConnected = true
                }
                publishProfileState(matching)
            }
            runCatching { adapter?.closeProfileProxy(profile, proxy) }
        }

        override fun onServiceDisconnected(profile: Int) = Unit
    }

    @SuppressLint("MissingPermission")
    private fun handleProfileState(intent: Intent) {
        val device = bluetoothDeviceExtra(intent) ?: return
        if (!matchesTarget(device)) return
        targetAddress = device.address
        val connected = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED) ==
            BluetoothProfile.STATE_CONNECTED
        when (intent.action) {
            BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> a2dpConnected = connected
            BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> headsetConnected = connected
        }
        publishProfileState(device)
    }

    @SuppressLint("MissingPermission")
    private fun publishProfileState(device: BluetoothDevice) {
        val name = safeName(device)
        _state.value = if (a2dpConnected || headsetConnected) {
            ClassicBluetoothState.Connected(
                name = name,
                calls = headsetConnected,
                media = a2dpConnected,
            )
        } else if (device.bondState == BluetoothDevice.BOND_BONDED) {
            ClassicBluetoothState.Paired(name)
        } else {
            _state.value
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent ?: return
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> bluetoothDeviceExtra(intent)?.let(::handleFound)
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> bluetoothDeviceExtra(intent)?.let(::handleBondChange)
                BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
                BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> handleProfileState(intent)
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    if (_state.value is ClassicBluetoothState.Searching) {
                        discoveryTimeout?.cancel()
                        _state.value = ClassicBluetoothState.Failed("Could not find the JS-01 calls/audio radio. Keep the glasses awake and retry.")
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun matchesTarget(device: BluetoothDevice): Boolean {
        targetAddress?.let { address ->
            if (device.address.equals(address, ignoreCase = true)) return true
        }
        val candidate = safeName(device)
        if (!isSupportedGlassesName(candidate)) return false
        val requested = targetName ?: return true
        val requestedKey = normalizedProductKey(requested)
        val candidateKey = normalizedProductKey(candidate)
        return when {
            requestedKey.startsWith("js01") -> candidateKey.startsWith("js01")
            requestedKey.contains("heycyan") -> candidateKey.contains("heycyan")
            requestedKey.startsWith("o") -> candidateKey.startsWith("o")
            requestedKey.startsWith("q") -> candidateKey.startsWith("q")
            else -> candidateKey == requestedKey
        }
    }

    @SuppressLint("MissingPermission")
    private fun safeName(device: BluetoothDevice): String = runCatching { device.name }
        .getOrNull()
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: "JS-01 Pro"

    private fun isSupportedGlassesName(raw: String?): Boolean {
        val name = raw?.trim()?.lowercase(Locale.ROOT).orEmpty()
        return name.startsWith("js-01") ||
            name.startsWith("js01") ||
            name.contains("heycyan") ||
            name.contains("hey cyan") ||
            name.startsWith("o_") ||
            name.startsWith("q_")
    }

    private fun normalizedProductKey(raw: String): String = raw
        .lowercase(Locale.ROOT)
        .filter { it.isLetterOrDigit() }

    private fun bluetoothDeviceExtra(intent: Intent): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }

    private fun hasConnectPermission(): Boolean = Build.VERSION.SDK_INT < 31 ||
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun hasScanPermission(): Boolean = Build.VERSION.SDK_INT < 31 ||
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
}

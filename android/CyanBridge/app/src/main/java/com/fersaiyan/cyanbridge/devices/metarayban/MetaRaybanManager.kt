package com.fersaiyan.cyanbridge.devices.metarayban

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Manages Meta Ray-Ban glasses integration via the Meta Wearables DAT SDK.
 *
 * This class handles:
 * - SDK initialization
 * - Device registration and pairing
 * - Camera streaming and photo capture
 * - Display rendering (for Meta Ray-Ban Display)
 * - Session lifecycle management
 *
 * Note: The Meta DAT SDK is optional. If not available, all operations will fail gracefully.
 */
class MetaRaybanManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "MetaRaybanManager"

        @Volatile
        private var instance: MetaRaybanManager? = null

        fun getInstance(context: Context): MetaRaybanManager {
            return instance ?: synchronized(this) {
                instance ?: MetaRaybanManager(context.applicationContext).also { instance = it }
            }
        }

        // Check if Meta DAT SDK is available at runtime
        private val isSdkAvailable: Boolean by lazy {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@lazy false
            try {
                Class.forName("com.meta.wearable.dat.core.Wearables")
                true
            } catch (e: ClassNotFoundException) {
                false
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // SDK state
    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _registrationState = MutableStateFlow(RegistrationState.UNAVAILABLE)
    val registrationState: StateFlow<RegistrationState> = _registrationState.asStateFlow()

    private val _deviceSessionState = MutableStateFlow(DeviceSessionState.IDLE)
    val deviceSessionState: StateFlow<DeviceSessionState> = _deviceSessionState.asStateFlow()

    // Streaming state
    private val _streamState = MutableStateFlow(StreamState.STOPPED)
    val streamState: StateFlow<StreamState> = _streamState.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    // Photo capture state
    private val _lastCapturedPhoto = MutableStateFlow<Any?>(null)
    val lastCapturedPhoto: StateFlow<Any?> = _lastCapturedPhoto.asStateFlow()

    // Display state
    private val _isDisplayActive = MutableStateFlow(false)
    val isDisplayActive: StateFlow<Boolean> = _isDisplayActive.asStateFlow()

    // Error state
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    /**
     * Initialize the Meta Wearables DAT SDK.
     * Call this once at app startup.
     */
    fun initialize() {
        if (_isInitialized.value) {
            Log.d(TAG, "SDK already initialized")
            return
        }

        if (!isSdkAvailable) {
            Log.w(TAG, "Meta Wearables DAT SDK not available. Add GITHUB_TOKEN to enable Meta Ray-Ban support.")
            _lastError.value = "SDK not available"
            return
        }

        try {
            // Use reflection to initialize the SDK
            val wearablesClass = Class.forName("com.meta.wearable.dat.core.Wearables")
            val wearables = wearablesClass.getField("INSTANCE").get(null)
            val initializeMethod = wearablesClass.getMethod("initialize", Context::class.java)
            initializeMethod.invoke(wearables, context)
            _isInitialized.value = true
            refreshRegistrationState()
            Log.i(TAG, "Meta Wearables DAT SDK initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Meta Wearables DAT SDK", e)
            _isInitialized.value = false
            _lastError.value = e.message
        }
    }

    /**
     * Start the registration flow.
     * This will deeplink to the Meta AI app for confirmation.
     */
    fun startRegistration(activity: Activity) {
        if (!isSdkAvailable) {
            _lastError.value = "SDK not available"
            return
        }

        try {
            val wearablesClass = Class.forName("com.meta.wearable.dat.core.Wearables")
            val wearables = wearablesClass.getField("INSTANCE").get(null)
            val startRegMethod = wearablesClass.getMethod("startRegistration", Activity::class.java)
            _registrationState.value = RegistrationState.REGISTERING
            startRegMethod.invoke(wearables, activity)
            refreshRegistrationState()
            Log.i(TAG, "Started registration flow")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start registration", e)
            _lastError.value = e.message
        }
    }

    /**
     * Start the unregistration flow.
     */
    fun startUnregistration(activity: Activity) {
        if (!isSdkAvailable) {
            _lastError.value = "SDK not available"
            return
        }

        try {
            val wearablesClass = Class.forName("com.meta.wearable.dat.core.Wearables")
            val wearables = wearablesClass.getField("INSTANCE").get(null)
            val startUnregMethod = wearablesClass.getMethod("startUnregistration", Activity::class.java)
            _registrationState.value = RegistrationState.REGISTERING
            startUnregMethod.invoke(wearables, activity)
            refreshRegistrationState()
            Log.i(TAG, "Started unregistration flow")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start unregistration", e)
            _lastError.value = e.message
        }
    }

    /**
     * Check if the app is registered with the Meta AI app.
     */
    fun isRegistered(): Boolean {
        return _registrationState.value == RegistrationState.REGISTERED
    }

    fun refreshRegistrationState() {
        if (!isSdkAvailable || !_isInitialized.value) return
        runCatching {
            val wearablesClass = Class.forName("com.meta.wearable.dat.core.Wearables")
            val wearables = wearablesClass.getField("INSTANCE").get(null)
            val stateFlow = wearablesClass.getMethod("getRegistrationState").invoke(wearables)
            val sdkState = stateFlow.javaClass.getMethod("getValue").invoke(stateFlow).toString()
            _registrationState.value = when (sdkState.uppercase()) {
                "REGISTERED" -> RegistrationState.REGISTERED
                "NOT_REGISTERED" -> RegistrationState.UNREGISTERED
                "REGISTERING" -> RegistrationState.REGISTERING
                else -> RegistrationState.UNAVAILABLE
            }
        }.onFailure { error ->
            Log.w(TAG, "Unable to read Meta registration state", error)
        }
    }

    fun handleRegistrationCallback(intent: Intent): Boolean {
        val data = intent.data ?: return false
        if (!data.scheme.equals("cyanbridge", ignoreCase = true)) return false
        refreshRegistrationState()
        val error = data.getQueryParameter("error")
        val result = data.getQueryParameter("status")
            ?: data.getQueryParameter("result")
            ?: data.getQueryParameter("success")
        if (!error.isNullOrBlank()) {
            _lastError.value = error
            _registrationState.value = RegistrationState.UNREGISTERED
        } else if (result.equals("registered", true) || result.equals("success", true) || result == "true") {
            _registrationState.value = RegistrationState.REGISTERED
        }
        return true
    }

    /**
     * Create a device session and start streaming.
     */
    fun startSession(onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (!isSdkAvailable) {
            onError("SDK not available. Add GITHUB_TOKEN to enable Meta Ray-Ban support.")
            return
        }

        if (!_isInitialized.value) {
            onError("SDK not initialized")
            return
        }

        onError("Meta session support is not implemented yet")
    }

    /**
     * Stop the current session and clean up resources.
     */
    fun stopSession() {
        scope.launch {
            try {
                _deviceSessionState.value = DeviceSessionState.STOPPING
                // TODO: Stop session via reflection
                _deviceSessionState.value = DeviceSessionState.IDLE
                _isStreaming.value = false
                _streamState.value = StreamState.STOPPED
                _isDisplayActive.value = false
                Log.i(TAG, "Session stopped")
            } catch (e: Exception) {
                Log.e(TAG, "Exception stopping session", e)
            }
        }
    }

    /**
     * Start camera streaming with default configuration.
     */
    fun startStreaming(
        onFrame: (Bitmap) -> Unit,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (!isSdkAvailable) {
            onError("SDK not available")
            return
        }

        onError("Meta camera streaming is not implemented yet")
    }

    /**
     * Stop camera streaming.
     */
    fun stopStreaming() {
        scope.launch {
            try {
                _streamState.value = StreamState.STOPPING
                // TODO: Stop stream via reflection
                _streamState.value = StreamState.STOPPED
                _isStreaming.value = false
                Log.i(TAG, "Streaming stopped")
            } catch (e: Exception) {
                Log.e(TAG, "Exception stopping stream", e)
            }
        }
    }

    /**
     * Capture a photo from the current stream.
     */
    fun capturePhoto(onSuccess: (Any) -> Unit, onError: (String) -> Unit) {
        if (!isSdkAvailable) {
            onError("SDK not available")
            return
        }

        scope.launch {
            try {
                // TODO: Implement photo capture via reflection
                onError("Photo capture not yet implemented")
            } catch (e: Exception) {
                Log.e(TAG, "Exception capturing photo", e)
                onError(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Start display rendering (for Meta Ray-Ban Display).
     */
    fun startDisplay(onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (!isSdkAvailable) {
            onError("SDK not available")
            return
        }

        onError("Meta display support is not implemented yet")
    }

    /**
     * Stop display rendering.
     */
    fun stopDisplay() {
        scope.launch {
            try {
                _isDisplayActive.value = false
                Log.i(TAG, "Display stopped")
            } catch (e: Exception) {
                Log.e(TAG, "Exception stopping display", e)
            }
        }
    }

    /**
     * Clean up resources when the manager is no longer needed.
     */
    fun destroy() {
        stopSession()
        instance = null
    }

    // Simple enums to represent SDK states without requiring SDK imports
    enum class RegistrationState {
        UNAVAILABLE,
        REGISTERED,
        REGISTERING,
        UNREGISTERED
    }

    enum class DeviceSessionState {
        IDLE,
        STARTING,
        STARTED,
        PAUSED,
        STOPPING,
        STOPPED
    }

    enum class StreamState {
        STOPPED,
        STARTING,
        STREAMING,
        STOPPING,
        PAUSED
    }
}

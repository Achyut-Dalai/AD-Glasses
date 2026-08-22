package com.ad_glasses.devices.metarayban

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Compile-time fallback used only when the private Meta Wearables DAT SDK is not available.
 *
 * The real implementation is excluded from this source set in that case, so AD Glasses keeps
 * building and every non-Meta feature remains usable. Selecting Meta still fails explicitly and
 * never falls through to another glasses protocol.
 */
class MetaRaybanManager private constructor(private val context: Context) {

    companion object {
        private const val UNAVAILABLE_MESSAGE =
            "Meta Wearables DAT SDK is unavailable in this build. Configure github_token/meta_token with GitHub Packages read access to enable Meta support."

        @Volatile
        private var instance: MetaRaybanManager? = null

        fun getInstance(context: Context): MetaRaybanManager =
            instance ?: synchronized(this) {
                instance ?: MetaRaybanManager(context.applicationContext).also { instance = it }
            }
    }

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _registrationState = MutableStateFlow(RegistrationState.UNAVAILABLE)
    val registrationState: StateFlow<RegistrationState> = _registrationState.asStateFlow()

    private val _availableDeviceCount = MutableStateFlow(0)
    val availableDeviceCount: StateFlow<Int> = _availableDeviceCount.asStateFlow()

    private val _selectedDeviceName = MutableStateFlow<String?>(null)
    val selectedDeviceName: StateFlow<String?> = _selectedDeviceName.asStateFlow()

    private val _selectedDeviceIsDisplayCapable = MutableStateFlow(false)
    val selectedDeviceIsDisplayCapable: StateFlow<Boolean> = _selectedDeviceIsDisplayCapable.asStateFlow()

    private val _deviceSessionState = MutableStateFlow(DeviceSessionState.IDLE)
    val deviceSessionState: StateFlow<DeviceSessionState> = _deviceSessionState.asStateFlow()

    private val _streamState = MutableStateFlow(StreamState.STOPPED)
    val streamState: StateFlow<StreamState> = _streamState.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _lastCapturedPhoto = MutableStateFlow<CapturedPhoto?>(null)
    val lastCapturedPhoto: StateFlow<CapturedPhoto?> = _lastCapturedPhoto.asStateFlow()

    private val _isDisplayActive = MutableStateFlow(false)
    val isDisplayActive: StateFlow<Boolean> = _isDisplayActive.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(UNAVAILABLE_MESSAGE)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    fun initialize() {
        _isInitialized.value = false
        _registrationState.value = RegistrationState.UNAVAILABLE
        _lastError.value = UNAVAILABLE_MESSAGE
    }

    fun startRegistration(activity: Activity) {
        @Suppress("UNUSED_VARIABLE")
        val ignored = activity
        unavailable("registration")
    }

    fun startUnregistration(activity: Activity) {
        @Suppress("UNUSED_VARIABLE")
        val ignored = activity
        unavailable("unregistration")
    }

    fun isRegistered(): Boolean = false

    fun isCameraReady(): Boolean = false

    suspend fun awaitCameraReady(timeoutMs: Long = 10_000L): Boolean {
        @Suppress("UNUSED_VARIABLE")
        val ignored = timeoutMs
        unavailable("camera readiness")
        return false
    }

    fun refreshRegistrationState() {
        _registrationState.value = RegistrationState.UNAVAILABLE
    }

    fun handleRegistrationCallback(intent: Intent): Boolean {
        @Suppress("UNUSED_VARIABLE")
        val ignored = intent
        return false
    }

    fun checkCameraPermission(
        onGranted: () -> Unit,
        onRequestNeeded: () -> Unit,
        onError: (String) -> Unit,
    ) {
        @Suppress("UNUSED_VARIABLE")
        val ignoredGranted = onGranted
        @Suppress("UNUSED_VARIABLE")
        val ignoredRequest = onRequestNeeded
        onError(unavailable("camera permission"))
    }

    fun startSession(onSuccess: () -> Unit, onError: (String) -> Unit) {
        @Suppress("UNUSED_VARIABLE")
        val ignored = onSuccess
        onError(unavailable("device session"))
    }

    fun stopSession() {
        _deviceSessionState.value = DeviceSessionState.IDLE
    }

    fun startStreaming(
        onFrame: (Bitmap) -> Unit,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        @Suppress("UNUSED_VARIABLE")
        val ignoredFrame = onFrame
        @Suppress("UNUSED_VARIABLE")
        val ignoredSuccess = onSuccess
        onError(unavailable("camera stream"))
    }

    fun stopStreaming() {
        _isStreaming.value = false
        _streamState.value = StreamState.STOPPED
    }

    fun capturePhoto(onSuccess: (CapturedPhoto) -> Unit, onError: (String) -> Unit) {
        @Suppress("UNUSED_VARIABLE")
        val ignored = onSuccess
        onError(unavailable("photo capture"))
    }

    suspend fun capturePhotoOnce(timeoutMs: Long = 20_000L): CapturedPhoto {
        @Suppress("UNUSED_VARIABLE")
        val ignored = timeoutMs
        throw IllegalStateException(unavailable("photo capture"))
    }

    suspend fun savePhotoForProcessing(photo: CapturedPhoto, namePrefix: String): File {
        @Suppress("UNUSED_VARIABLE")
        val ignoredPhoto = photo
        @Suppress("UNUSED_VARIABLE")
        val ignoredPrefix = namePrefix
        throw IllegalStateException(unavailable("photo processing"))
    }

    fun startDisplay(onSuccess: () -> Unit, onError: (String) -> Unit) {
        @Suppress("UNUSED_VARIABLE")
        val ignored = onSuccess
        onError(unavailable("display session"))
    }

    fun stopDisplay() {
        _isDisplayActive.value = false
    }

    fun reportExternalError(operation: String, message: String): String {
        val detail = "$operation: $message"
        _lastError.value = detail
        return detail
    }

    fun diagnosticsSnapshot(): String = buildString {
        appendLine("backend=unavailable")
        appendLine("initialized=false")
        appendLine("registration=${RegistrationState.UNAVAILABLE}")
        appendLine("availableDeviceCount=0")
        appendLine("session=${DeviceSessionState.IDLE}")
        appendLine("stream=${StreamState.STOPPED}")
        appendLine("displayActive=false")
        append("lastError=${_lastError.value ?: UNAVAILABLE_MESSAGE}")
    }

    fun destroy() {
        stopStreaming()
        stopDisplay()
        stopSession()
        instance = null
    }

    private fun unavailable(operation: String): String {
        val detail = "$operation: $UNAVAILABLE_MESSAGE"
        _lastError.value = detail
        return detail
    }

    data class CapturedPhoto(
        val bytes: ByteArray,
        val mimeType: String,
        val uri: Uri?,
    )

    enum class RegistrationState {
        UNAVAILABLE,
        AVAILABLE,
        REGISTERED,
        REGISTERING,
        UNREGISTERING,
    }

    enum class DeviceSessionState {
        IDLE,
        STARTING,
        STARTED,
        PAUSED,
        STOPPING,
        STOPPED,
    }

    enum class StreamState {
        STOPPED,
        STARTING,
        STARTED,
        STREAMING,
        STOPPING,
        PAUSED,
        CLOSED,
    }
}

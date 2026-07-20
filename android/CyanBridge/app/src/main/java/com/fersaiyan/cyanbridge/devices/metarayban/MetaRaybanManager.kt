package com.fersaiyan.cyanbridge.devices.metarayban

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.fersaiyan.cyanbridge.glasses.GlassesSession
import com.fersaiyan.cyanbridge.glasses.GlassesSessionCoordinator
import com.fersaiyan.cyanbridge.glasses.GlassesSessionLease
import com.meta.wearable.dat.camera.Stream
import com.meta.wearable.dat.camera.addStream
import com.meta.wearable.dat.camera.types.PhotoData
import com.meta.wearable.dat.camera.types.StreamConfiguration
import com.meta.wearable.dat.camera.types.StreamError
import com.meta.wearable.dat.camera.types.StreamState as DatStreamState
import com.meta.wearable.dat.camera.types.VideoFrame
import com.meta.wearable.dat.camera.types.VideoQuality
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.selectors.AutoDeviceSelector
import com.meta.wearable.dat.core.selectors.SpecificDeviceSelector
import com.meta.wearable.dat.core.session.DeviceSession
import com.meta.wearable.dat.core.session.DeviceSessionState as DatDeviceSessionState
import com.meta.wearable.dat.core.types.Device
import com.meta.wearable.dat.core.types.DeviceCompatibility
import com.meta.wearable.dat.core.types.DeviceIdentifier
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import com.meta.wearable.dat.core.types.RegistrationState as DatRegistrationState
import com.meta.wearable.dat.display.Display
import com.meta.wearable.dat.display.addDisplay
import com.meta.wearable.dat.display.types.DisplayState
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Owns the single Android DAT session used by CyanBridge's Meta camera source.
 *
 * DAT state is collected from the SDK rather than inferred from button presses. The
 * manager deliberately does not use the HeyCyan BLE, media, P2P, or OTA paths.
 */
class MetaRaybanManager private constructor(context: Context) {

    companion object {
        private const val TAG = "MetaRaybanManager"

        @Volatile
        private var instance: MetaRaybanManager? = null

        fun getInstance(context: Context): MetaRaybanManager {
            return instance ?: synchronized(this) {
                instance ?: MetaRaybanManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var registrationJob: Job? = null
    private var registrationErrorJob: Job? = null
    private var devicesJob: Job? = null
    private val deviceMetadataJobs = mutableMapOf<String, Job>()
    private val devicesMetadata = mutableMapOf<String, Device>()

    private var session: DeviceSession? = null
    private var metaCameraLease: GlassesSessionLease? = null
    private var sessionStateJob: Job? = null
    private var sessionErrorJob: Job? = null
    private var stream: Stream? = null
    private var streamStateJob: Job? = null
    private var streamErrorJob: Job? = null
    private var videoJob: Job? = null
    private var display: Display? = null
    private var displayStateJob: Job? = null
    private val captureMutex = Mutex()

    private var selectedDeviceId: String? = null
    private var streamFrameHandler: ((Bitmap) -> Unit)? = null
    private var streamStartedHandler: (() -> Unit)? = null
    private var displayStartedHandler: (() -> Unit)? = null

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

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    fun initialize() {
        if (_isInitialized.value) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            fail("Meta Wearables DAT requires Android 10 or newer")
            return
        }

        Wearables.initialize(context).fold(
            onSuccess = {
                _isInitialized.value = true
                observeWearables()
                Log.i(TAG, "Meta Wearables DAT SDK initialized")
            },
            onFailure = { error, _ -> fail(error.description) },
        )
    }

    private fun observeWearables() {
        if (registrationJob != null) return

        registrationJob = scope.launch {
            Wearables.registrationState.collect { state ->
                _registrationState.value = state.toManagerState()
            }
        }
        registrationErrorJob = scope.launch {
            Wearables.registrationErrorStream.collect { error ->
                fail(error.getLocalizedDescription(context))
            }
        }
        devicesJob = scope.launch {
            Wearables.devices.collect { identifiers ->
                updateDevices(identifiers.map { it.toString() }.toSet())
            }
        }
    }

    private fun updateDevices(identifiers: Set<String>) {
        _availableDeviceCount.value = identifiers.size

        val removed = deviceMetadataJobs.keys - identifiers
        removed.forEach { id ->
            deviceMetadataJobs.remove(id)?.cancel()
            devicesMetadata.remove(id)
        }

        val previousSelection = selectedDeviceId
        selectedDeviceId = when {
            previousSelection != null && previousSelection in identifiers -> previousSelection
            else -> identifiers.firstOrNull()
        }
        updateSelectedDeviceState()

        val newIdentifiers = identifiers - deviceMetadataJobs.keys
        newIdentifiers.forEach { id ->
            val deviceId = id
            val metadataFlow = Wearables.devicesMetadata.entries
                .firstOrNull { it.key.toString() == deviceId }
                ?.value
            if (metadataFlow == null) return@forEach

            deviceMetadataJobs[deviceId] = scope.launch {
                metadataFlow.collect { metadata ->
                    devicesMetadata[deviceId] = metadata
                    updateSelectedDeviceState()
                }
            }
        }
    }

    private fun updateSelectedDeviceState() {
        val selected = selectedDeviceId?.let { id -> devicesMetadata[id] }
        _selectedDeviceName.value = selected?.name?.takeIf { it.isNotBlank() }
        _selectedDeviceIsDisplayCapable.value = selected?.isDisplayCapable() == true
    }

    fun startRegistration(activity: Activity) {
        if (!requireInitialized()) return
        if (_registrationState.value == RegistrationState.REGISTERED) return
        Wearables.startRegistration(activity)
    }

    fun startUnregistration(activity: Activity) {
        if (!requireInitialized()) return
        if (_registrationState.value != RegistrationState.REGISTERED) return
        Wearables.startUnregistration(activity)
    }

    fun isRegistered(): Boolean = _registrationState.value == RegistrationState.REGISTERED

    fun isCameraReady(): Boolean =
        _isInitialized.value &&
            _registrationState.value == RegistrationState.REGISTERED &&
            _availableDeviceCount.value > 0

    suspend fun awaitCameraReady(timeoutMs: Long = 10_000L): Boolean {
        if (isCameraReady()) return true
        return withTimeoutOrNull(timeoutMs) {
            combine(registrationState, availableDeviceCount) { registration, devices ->
                registration == RegistrationState.REGISTERED && devices > 0
            }.first { it }
        } == true
    }

    fun refreshRegistrationState() {
        if (_isInitialized.value) {
            _registrationState.value = Wearables.registrationState.value.toManagerState()
        }
    }

    /** DAT consumes the callback itself; this only tells the Activity to refresh its UI. */
    fun handleRegistrationCallback(intent: android.content.Intent): Boolean {
        if (!intent.data?.scheme.equals("cyanbridge", ignoreCase = true)) return false
        intent.data?.getQueryParameter("error")?.takeIf { it.isNotBlank() }?.let(::fail)
        refreshRegistrationState()
        return true
    }

    fun checkCameraPermission(
        onGranted: () -> Unit,
        onRequestNeeded: () -> Unit,
        onError: (String) -> Unit,
    ) {
        if (!requireInitialized(onError)) return
        scope.launch {
            Wearables.checkPermissionStatus(Permission.CAMERA).fold(
                onSuccess = { status ->
                    if (status == PermissionStatus.Granted) onGranted() else onRequestNeeded()
                },
                onFailure = { error, _ -> onError(error.description) },
            )
        }
    }

    fun startSession(onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (!requireInitialized(onError)) return
        if (_registrationState.value != RegistrationState.REGISTERED) {
            onError("Meta AI registration is required")
            return
        }
        if (_availableDeviceCount.value == 0) {
            onError("No compatible Meta wearable is available")
            return
        }
        when (selectedDeviceId?.let { devicesMetadata[it]?.compatibility }) {
            DeviceCompatibility.DEVICE_UPDATE_REQUIRED -> {
                onError("The Meta wearable requires a firmware update")
                return
            }
            DeviceCompatibility.SDK_UPDATE_REQUIRED -> {
                onError("The Meta wearable requires a newer DAT SDK")
                return
            }
            else -> Unit
        }
        if (session != null && _deviceSessionState.value !in setOf(DeviceSessionState.IDLE, DeviceSessionState.STOPPED)) {
            if (_deviceSessionState.value == DeviceSessionState.STARTED) onSuccess()
            return
        }

        val lease = GlassesSessionCoordinator.tryAcquireLease(GlassesSession.META_CAMERA)
        if (lease == null) {
            onError("Another glasses transport is currently active")
            return
        }
        metaCameraLease = lease

        clearError()
        _deviceSessionState.value = DeviceSessionState.STARTING
        val selector = selectedDeviceId
            ?.let(::DeviceIdentifier)
            ?.let(::SpecificDeviceSelector)
            ?: AutoDeviceSelector()
        val result = Wearables.createSession(selector)
        result.fold(
            onSuccess = { newSession ->
                session = newSession
                observeSession(newSession, onSuccess, onError)
                newSession.start()
            },
            onFailure = { error, _ ->
                releaseMetaCameraLease()
                _deviceSessionState.value = DeviceSessionState.IDLE
                onError(error.description)
            },
        )
    }

    private fun observeSession(
        currentSession: DeviceSession,
        onStarted: () -> Unit,
        onError: (String) -> Unit,
    ) {
        sessionStateJob?.cancel()
        sessionErrorJob?.cancel()
        var started = false
        sessionStateJob = scope.launch {
            currentSession.state.collect { state ->
                _deviceSessionState.value = state.toManagerState()
                when (state) {
                    DatDeviceSessionState.STARTED -> {
                        if (!started) {
                            started = true
                            onStarted()
                        }
                    }
                    DatDeviceSessionState.STOPPED -> {
                        stopStreamInternal()
                        stopDisplayInternal()
                        session = null
                        releaseMetaCameraLease()
                        _deviceSessionState.value = DeviceSessionState.IDLE
                    }
                    else -> Unit
                }
            }
        }
        sessionErrorJob = scope.launch {
            currentSession.errors.collect { error ->
                val message = error.description
                fail(message)
                onError(message)
                currentSession.stop()
            }
        }
    }

    fun stopSession() {
        stopStreamInternal()
        stopDisplayInternal()
        val currentSession = session
        if (currentSession == null) {
            releaseMetaCameraLease()
            _deviceSessionState.value = DeviceSessionState.IDLE
            return
        }
        _deviceSessionState.value = DeviceSessionState.STOPPING
        currentSession.stop()
    }

    fun startStreaming(
        onFrame: (Bitmap) -> Unit,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        if (!requireInitialized(onError)) return
        val currentSession = session
        if (currentSession == null || _deviceSessionState.value != DeviceSessionState.STARTED) {
            onError("Start a Meta device session first")
            return
        }
        if (stream != null) {
            if (_streamState.value == StreamState.STREAMING) onSuccess()
            return
        }

        clearError()
        streamFrameHandler = onFrame
        streamStartedHandler = onSuccess
        _streamState.value = StreamState.STARTING

        currentSession.addStream(
            StreamConfiguration(videoQuality = VideoQuality.MEDIUM, frameRate = 24),
        ).fold(
            onSuccess = { newStream ->
                stream = newStream
                observeStream(newStream, onError)
                newStream.start().onFailure { error, _ ->
                    handleStreamFailure(error.description, onError)
                }
            },
            onFailure = { error, _ ->
                _streamState.value = StreamState.STOPPED
                onError(error.description)
            },
        )
    }

    private fun observeStream(currentStream: Stream, onError: (String) -> Unit) {
        streamStateJob?.cancel()
        streamErrorJob?.cancel()
        videoJob?.cancel()

        streamStateJob = scope.launch {
            currentStream.state.collect { state ->
                _streamState.value = state.toManagerState()
                _isStreaming.value = state == DatStreamState.STREAMING
                if (state == DatStreamState.STREAMING) {
                    streamStartedHandler?.invoke()
                    streamStartedHandler = null
                }
                if (state == DatStreamState.STOPPED || state == DatStreamState.CLOSED) {
                    stopStreamInternal()
                }
            }
        }
        streamErrorJob = scope.launch {
            currentStream.errorStream.collect { error ->
                val message = error.getLocalizedDescription(context)
                if (error == StreamError.STREAM_ERROR) {
                    Log.w(TAG, "Non-terminal DAT stream error: $message")
                } else {
                    handleStreamFailure(message, onError)
                }
            }
        }
        videoJob = scope.launch(Dispatchers.Default) {
            currentStream.videoStream.collect { frame ->
                if (frame.isCompressed || frame.isCodecConfig) return@collect
                val bitmap = YuvToBitmapConverter.convert(frame)
                if (bitmap != null) {
                    withContext(Dispatchers.Main.immediate) {
                        streamFrameHandler?.invoke(bitmap)
                    }
                }
            }
        }
    }

    private fun handleStreamFailure(message: String, onError: (String) -> Unit) {
        fail(message)
        onError(message)
        stopStreamInternal()
    }

    fun stopStreaming() {
        stopStreamInternal()
    }

    private fun stopStreamInternal() {
        videoJob?.cancel()
        videoJob = null
        streamErrorJob?.cancel()
        streamErrorJob = null
        streamStateJob?.cancel()
        streamStateJob = null
        streamStartedHandler = null
        streamFrameHandler = null
        stream?.stop()
        stream = null
        _isStreaming.value = false
        _streamState.value = StreamState.STOPPED
    }

    fun capturePhoto(onSuccess: (CapturedPhoto) -> Unit, onError: (String) -> Unit) {
        if (!requireInitialized(onError)) return
        val currentStream = stream
        if (currentStream == null || _streamState.value != StreamState.STREAMING) {
            onError("Camera stream is not active")
            return
        }

        scope.launch {
            currentStream.capturePhoto().fold(
                onSuccess = { data ->
                    runCatching { persistPhoto(data) }
                        .onSuccess { photo ->
                            _lastCapturedPhoto.value = photo
                            onSuccess(photo)
                        }
                        .onFailure { error ->
                            fail(error.message ?: "Unable to save captured photo")
                            onError(error.message ?: "Unable to save captured photo")
                        }
                },
                onFailure = { error, _ ->
                    onError(error.description)
                },
            )
        }
    }

    /**
     * Captures one fresh photo through the shared DAT session. This is intended for
     * background plugins; it never opens a second session and releases resources it
     * had to start after the one-shot completes.
     */
    suspend fun capturePhotoOnce(timeoutMs: Long = 20_000L): CapturedPhoto = captureMutex.withLock {
        val hadSession = session != null && _deviceSessionState.value == DeviceSessionState.STARTED
        val hadStream = stream != null && _streamState.value == StreamState.STREAMING
        try {
            withTimeout(timeoutMs) {
                if (!hadSession) awaitSession()
                if (!hadStream) awaitStream()
                awaitPhoto()
            }
        } finally {
            withContext(Dispatchers.Main.immediate) {
                if (!hadStream) stopStreaming()
                if (!hadSession) stopSession()
            }
        }
    }

    private suspend fun awaitSession() {
        suspendCancellableCoroutine<Unit> { continuation ->
            scope.launch {
                startSession(
                    onSuccess = {
                        if (continuation.isActive) continuation.resume(Unit)
                    },
                    onError = { error ->
                        if (continuation.isActive) {
                            continuation.resumeWithException(IllegalStateException(error))
                        }
                    },
                )
            }
        }
    }

    private suspend fun awaitStream() {
        suspendCancellableCoroutine<Unit> { continuation ->
            scope.launch {
                startStreaming(
                    onFrame = {},
                    onSuccess = {
                        if (continuation.isActive) continuation.resume(Unit)
                    },
                    onError = { error ->
                        if (continuation.isActive) {
                            continuation.resumeWithException(IllegalStateException(error))
                        }
                    },
                )
            }
        }
    }

    private suspend fun awaitPhoto(): CapturedPhoto {
        return suspendCancellableCoroutine { continuation ->
            scope.launch {
                capturePhoto(
                    onSuccess = { photo ->
                        if (continuation.isActive) continuation.resume(photo)
                    },
                    onError = { error ->
                        if (continuation.isActive) {
                            continuation.resumeWithException(IllegalStateException(error))
                        }
                    },
                )
            }
        }
    }

    private suspend fun persistPhoto(data: PhotoData): CapturedPhoto = withContext(Dispatchers.IO) {
        val bytes: ByteArray
        val mimeType: String
        val extension: String
        when (data) {
            is PhotoData.Bitmap -> {
                val output = ByteArrayOutputStream()
                check(data.bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)) {
                    "Unable to encode captured photo"
                }
                bytes = output.toByteArray()
                mimeType = "image/jpeg"
                extension = "jpg"
            }
            is PhotoData.HEIC -> {
                val buffer = data.data.duplicate()
                bytes = ByteArray(buffer.remaining()).also(buffer::get)
                mimeType = "image/heic"
                extension = "heic"
            }
        }

        val displayName = "Meta_${System.currentTimeMillis()}.$extension"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_DCIM}/CyanBridge",
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val uri = context.contentResolver.insert(collection, values)
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri).use { output ->
                    checkNotNull(output) { "Unable to open MediaStore output" }
                    output.write(bytes)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    context.contentResolver.update(
                        uri,
                        ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                        null,
                        null,
                    )
                }
            } catch (error: Throwable) {
                context.contentResolver.delete(uri, null, null)
                throw error
            }
        } else {
            Log.w(TAG, "MediaStore did not accept captured photo")
        }
        CapturedPhoto(bytes = bytes, mimeType = mimeType, uri = uri)
    }

    /** Writes a DAT photo in a format accepted by the local and cloud vision pipelines. */
    suspend fun savePhotoForProcessing(photo: CapturedPhoto, namePrefix: String): File =
        withContext(Dispatchers.IO) {
            val outputDirectory = context.getExternalFilesDir(Environment.DIRECTORY_DCIM) ?: context.filesDir
            outputDirectory.mkdirs()
            val safePrefix = namePrefix.replace(Regex("[^A-Za-z0-9_-]"), "_")
            val file = File(outputDirectory, "${safePrefix}_${System.currentTimeMillis()}.jpg")

            if (photo.mimeType.equals("image/heic", ignoreCase = true)) {
                val bitmap = BitmapFactory.decodeByteArray(photo.bytes, 0, photo.bytes.size)
                    ?: error("Unable to decode Meta HEIC photo")
                try {
                    file.outputStream().use { output ->
                        check(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)) {
                            "Unable to encode Meta photo"
                        }
                    }
                } finally {
                    bitmap.recycle()
                }
            } else {
                file.writeBytes(photo.bytes)
            }
            file
        }

    fun startDisplay(onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (!requireInitialized(onError)) return
        if (!_selectedDeviceIsDisplayCapable.value) {
            onError("Selected Meta wearable does not expose a display")
            return
        }
        val currentSession = session
        if (currentSession == null || _deviceSessionState.value != DeviceSessionState.STARTED) {
            onError("Start a Meta device session first")
            return
        }
        if (display != null) {
            if (_isDisplayActive.value) onSuccess()
            return
        }

        displayStartedHandler = onSuccess
        currentSession.addDisplay().fold(
            onSuccess = { newDisplay ->
                display = newDisplay
                displayStateJob?.cancel()
                displayStateJob = scope.launch {
                    newDisplay.state.collect { state ->
                        _isDisplayActive.value = state == DisplayState.STARTED
                        if (state == DisplayState.STARTED) {
                            displayStartedHandler?.invoke()
                            displayStartedHandler = null
                        }
                        if (state == DisplayState.STOPPED || state == DisplayState.CLOSED) {
                            stopDisplayInternal()
                        }
                    }
                }
            },
            onFailure = { error, _ ->
                displayStartedHandler = null
                onError(error.description)
            },
        )
    }

    fun stopDisplay() {
        stopDisplayInternal()
    }

    private fun stopDisplayInternal() {
        displayStateJob?.cancel()
        displayStateJob = null
        displayStartedHandler = null
        display?.stop()
        display = null
        _isDisplayActive.value = false
    }

    private fun requireInitialized(onError: ((String) -> Unit)? = null): Boolean {
        if (_isInitialized.value) return true
        val message = "Meta Wearables DAT is not initialized"
        fail(message)
        onError?.invoke(message)
        return false
    }

    private fun clearError() {
        _lastError.value = null
    }

    private fun releaseMetaCameraLease() {
        metaCameraLease?.let { lease -> GlassesSessionCoordinator.release(lease) }
        metaCameraLease = null
    }

    private fun fail(message: String) {
        _lastError.value = message
        Log.e(TAG, message)
    }

    fun destroy() {
        stopSession()
        registrationJob?.cancel()
        registrationErrorJob?.cancel()
        devicesJob?.cancel()
        deviceMetadataJobs.values.forEach(Job::cancel)
        deviceMetadataJobs.clear()
        releaseMetaCameraLease()
        scope.coroutineContext[Job]?.cancel()
        instance = null
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

    private fun DatRegistrationState.toManagerState(): RegistrationState = when (this) {
        DatRegistrationState.UNAVAILABLE -> RegistrationState.UNAVAILABLE
        DatRegistrationState.AVAILABLE -> RegistrationState.AVAILABLE
        DatRegistrationState.REGISTERED -> RegistrationState.REGISTERED
        DatRegistrationState.REGISTERING -> RegistrationState.REGISTERING
        DatRegistrationState.UNREGISTERING -> RegistrationState.UNREGISTERING
    }

    private fun DatDeviceSessionState.toManagerState(): DeviceSessionState = when (this) {
        DatDeviceSessionState.IDLE -> DeviceSessionState.IDLE
        DatDeviceSessionState.STARTING -> DeviceSessionState.STARTING
        DatDeviceSessionState.STARTED -> DeviceSessionState.STARTED
        DatDeviceSessionState.PAUSED -> DeviceSessionState.PAUSED
        DatDeviceSessionState.STOPPING -> DeviceSessionState.STOPPING
        DatDeviceSessionState.STOPPED -> DeviceSessionState.STOPPED
    }

    private fun DatStreamState.toManagerState(): StreamState = when (this) {
        DatStreamState.STARTING -> StreamState.STARTING
        DatStreamState.STARTED -> StreamState.STARTED
        DatStreamState.STREAMING -> StreamState.STREAMING
        DatStreamState.STOPPING -> StreamState.STOPPING
        DatStreamState.STOPPED -> StreamState.STOPPED
        DatStreamState.PAUSED -> StreamState.PAUSED
        DatStreamState.CLOSED -> StreamState.CLOSED
    }
}

/** Converts the DAT camera's raw I420 frames into Android bitmaps for shared consumers. */
private object YuvToBitmapConverter {
    fun convert(frame: VideoFrame): Bitmap? {
        val width = frame.width
        val height = frame.height
        if (width <= 0 || height <= 0 || width % 2 != 0 || height % 2 != 0) return null

        val frameSize = width * height
        val expectedSize = frameSize + frameSize / 2
        if (frame.buffer.remaining() < expectedSize) return null

        val bytes = ByteArray(expectedSize)
        val source = frame.buffer.duplicate()
        source.get(bytes)
        val uOffset = frameSize
        val vOffset = frameSize + frameSize / 4
        val pixels = IntArray(frameSize)

        for (row in 0 until height) {
            val uvRowOffset = (row / 2) * (width / 2)
            for (column in 0 until width) {
                val pixelIndex = row * width + column
                val uvIndex = uvRowOffset + column / 2
                val y = (bytes[pixelIndex].toInt() and 0xff) - 16
                val u = (bytes[uOffset + uvIndex].toInt() and 0xff) - 128
                val v = (bytes[vOffset + uvIndex].toInt() and 0xff) - 128
                val yScaled = 1192 * y
                val red = ((yScaled + 1836 * v) shr 10).coerceIn(0, 255)
                val green = ((yScaled - 218 * u - 546 * v) shr 10).coerceIn(0, 255)
                val blue = ((yScaled + 2163 * u) shr 10).coerceIn(0, 255)
                pixels[pixelIndex] = (0xff shl 24) or (red shl 16) or (green shl 8) or blue
            }
        }
        return runCatching { Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888) }.getOrNull()
    }
}

package com.ad_glasses.ai.transcription.moonshine

import ai.moonshine.voice.Transcriber
import ai.moonshine.voice.TranscriptEvent
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AutomaticGainControl
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.io.IOException
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * SpeechRecognizer-compatible bridge backed exclusively by the vendored Moonshine runtime.
 *
 * Microphone capture starts before a cold Moonshine model load. This guarantees that the first Ask
 * utterance is buffered instead of disappearing while the model is warming. One engine thread still
 * owns every native stream operation (create/start/add/stop/free).
 */
class MoonshineRecognitionService : RecognitionService() {
    private val setupWorker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "moonshine-recognition-setup").apply { isDaemon = true }
    }
    private val generation = AtomicLong(0L)

    @Volatile
    private var activeSession: Session? = null

    private class Session(
        val id: Long,
        val callback: Callback,
    ) {
        val cancelled = AtomicBoolean(false)
        val captureEnded = AtomicBoolean(false)
        val terminalRequested = AtomicBoolean(false)
        val callbackDelivered = AtomicBoolean(false)
        val engineDone = CountDownLatch(1)
        val audioQueue = LinkedBlockingQueue<FloatArray>(MAX_QUEUED_CHUNKS)
        val finalText = AtomicReference<String?>(null)
        val failure = AtomicReference<Throwable?>(null)

        @Volatile var transcriber: Transcriber? = null
        @Volatile var transcriptListener: Consumer<TranscriptEvent>? = null
        @Volatile var streamHandle: Int = -1
        @Volatile var audioRecord: AudioRecord? = null
        @Volatile var automaticGainControl: AutomaticGainControl? = null
        @Volatile var softwareGain: Float = 1.0f
        @Volatile var captureThread: Thread? = null
        @Volatile var engineThread: Thread? = null
        @Volatile var beganSpeech: Boolean = false
        @Volatile var captureStartedAtMs: Long = 0L
        @Volatile var lastVoiceAtMs: Long = 0L
        @Volatile var noiseFloorDb: Float = INITIAL_NOISE_FLOOR_DBFS
        @Volatile var maxRmsDb: Float = -120f
        @Volatile var consecutiveVoiceFrames: Int = 0
        @Volatile var capturedSamples: Long = 0L
        @Volatile var maxQueueDepth: Int = 0
    }

    private data class CaptureDevice(
        val record: AudioRecord,
        val preferredInput: AudioDeviceInfo?,
        val audioSource: Int,
    )

    override fun onStartListening(recognizerIntent: Intent, listener: Callback) {
        val previous = activeSession
        previous?.let { cancelSession(it, "superseded") }

        val session = Session(generation.incrementAndGet(), listener)
        activeSession = session

        setupWorker.execute {
            try {
                if (previous != null && !previous.engineDone.await(PREVIOUS_ENGINE_WAIT_MS, TimeUnit.MILLISECONDS)) {
                    throw IllegalStateException("Moonshine is still stopping the previous voice request")
                }
                if (!isCurrent(session) || session.cancelled.get()) {
                    session.engineDone.countDown()
                    return@execute
                }

                val requestedLanguageTag = recognizerIntent
                    .getStringExtra(RecognizerIntent.EXTRA_LANGUAGE)
                    ?.trim()
                    .orEmpty()
                    .ifBlank { Locale.getDefault().toLanguageTag() }

                // Capture first. A cold model can take noticeable time to load, but the user's first
                // utterance must already be in the queue while that happens.
                val capture = createAudioRecord()
                session.audioRecord = capture.record
                configureInputGain(session, capture.record)
                capture.record.startRecording()
                check(capture.record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    "Moonshine microphone did not enter the recording state"
                }
                session.captureStartedAtMs = SystemClock.elapsedRealtime()
                listener.readyForSpeech(Bundle.EMPTY)
                startCapture(session)
                Log.i(
                    TAG,
                    "stage=asr_ready engine=moonshine inputRate=$TARGET_SAMPLE_RATE " +
                        "source=${capture.audioSource} preferredInput=${capture.preferredInput?.type ?: -1} " +
                        "softwareGain=${session.softwareGain}",
                )

                val model = MoonshineModelManager.chooseDefault(requestedLanguageTag)
                val modelLoadStartedAt = SystemClock.elapsedRealtime()
                val modelDir = MoonshineModelManager.prepareForRuntime(applicationContext, model)
                val transcriber = MoonshineRuntime.acquire(model.id) {
                    Transcriber().apply {
                        loadFromFiles(modelDir.absolutePath, model.modelArch)
                    }
                }
                session.transcriber = transcriber
                Log.i(
                    TAG,
                    "stage=asr_model_ready engine=moonshine model=${model.id} " +
                        "loadMs=${SystemClock.elapsedRealtime() - modelLoadStartedAt} " +
                        "bufferedMs=${session.capturedSamples * 1000L / TARGET_SAMPLE_RATE}",
                )

                if (!isCurrent(session) || session.cancelled.get()) {
                    stopCapture(session)
                    session.engineDone.countDown()
                    return@execute
                }

                startEngine(session)
            } catch (error: Throwable) {
                failBeforeEngine(session, error)
            }
        }
    }

    override fun onStopListening(listener: Callback) {
        val session = activeSession?.takeIf { it.callback === listener } ?: return
        if (session.callbackDelivered.get() || session.cancelled.get()) return
        stopCapture(session)
    }

    override fun onCancel(listener: Callback) {
        val session = activeSession?.takeIf { it.callback === listener } ?: return
        cancelSession(session, "client_cancel")
    }

    override fun onDestroy() {
        activeSession?.let { cancelSession(it, "service_destroy") }
        setupWorker.shutdown()
        super.onDestroy()
    }

    private fun startCapture(session: Session) {
        val thread = Thread({ captureLoop(session) }, "moonshine-audio-capture").apply { isDaemon = true }
        session.captureThread = thread
        thread.start()
    }

    private fun startEngine(session: Session) {
        val thread = Thread({ engineLoop(session) }, "moonshine-audio-transcribe").apply { isDaemon = true }
        session.engineThread = thread
        thread.start()
    }

    private fun captureLoop(session: Session) {
        val record = session.audioRecord ?: return
        val buffer = ShortArray(READ_SAMPLES)
        try {
            while (!session.cancelled.get() && !session.captureEnded.get() && !session.terminalRequested.get()) {
                val read = record.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                when {
                    read > 0 -> {
                        session.capturedSamples += read
                        val gain = session.softwareGain
                        val audio = FloatArray(read) { index ->
                            ((buffer[index] / 32768.0f) * gain).coerceIn(-1.0f, 1.0f)
                        }
                        val rmsDb = rmsDb(audio)
                        session.maxRmsDb = max(session.maxRmsDb, rmsDb)
                        runCatching { session.callback.rmsChanged(rmsDb) }

                        if (!session.audioQueue.offer(audio)) {
                            val error = IllegalStateException("Moonshine audio processing could not keep up with the microphone")
                            session.failure.compareAndSet(null, error)
                            session.terminalRequested.set(true)
                            Log.e(TAG, "stage=asr_audio_overrun engine=moonshine queue=${session.audioQueue.size}")
                            requestRecordStop(session)
                            break
                        }
                        session.maxQueueDepth = max(session.maxQueueDepth, session.audioQueue.size)

                        if (shouldEndCapture(session, rmsDb, SystemClock.elapsedRealtime())) {
                            break
                        }
                    }
                    read == 0 -> Unit
                    session.cancelled.get() || session.captureEnded.get() || session.terminalRequested.get() -> Unit
                    else -> throw IOException("AudioRecord.read failed with code $read")
                }
            }
        } catch (error: Throwable) {
            if (!session.cancelled.get() && !session.callbackDelivered.get()) {
                session.failure.compareAndSet(null, error)
                session.terminalRequested.set(true)
                Log.e(TAG, "stage=asr_capture_failed engine=moonshine message=${error.message}", error)
            }
        } finally {
            session.captureEnded.set(true)
            releaseRecord(session)
        }
    }

    /**
     * Audio-level endpointing is independent from Moonshine transcript churn. It therefore closes
     * the mic when the person actually stops talking, even if background noise keeps changing the
     * partial transcript.
     */
    private fun shouldEndCapture(session: Session, rmsDb: Float, nowMs: Long): Boolean {
        val startedAt = session.captureStartedAtMs
        if (startedAt <= 0L) return false
        val elapsedMs = nowMs - startedAt
        val startThreshold = max(VAD_ABSOLUTE_MIN_DBFS, session.noiseFloorDb + VAD_NOISE_MARGIN_DB)

        if (!session.beganSpeech) {
            if (rmsDb >= startThreshold) {
                session.consecutiveVoiceFrames += 1
                if (session.consecutiveVoiceFrames >= VAD_START_FRAMES) {
                    markSpeechStarted(session, nowMs, "audio")
                }
            } else {
                session.consecutiveVoiceFrames = 0
                if (elapsedMs <= NOISE_CALIBRATION_MS || rmsDb < startThreshold - 2f) {
                    session.noiseFloorDb =
                        (session.noiseFloorDb * 0.88f + rmsDb * 0.12f).coerceIn(-90f, -20f)
                }
            }

            if (!session.beganSpeech && elapsedMs >= NO_SPEECH_TIMEOUT_MS) {
                Log.i(
                    TAG,
                    "stage=asr_no_speech_timeout engine=moonshine elapsedMs=$elapsedMs " +
                        "noiseFloorDb=${session.noiseFloorDb} maxRmsDb=${session.maxRmsDb}",
                )
                return true
            }
            return false
        }

        val continueThreshold = startThreshold - VAD_HYSTERESIS_DB
        if (rmsDb >= continueThreshold) {
            session.lastVoiceAtMs = nowMs
        }

        if (elapsedMs >= MAX_UTTERANCE_MS) {
            Log.i(TAG, "stage=asr_max_utterance engine=moonshine elapsedMs=$elapsedMs")
            return true
        }

        val lastVoiceAt = session.lastVoiceAtMs
        if (lastVoiceAt > 0L && nowMs - lastVoiceAt >= END_SILENCE_MS) {
            Log.i(
                TAG,
                "stage=asr_audio_silence_end engine=moonshine silenceMs=${nowMs - lastVoiceAt} " +
                    "noiseFloorDb=${session.noiseFloorDb} maxRmsDb=${session.maxRmsDb}",
            )
            return true
        }
        return false
    }

    private fun markSpeechStarted(session: Session, nowMs: Long, source: String) {
        if (!session.beganSpeech) {
            synchronized(session) {
                if (!session.beganSpeech && !session.cancelled.get()) {
                    session.beganSpeech = true
                    session.lastVoiceAtMs = nowMs
                    runCatching { session.callback.beginningOfSpeech() }
                    Log.i(
                        TAG,
                        "stage=asr_speech_started engine=moonshine source=$source " +
                            "noiseFloorDb=${session.noiseFloorDb} maxRmsDb=${session.maxRmsDb}",
                    )
                }
            }
        } else if (session.lastVoiceAtMs <= 0L) {
            session.lastVoiceAtMs = nowMs
        }
    }

    private fun rmsDb(audio: FloatArray): Float {
        if (audio.isEmpty()) return -120f
        var sumSquares = 0.0
        for (sample in audio) {
            val value = sample.toDouble()
            sumSquares += value * value
        }
        val rms = sqrt(sumSquares / audio.size.toDouble()).coerceAtLeast(RMS_FLOOR)
        return (20.0 * log10(rms)).toFloat()
    }

    /** One thread owns all Moonshine stream/JNI operations for the request. */
    private fun engineLoop(session: Session) {
        val transcriber = session.transcriber
        if (transcriber == null) {
            session.failure.compareAndSet(null, IllegalStateException("Moonshine transcriber is unavailable"))
            session.engineDone.countDown()
            finishSession(session)
            return
        }

        var streamHandle = -1
        var listener: Consumer<TranscriptEvent>? = null
        var streamStarted = false
        try {
            streamHandle = transcriber.createStream()
            session.streamHandle = streamHandle
            listener = Consumer<TranscriptEvent> { event -> onTranscriptEvent(session, event) }
            session.transcriptListener = listener
            transcriber.addListener(listener)
            transcriber.startStream(streamHandle)
            streamStarted = true

            while (!session.cancelled.get() && !session.terminalRequested.get()) {
                val chunk = session.audioQueue.poll(ENGINE_POLL_MS, TimeUnit.MILLISECONDS)
                if (chunk != null) {
                    transcriber.addAudioToStream(streamHandle, chunk, TARGET_SAMPLE_RATE)
                }
                if (session.captureEnded.get() && session.audioQueue.isEmpty()) break
            }

            if (!session.cancelled.get() && !session.terminalRequested.get()) {
                while (true) {
                    val chunk = session.audioQueue.poll() ?: break
                    transcriber.addAudioToStream(streamHandle, chunk, TARGET_SAMPLE_RATE)
                    if (session.terminalRequested.get()) break
                }
            }
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            if (!session.cancelled.get()) session.failure.compareAndSet(null, interrupted)
        } catch (error: Throwable) {
            if (!session.cancelled.get()) {
                session.failure.compareAndSet(null, error)
                Log.e(TAG, "stage=asr_engine_failed engine=moonshine message=${error.message}", error)
            }
        } finally {
            stopCapture(session)

            if (streamStarted && streamHandle >= 0) {
                try {
                    if (session.finalText.get() != null || session.cancelled.get() || session.failure.get() != null) {
                        listener?.let { transcriber.removeListener(it) }
                        session.transcriptListener = null
                        listener = null
                    }
                    // Forced flush produces a trailing LineCompleted after VAD/client stop.
                    transcriber.stopStream(streamHandle)
                } catch (error: Throwable) {
                    if (!session.cancelled.get()) session.failure.compareAndSet(null, error)
                    Log.w(TAG, "stage=asr_stream_stop_failed engine=moonshine", error)
                }

                listener?.let { transcriber.removeListener(it) }
                session.transcriptListener = null
                try {
                    transcriber.freeStream(streamHandle)
                } catch (error: Throwable) {
                    if (!session.cancelled.get()) session.failure.compareAndSet(null, error)
                    Log.w(TAG, "stage=asr_stream_free_failed engine=moonshine", error)
                }
            }
            session.streamHandle = -1
            session.audioQueue.clear()
            session.engineDone.countDown()
            finishSession(session)
        }
    }

    private fun onTranscriptEvent(session: Session, event: TranscriptEvent) {
        if (session.cancelled.get() || session.callbackDelivered.get()) return
        val eventStream = when (event) {
            is TranscriptEvent.LineStarted -> event.streamHandle
            is TranscriptEvent.LineUpdated -> event.streamHandle
            is TranscriptEvent.LineTextChanged -> event.streamHandle
            is TranscriptEvent.LineSpeakersChanged -> event.streamHandle
            is TranscriptEvent.LineCompleted -> event.streamHandle
            is TranscriptEvent.Error -> event.streamHandle
            else -> return
        }
        if (eventStream != session.streamHandle) return

        when (event) {
            is TranscriptEvent.LineTextChanged -> onPartial(session, event.line.text.orEmpty())
            is TranscriptEvent.LineCompleted -> {
                val clean = event.line.text.orEmpty().trim()
                if (clean.isNotBlank() && session.finalText.compareAndSet(null, clean)) {
                    session.terminalRequested.set(true)
                    requestRecordStop(session)
                }
            }
            is TranscriptEvent.Error -> {
                session.failure.compareAndSet(null, event.cause)
                session.terminalRequested.set(true)
                requestRecordStop(session)
            }
        }
    }

    private fun onPartial(session: Session, text: String) {
        val clean = text.trim()
        if (clean.isBlank() || session.cancelled.get() || session.callbackDelivered.get()) return
        markSpeechStarted(session, SystemClock.elapsedRealtime(), "transcript")
        runCatching { session.callback.partialResults(resultBundle(clean)) }
    }

    private fun finishSession(session: Session) {
        stopCapture(session)
        releaseInputAudioRoute()
        if (activeSession === session) activeSession = null

        if (session.cancelled.get() || !session.callbackDelivered.compareAndSet(false, true)) return

        val elapsedAudioMs = session.capturedSamples * 1000L / TARGET_SAMPLE_RATE
        val error = session.failure.get()
        val text = session.finalText.get()?.trim().orEmpty()
        when {
            error != null -> {
                Log.e(TAG, "stage=asr_failed engine=moonshine capturedMs=$elapsedAudioMs message=${error.message}", error)
                runCatching { session.callback.error(mapError(error)) }
            }
            text.isNotBlank() -> {
                if (session.beganSpeech) runCatching { session.callback.endOfSpeech() }
                Log.i(
                    TAG,
                    "stage=asr_final engine=moonshine chars=${text.length} capturedMs=$elapsedAudioMs " +
                        "maxQueue=${session.maxQueueDepth} maxRmsDb=${session.maxRmsDb}",
                )
                runCatching { session.callback.results(resultBundle(text)) }
            }
            else -> {
                Log.i(
                    TAG,
                    "stage=asr_no_match engine=moonshine capturedMs=$elapsedAudioMs maxRmsDb=${session.maxRmsDb}",
                )
                runCatching { session.callback.error(SpeechRecognizer.ERROR_NO_MATCH) }
            }
        }
    }

    private fun failBeforeEngine(session: Session, error: Throwable) {
        session.failure.compareAndSet(null, error)
        stopCapture(session)
        if (session.engineThread == null) session.engineDone.countDown()
        finishSession(session)
    }

    private fun cancelSession(session: Session, reason: String) {
        if (!session.cancelled.compareAndSet(false, true)) return
        session.terminalRequested.set(true)
        stopCapture(session)
        session.engineThread?.interrupt()
        if (activeSession === session) activeSession = null
        Log.i(TAG, "stage=asr_cancelled engine=moonshine reason=$reason")
    }

    private fun stopCapture(session: Session) {
        session.captureEnded.set(true)
        requestRecordStop(session)
        val thread = session.captureThread
        if (thread == null) {
            releaseRecord(session)
            return
        }
        if (thread === Thread.currentThread()) return
        thread.interrupt()
        runCatching { thread.join(CAPTURE_JOIN_MS) }
            .onFailure { if (it is InterruptedException) Thread.currentThread().interrupt() }
        if (!thread.isAlive) {
            releaseRecord(session)
        } else {
            Log.w(TAG, "stage=asr_capture_join_timeout engine=moonshine")
        }
    }

    private fun requestRecordStop(session: Session) {
        val record = synchronized(session) { session.audioRecord } ?: return
        runCatching {
            if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) record.stop()
        }.onFailure { Log.w(TAG, "stage=asr_audio_stop_failed engine=moonshine", it) }
    }

    private fun releaseRecord(session: Session) {
        val agc = synchronized(session) {
            val current = session.automaticGainControl
            session.automaticGainControl = null
            current
        }
        runCatching { agc?.release() }
            .onFailure { Log.w(TAG, "stage=asr_agc_release_failed engine=moonshine", it) }

        val record = synchronized(session) {
            val current = session.audioRecord ?: return
            session.audioRecord = null
            current
        }
        runCatching { record.release() }
            .onFailure { Log.w(TAG, "stage=asr_audio_release_failed engine=moonshine", it) }
    }

    private fun configureInputGain(session: Session, record: AudioRecord) {
        val agc = if (AutomaticGainControl.isAvailable()) {
            runCatching { AutomaticGainControl.create(record.audioSessionId) }.getOrNull()
        } else {
            null
        }
        val agcEnabled = if (agc != null) {
            runCatching {
                agc.enabled = true
                agc.enabled
            }.getOrDefault(false)
        } else {
            false
        }
        session.automaticGainControl = agc
        session.softwareGain = if (agcEnabled) 1.0f else SOFTWARE_GAIN_FALLBACK
        Log.i(
            TAG,
            "stage=asr_input_gain engine=moonshine agcAvailable=${AutomaticGainControl.isAvailable()} " +
                "agcEnabled=$agcEnabled softwareGain=${session.softwareGain}",
        )
    }

    private fun createAudioRecord(): CaptureDevice {
        val minBuffer = AudioRecord.getMinBufferSize(
            TARGET_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        check(minBuffer > 0) { "16 kHz microphone input is unavailable for Moonshine" }

        val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val preferredInput = audioManager?.getDevices(AudioManager.GET_DEVICES_INPUTS)
            ?.firstOrNull(::isBluetoothInput)

        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(TARGET_SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()
        val bufferBytes = max(minBuffer * 4, TARGET_SAMPLE_RATE * 2)

        fun build(source: Int): AudioRecord = AudioRecord.Builder()
            .setAudioSource(source)
            .setAudioFormat(format)
            .setBufferSizeInBytes(bufferBytes)
            .build()

        var source = MediaRecorder.AudioSource.VOICE_RECOGNITION
        val record = runCatching { build(source) }.getOrElse { voiceError ->
            Log.w(TAG, "VOICE_RECOGNITION source failed; falling back to MIC", voiceError)
            source = MediaRecorder.AudioSource.MIC
            build(source)
        }
        check(record.state == AudioRecord.STATE_INITIALIZED) {
            "Moonshine 16 kHz microphone failed to initialize"
        }

        if (preferredInput != null) {
            val preferred = runCatching { record.setPreferredDevice(preferredInput) }.getOrDefault(false)
            Log.i(
                TAG,
                "stage=asr_input_device engine=moonshine type=${preferredInput.type} preferred=$preferred " +
                    "source=$source rate=$TARGET_SAMPLE_RATE",
            )
        } else {
            Log.i(TAG, "stage=asr_input_device engine=moonshine type=default source=$source rate=$TARGET_SAMPLE_RATE")
        }
        return CaptureDevice(record, preferredInput, source)
    }

    private fun isBluetoothInput(device: AudioDeviceInfo): Boolean =
        device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && device.type == AudioDeviceInfo.TYPE_BLE_HEADSET)

    @Suppress("DEPRECATION")
    private fun releaseInputAudioRoute() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            } else {
                audioManager.isBluetoothScoOn = false
                audioManager.stopBluetoothSco()
            }
            audioManager.mode = AudioManager.MODE_NORMAL
            Log.i(TAG, "stage=asr_input_route_released engine=moonshine")
        }.onFailure { error ->
            Log.w(TAG, "stage=asr_input_route_release_failed engine=moonshine", error)
        }
    }

    private fun isCurrent(session: Session): Boolean =
        activeSession === session && generation.get() == session.id

    private fun resultBundle(text: String): Bundle = Bundle().apply {
        putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(text))
    }

    private fun mapError(error: Throwable): Int {
        val message = generateSequence(error) { it.cause }
            .joinToString(" ") { it.message.orEmpty() }
            .lowercase(Locale.US)
        return when {
            "permission" in message -> SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS
            "microphone" in message || "audio" in message || "record" in message || "16 khz" in message ->
                SpeechRecognizer.ERROR_AUDIO
            "model" in message || "moonshine" in message -> SpeechRecognizer.ERROR_CLIENT
            else -> SpeechRecognizer.ERROR_CLIENT
        }
    }

    private object MoonshineRuntime {
        private val lock = Any()
        private var loadedModelId: String? = null
        private var transcriber: Transcriber? = null

        fun acquire(modelId: String, loader: () -> Transcriber): Transcriber = synchronized(lock) {
            val existing = transcriber
            if (existing != null && loadedModelId == modelId && existing.isLoaded) return@synchronized existing

            runCatching { existing?.close() }
            val loaded = loader()
            transcriber = loaded
            loadedModelId = modelId
            loaded
        }
    }

    private companion object {
        const val TAG = "AssistantTiming"
        const val TARGET_SAMPLE_RATE = 16_000
        const val READ_SAMPLES = 800 // 50 ms at 16 kHz.
        const val MAX_QUEUED_CHUNKS = 600 // 30 seconds, enough to cover a cold model load.
        const val ENGINE_POLL_MS = 40L
        const val CAPTURE_JOIN_MS = 750L
        const val PREVIOUS_ENGINE_WAIT_MS = 2_500L

        const val INITIAL_NOISE_FLOOR_DBFS = -72f
        const val VAD_ABSOLUTE_MIN_DBFS = -62f
        const val VAD_NOISE_MARGIN_DB = 7f
        const val VAD_HYSTERESIS_DB = 3f
        const val VAD_START_FRAMES = 2
        const val NOISE_CALIBRATION_MS = 500L
        const val END_SILENCE_MS = 900L
        const val NO_SPEECH_TIMEOUT_MS = 6_000L
        const val MAX_UTTERANCE_MS = 15_000L
        const val SOFTWARE_GAIN_FALLBACK = 2.0f
        const val RMS_FLOOR = 1e-6
    }
}

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
import kotlin.math.max

/**
 * SpeechRecognizer-compatible bridge backed exclusively by the vendored Moonshine runtime.
 *
 * There is deliberately no Android/system ASR fallback. AD owns microphone routing/capture so a
 * device audio failure becomes a normal recognition error, while Moonshine owns transcription.
 * The critical invariant is that one engine thread owns every native stream operation for a
 * request (create/start/add/stop/free). Recognition callbacks are delivered only after that native
 * stream has been stopped and freed, so destroying SpeechRecognizer cannot race Moonshine JNI.
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
        @Volatile var captureThread: Thread? = null
        @Volatile var engineThread: Thread? = null
        @Volatile var engineReadyAtMs: Long = 0L
        @Volatile var beganSpeech: Boolean = false
        @Volatile var lastTranscriptChangeAtMs: Long = 0L
        @Volatile var capturedSamples: Long = 0L
        @Volatile var maxQueueDepth: Int = 0
    }

    private data class CaptureDevice(
        val record: AudioRecord,
        val preferredInput: AudioDeviceInfo?,
    )

    override fun onStartListening(recognizerIntent: Intent, listener: Callback) {
        val previous = activeSession
        previous?.let { cancelSession(it, "superseded") }

        val session = Session(generation.incrementAndGet(), listener)
        activeSession = session

        setupWorker.execute {
            try {
                // A process-wide Transcriber is cached for latency, so never hand it to a new Ask
                // until the previous request's native stream owner has definitely exited.
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
                val model = MoonshineModelManager.chooseDefault(requestedLanguageTag)
                val modelDir = MoonshineModelManager.prepareForRuntime(applicationContext, model)

                // Start the microphone before a cold native model load. Medium Streaming can take
                // long enough to initialize that an utterance spoken immediately after the listening
                // cue used to happen before AudioRecord existed and was therefore lost completely.
                // Capture into the bounded queue while Moonshine warms; the engine drains it once the
                // native model is ready.
                val capture = createAudioRecord()
                session.audioRecord = capture.record
                capture.record.startRecording()
                check(capture.record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    "Moonshine microphone did not enter the recording state"
                }

                if (!isCurrent(session) || session.cancelled.get()) {
                    stopCapture(session)
                    session.engineDone.countDown()
                    return@execute
                }

                listener.readyForSpeech(Bundle.EMPTY)
                startCapture(session)
                val captureReadyAtMs = SystemClock.elapsedRealtime()
                Log.i(
                    TAG,
                    "stage=asr_capture_ready engine=moonshine model=${model.id} requestedLanguage=$requestedLanguageTag " +
                        "inputRate=$TARGET_SAMPLE_RATE preferredInput=${capture.preferredInput?.type ?: -1}",
                )

                val loadStarted = SystemClock.elapsedRealtime()
                val transcriber = MoonshineRuntime.acquire(model.id) {
                    Transcriber().apply {
                        loadFromFiles(modelDir.absolutePath, model.modelArch)
                    }
                }
                session.transcriber = transcriber

                if (!isCurrent(session) || session.cancelled.get()) {
                    stopCapture(session)
                    session.engineDone.countDown()
                    return@execute
                }

                startEngine(session)
                val bufferedMs = session.capturedSamples * 1000L / TARGET_SAMPLE_RATE
                Log.i(
                    TAG,
                    "stage=asr_engine_start engine=moonshine model=${model.id} " +
                        "modelReadyMs=${SystemClock.elapsedRealtime() - loadStarted} " +
                        "captureToEngineMs=${SystemClock.elapsedRealtime() - captureReadyAtMs} bufferedMs=$bufferedMs",
                )
            } catch (error: Throwable) {
                failBeforeEngine(session, error)
            }
        }
    }

    override fun onStopListening(listener: Callback) {
        val session = activeSession?.takeIf { it.callback === listener } ?: return
        if (session.callbackDelivered.get() || session.cancelled.get()) return
        stopCapture(session)
        // The engine drains already-captured speech, then stopStream() forces Moonshine's trailing
        // transcript. No second thread is allowed to touch the native stream.
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

    /**
     * Keep input identical to Moonshine's Android microphone path: mono PCM16 at 16 kHz from MIC.
     * Android's audio stack is allowed to satisfy that format internally; AD does not run a
     * chunk-resetting resampler in front of the model anymore.
     */
    private fun captureLoop(session: Session) {
        val record = session.audioRecord ?: return
        val buffer = ShortArray(READ_SAMPLES)
        try {
            while (!session.cancelled.get() && !session.captureEnded.get() && !session.terminalRequested.get()) {
                val read = record.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                when {
                    read > 0 -> {
                        session.capturedSamples += read
                        val audio = FloatArray(read) { index -> buffer[index] / 32768.0f }
                        if (!session.audioQueue.offer(audio)) {
                            val error = IllegalStateException("Moonshine audio processing could not keep up with the microphone")
                            session.failure.compareAndSet(null, error)
                            session.terminalRequested.set(true)
                            Log.e(TAG, "stage=asr_audio_overrun engine=moonshine queue=${session.audioQueue.size}")
                            requestRecordStop(session)
                            break
                        }
                        session.maxQueueDepth = max(session.maxQueueDepth, session.audioQueue.size)
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
            session.engineReadyAtMs = SystemClock.elapsedRealtime()
            Log.i(
                TAG,
                "stage=asr_engine_ready engine=moonshine bufferedMs=${session.capturedSamples * 1000L / TARGET_SAMPLE_RATE}",
            )

            while (!session.cancelled.get() && !session.terminalRequested.get()) {
                val chunk = session.audioQueue.poll(ENGINE_POLL_MS, TimeUnit.MILLISECONDS)
                if (chunk != null) {
                    transcriber.addAudioToStream(streamHandle, chunk, TARGET_SAMPLE_RATE)
                }
                if (shouldFinalizeAfterTranscriptSilence(session)) {
                    val now = SystemClock.elapsedRealtime()
                    val idleMs = now - session.lastTranscriptChangeAtMs
                    val capturedMs = session.capturedSamples * 1000L / TARGET_SAMPLE_RATE
                    Log.i(
                        TAG,
                        "stage=asr_silence_timeout engine=moonshine idleMs=$idleMs capturedMs=$capturedMs",
                    )
                    // Do not wait for a second utterance to make Moonshine close the first line.
                    // Stop only microphone capture here; the engine thread remains the sole owner of
                    // the native stream and will drain queued audio before stopStream() forces the
                    // trailing LineCompleted event.
                    stopCapture(session)
                    break
                }
                if (shouldFinalizeWithoutSpeech(session)) {
                    val readyMs = SystemClock.elapsedRealtime() - session.engineReadyAtMs
                    val capturedMs = session.capturedSamples * 1000L / TARGET_SAMPLE_RATE
                    Log.i(
                        TAG,
                        "stage=asr_initial_silence_timeout engine=moonshine readyMs=$readyMs capturedMs=$capturedMs",
                    )
                    stopCapture(session)
                    break
                }
                if (session.captureEnded.get() && session.audioQueue.isEmpty()) break
            }

            // A normal client stop or either silence timeout can arrive before Moonshine emitted a
            // completed line. Drain the queue first, then keep the listener attached while
            // stopStream forces the trailing text.
            if (!session.cancelled.get() && !session.terminalRequested.get()) {
                while (true) {
                    val chunk = session.audioQueue.poll() ?: break
                    transcriber.addAudioToStream(streamHandle, chunk, TARGET_SAMPLE_RATE)
                    if (session.terminalRequested.get()) break
                }
            }
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            if (!session.cancelled.get()) {
                session.failure.compareAndSet(null, interrupted)
            }
        } catch (error: Throwable) {
            if (!session.cancelled.get()) {
                session.failure.compareAndSet(null, error)
                Log.e(TAG, "stage=asr_engine_failed engine=moonshine message=${error.message}", error)
            }
        } finally {
            // Stop capture before final native flush so no producer can keep extending the request.
            stopCapture(session)

            if (streamStarted && streamHandle >= 0) {
                try {
                    // If a line already completed, detach before the forced flush to avoid duplicate
                    // terminal callbacks. Otherwise retain the listener so stopStream can supply the
                    // final trailing line for onStopListening or silence finalization.
                    if (session.finalText.get() != null || session.cancelled.get() || session.failure.get() != null) {
                        listener?.let { transcriber.removeListener(it) }
                        session.transcriptListener = null
                        listener = null
                    }
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

    private fun shouldFinalizeAfterTranscriptSilence(session: Session): Boolean {
        val lastChangeAt = session.lastTranscriptChangeAtMs
        if (!session.beganSpeech || lastChangeAt <= 0L || session.captureEnded.get()) return false
        return SystemClock.elapsedRealtime() - lastChangeAt >= TRANSCRIPT_SILENCE_FINALIZE_MS
    }

    private fun shouldFinalizeWithoutSpeech(session: Session): Boolean {
        val engineReadyAtMs = session.engineReadyAtMs
        if (session.beganSpeech || engineReadyAtMs <= 0L || session.captureEnded.get()) return false
        return SystemClock.elapsedRealtime() - engineReadyAtMs >= INITIAL_NO_SPEECH_TIMEOUT_MS
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
        session.lastTranscriptChangeAtMs = SystemClock.elapsedRealtime()
        if (!session.beganSpeech) {
            synchronized(session) {
                if (!session.beganSpeech && !session.cancelled.get()) {
                    session.beganSpeech = true
                    runCatching { session.callback.beginningOfSpeech() }
                    Log.i(TAG, "stage=asr_speech_started engine=moonshine")
                }
            }
        }
        runCatching { session.callback.partialResults(resultBundle(clean)) }
    }

    /** Deliver terminal callbacks only after native stream stop/free has completed. */
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
                    "stage=asr_final engine=moonshine chars=${text.length} capturedMs=$elapsedAudioMs maxQueue=${session.maxQueueDepth}",
                )
                runCatching { session.callback.results(resultBundle(text)) }
            }
            else -> {
                Log.i(TAG, "stage=asr_no_match engine=moonshine capturedMs=$elapsedAudioMs")
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
        // engineLoop owns native teardown. If setup never started an engine, its queued setup task
        // will observe cancellation and count down engineDone without touching Moonshine JNI.
    }

    /** Stop AudioRecord before release; never race release against a blocking native read. */
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
        val record = synchronized(session) {
            val current = session.audioRecord ?: return
            session.audioRecord = null
            current
        }
        runCatching { record.release() }
            .onFailure { Log.w(TAG, "stage=asr_audio_release_failed engine=moonshine", it) }
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
        val record = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.MIC)
            .setAudioFormat(format)
            .setBufferSizeInBytes(max(minBuffer * 4, TARGET_SAMPLE_RATE * 2))
            .build()
        check(record.state == AudioRecord.STATE_INITIALIZED) {
            "Moonshine 16 kHz microphone failed to initialize"
        }

        if (preferredInput != null) {
            val preferred = runCatching { record.setPreferredDevice(preferredInput) }.getOrDefault(false)
            Log.i(
                TAG,
                "stage=asr_input_device engine=moonshine type=${preferredInput.type} preferred=$preferred rate=$TARGET_SAMPLE_RATE",
            )
        } else {
            Log.i(TAG, "stage=asr_input_device engine=moonshine type=default rate=$TARGET_SAMPLE_RATE")
        }
        return CaptureDevice(record, preferredInput)
    }

    private fun isBluetoothInput(device: AudioDeviceInfo): Boolean =
        device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && device.type == AudioDeviceInfo.TYPE_BLE_HEADSET)

    /** Release the input communication route only after AudioRecord and Moonshine stream teardown. */
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

    /** Process-wide native model cache. Streams remain strictly one-shot and single-owner. */
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
        const val MAX_QUEUED_CHUNKS = 600 // 30 seconds; overflow fails rather than corrupting speech.
        const val ENGINE_POLL_MS = 40L
        const val TRANSCRIPT_SILENCE_FINALIZE_MS = 1_200L
        const val INITIAL_NO_SPEECH_TIMEOUT_MS = 6_000L
        const val CAPTURE_JOIN_MS = 750L
        const val PREVIOUS_ENGINE_WAIT_MS = 2_500L
    }
}

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
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/**
 * SpeechRecognizer-compatible bridge backed exclusively by the vendored Moonshine runtime.
 *
 * AD Glasses deliberately does not fall back to Android/system ASR. We do, however, own the
 * AudioRecord lifecycle instead of using Moonshine's MicTranscriber wrapper. That gives the Ask
 * path one place to validate the Bluetooth microphone, contain AudioRecord failures, keep capture
 * continuous while Moonshine is doing inference, and stop capture before the communication route
 * is released. A microphone/device failure therefore becomes a recognition error instead of an
 * uncaught background-thread exception that can terminate the app process.
 */
class MoonshineRecognitionService : RecognitionService() {
    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "moonshine-recognition-worker").apply { isDaemon = true }
    }
    private val generation = AtomicLong(0L)

    @Volatile
    private var activeSession: Session? = null

    private class Session(
        val id: Long,
        val callback: Callback,
    ) {
        val terminalDelivered = AtomicBoolean(false)
        val captureEnded = AtomicBoolean(false)
        val abortRequested = AtomicBoolean(false)
        val cleanupScheduled = AtomicBoolean(false)
        val audioQueue = LinkedBlockingQueue<FloatArray>(MAX_QUEUED_CHUNKS)

        @Volatile var transcriber: Transcriber? = null
        @Volatile var streamHandle: Int = -1
        @Volatile var transcriptListener: Consumer<TranscriptEvent>? = null
        @Volatile var audioRecord: AudioRecord? = null
        @Volatile var captureThread: Thread? = null
        @Volatile var processingThread: Thread? = null
        @Volatile var inputSampleRate: Int = TARGET_SAMPLE_RATE
        @Volatile var beganSpeech: Boolean = false
    }

    private data class CaptureDevice(
        val record: AudioRecord,
        val sampleRate: Int,
        val preferredInput: AudioDeviceInfo?,
    )

    override fun onStartListening(recognizerIntent: Intent, listener: Callback) {
        val session = Session(generation.incrementAndGet(), listener)
        val previous = activeSession
        activeSession = session
        previous?.let { cancelSession(it, "superseded") }

        worker.execute {
            try {
                if (!isCurrent(session)) return@execute

                val requestedLanguageTag = recognizerIntent
                    .getStringExtra(RecognizerIntent.EXTRA_LANGUAGE)
                    ?.trim()
                    .orEmpty()
                    .ifBlank { Locale.getDefault().toLanguageTag() }
                val model = MoonshineModelManager.chooseDefault(requestedLanguageTag)
                val modelDir = MoonshineModelManager.prepareForRuntime(applicationContext, model)

                val loadStarted = SystemClock.elapsedRealtime()
                val transcriber = MoonshineRuntime.acquire(model.id) {
                    Transcriber().apply {
                        loadFromFiles(modelDir.absolutePath, model.modelArch)
                    }
                }
                session.transcriber = transcriber

                val streamHandle = transcriber.createStream()
                session.streamHandle = streamHandle
                val transcriptListener = Consumer<TranscriptEvent> { event ->
                    onTranscriptEvent(session, event)
                }
                session.transcriptListener = transcriptListener
                transcriber.addListener(transcriptListener)
                transcriber.startStream(streamHandle)

                Log.i(
                    TAG,
                    "stage=asr_model_ready engine=moonshine elapsedMs=${SystemClock.elapsedRealtime() - loadStarted} model=${model.id}",
                )

                if (!isCurrent(session)) {
                    cancelSession(session, "stale_after_model_load")
                    return@execute
                }

                val capture = createAudioRecord()
                session.audioRecord = capture.record
                session.inputSampleRate = capture.sampleRate

                // AudioRecord is fully constructed before we tell SpeechRecognizer clients that we
                // are ready. From this point every start/read/stop failure is caught by AD code.
                capture.record.startRecording()
                check(capture.record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    "Moonshine microphone did not enter the recording state."
                }

                listener.readyForSpeech(Bundle.EMPTY)
                startProcessing(session)
                startCapture(session)

                Log.i(
                    TAG,
                    "stage=asr_ready engine=moonshine language=${model.languageCode} requestedLanguage=$requestedLanguageTag model=${model.id} inputRate=${capture.sampleRate} preferredInput=${capture.preferredInput?.type ?: -1}",
                )
            } catch (error: Throwable) {
                fail(session, error)
            }
        }
    }

    override fun onStopListening(listener: Callback) {
        val session = activeSession?.takeIf { it.callback === listener } ?: return
        if (session.terminalDelivered.get()) return
        stopCapture(session)
        worker.execute {
            flushAndFinish(session)
        }
    }

    override fun onCancel(listener: Callback) {
        val session = activeSession?.takeIf { it.callback === listener } ?: return
        cancelSession(session, "client_cancel")
    }

    override fun onDestroy() {
        val session = activeSession
        activeSession = null
        generation.incrementAndGet()
        if (session != null) cancelSession(session, "service_destroy")
        worker.shutdown()
        super.onDestroy()
    }

    private fun startCapture(session: Session) {
        val thread = Thread({ captureLoop(session) }, "moonshine-audio-capture").apply { isDaemon = true }
        session.captureThread = thread
        thread.start()
    }

    private fun startProcessing(session: Session) {
        val thread = Thread({ processingLoop(session) }, "moonshine-audio-transcribe").apply { isDaemon = true }
        session.processingThread = thread
        thread.start()
    }

    private fun captureLoop(session: Session) {
        val record = session.audioRecord ?: return
        val sampleRate = session.inputSampleRate
        val shortsPerRead = max(MIN_READ_SAMPLES, sampleRate / 20) // ~50 ms at the input rate.
        val buffer = ShortArray(shortsPerRead)
        try {
            while (isCurrent(session) && !session.abortRequested.get() && !session.captureEnded.get()) {
                val read = record.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                when {
                    read > 0 -> {
                        val audio = pcm16ToTargetRate(buffer, read, sampleRate)
                        if (audio.isNotEmpty() && !session.audioQueue.offer(audio)) {
                            // Keeping AudioRecord draining is more important than blocking capture.
                            // Drop the oldest queued chunk, then keep the newest speech.
                            session.audioQueue.poll()
                            session.audioQueue.offer(audio)
                            Log.w(TAG, "stage=asr_audio_queue_overflow engine=moonshine")
                        }
                    }
                    read == 0 -> Unit
                    session.abortRequested.get() || session.captureEnded.get() -> Unit
                    else -> throw IOException("AudioRecord.read failed with code $read")
                }
            }
        } catch (error: Throwable) {
            if (!session.abortRequested.get() && !session.terminalDelivered.get() && isCurrent(session)) {
                fail(session, error)
            }
        } finally {
            session.captureEnded.set(true)
            safeStopAndReleaseRecord(session)
        }
    }

    private fun processingLoop(session: Session) {
        val transcriber = session.transcriber ?: return
        val streamHandle = session.streamHandle
        try {
            while (!session.abortRequested.get() && isCurrent(session)) {
                val chunk = session.audioQueue.poll(PROCESS_POLL_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
                if (chunk != null) {
                    transcriber.addAudioToStream(streamHandle, chunk, TARGET_SAMPLE_RATE)
                }
                if (session.captureEnded.get() && session.audioQueue.isEmpty()) break
            }
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (error: Throwable) {
            if (!session.abortRequested.get() && !session.terminalDelivered.get() && isCurrent(session)) {
                fail(session, error)
            }
        }
    }

    private fun onTranscriptEvent(session: Session, event: TranscriptEvent) {
        if (!isCurrent(session) || session.terminalDelivered.get()) return
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
            is TranscriptEvent.LineCompleted -> finishWithResult(session, event.line.text.orEmpty())
            is TranscriptEvent.Error -> fail(session, event.cause)
        }
    }

    private fun onPartial(session: Session, text: String) {
        val clean = text.trim()
        if (clean.isBlank() || !isCurrent(session) || session.terminalDelivered.get()) return
        if (!session.beganSpeech) {
            synchronized(session) {
                if (!session.beganSpeech && isCurrent(session)) {
                    session.beganSpeech = true
                    session.callback.beginningOfSpeech()
                    Log.i(TAG, "stage=asr_speech_started engine=moonshine")
                }
            }
        }
        if (isCurrent(session) && !session.terminalDelivered.get()) {
            session.callback.partialResults(resultBundle(clean))
        }
    }

    private fun finishWithResult(session: Session, text: String) {
        val clean = text.trim()
        if (clean.isBlank()) return
        if (!isCurrent(session) || !session.terminalDelivered.compareAndSet(false, true)) return

        // Stop the microphone first. Only after AudioRecord has been told to stop do we release the
        // Bluetooth communication route. This ordering avoids the route-teardown race that could
        // crash inside Moonshine's upstream microphone wrapper.
        session.abortRequested.set(true)
        session.audioQueue.clear()
        stopCapture(session)
        if (session.beganSpeech) session.callback.endOfSpeech()
        releaseInputAudioRoute()
        session.callback.results(resultBundle(clean))
        Log.i(TAG, "stage=asr_final engine=moonshine chars=${clean.length}")
        scheduleCleanup(session)
    }

    private fun flushAndFinish(session: Session) {
        if (!isCurrent(session) || session.terminalDelivered.get()) return
        try {
            joinThread(session.captureThread, CAPTURE_JOIN_MS)
            joinThread(session.processingThread, PROCESS_JOIN_MS)
            if (!isCurrent(session) || session.terminalDelivered.get()) return
            val transcriber = session.transcriber ?: throw IllegalStateException("Moonshine transcriber is unavailable")
            transcriber.stopStream(session.streamHandle)
            if (!session.terminalDelivered.get() && isCurrent(session)) {
                finishWithError(session, SpeechRecognizer.ERROR_NO_MATCH, "stage=asr_no_match engine=moonshine")
            }
        } catch (error: Throwable) {
            fail(session, error)
        }
    }

    private fun fail(session: Session, error: Throwable) {
        if (!isCurrent(session) || session.terminalDelivered.get()) return
        Log.e(TAG, "stage=asr_failed engine=moonshine message=${error.message}", error)
        finishWithError(session, mapError(error), null)
    }

    private fun finishWithError(session: Session, errorCode: Int, logLine: String?) {
        if (!isCurrent(session) || !session.terminalDelivered.compareAndSet(false, true)) return
        logLine?.let { Log.i(TAG, it) }
        session.abortRequested.set(true)
        session.audioQueue.clear()
        stopCapture(session)
        releaseInputAudioRoute()
        session.callback.error(errorCode)
        scheduleCleanup(session)
    }

    private fun cancelSession(session: Session, reason: String) {
        if (activeSession === session) activeSession = null
        generation.incrementAndGet()
        session.abortRequested.set(true)
        session.audioQueue.clear()
        stopCapture(session)
        Log.i(TAG, "stage=asr_cancelled engine=moonshine reason=$reason")
        scheduleCleanup(session)
    }

    private fun stopCapture(session: Session) {
        session.captureEnded.set(true)
        safeStopAndReleaseRecord(session)
        val captureThread = session.captureThread
        if (captureThread != null && captureThread !== Thread.currentThread()) {
            captureThread.interrupt()
            joinThread(captureThread, CAPTURE_JOIN_MS)
        }
    }

    private fun safeStopAndReleaseRecord(session: Session) {
        val record = session.audioRecord ?: return
        session.audioRecord = null
        runCatching {
            if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) record.stop()
        }.onFailure { Log.w(TAG, "stage=asr_audio_stop_failed engine=moonshine", it) }
        runCatching { record.release() }
            .onFailure { Log.w(TAG, "stage=asr_audio_release_failed engine=moonshine", it) }
    }

    private fun scheduleCleanup(session: Session) {
        if (!session.cleanupScheduled.compareAndSet(false, true)) return
        worker.execute {
            cleanupSession(session)
        }
    }

    private fun cleanupSession(session: Session) {
        session.abortRequested.set(true)
        session.captureEnded.set(true)
        session.processingThread?.interrupt()
        joinThread(session.captureThread, CAPTURE_JOIN_MS)
        joinThread(session.processingThread, PROCESS_JOIN_MS)

        val transcriber = session.transcriber
        val listener = session.transcriptListener
        if (transcriber != null && listener != null) {
            runCatching { transcriber.removeListener(listener) }
        }
        if (transcriber != null && session.streamHandle >= 0) {
            runCatching { transcriber.stopStream(session.streamHandle) }
            runCatching { transcriber.freeStream(session.streamHandle) }
        }
        session.streamHandle = -1
        session.transcriptListener = null
        session.transcriber = null
        if (activeSession === session) activeSession = null
    }

    private fun createAudioRecord(): CaptureDevice {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val preferredInput = audioManager?.getDevices(AudioManager.GET_DEVICES_INPUTS)
            ?.firstOrNull { device -> isBluetoothInput(device) }

        var lastError: Throwable? = null
        for (sampleRate in INPUT_SAMPLE_RATES) {
            val minBuffer = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            if (minBuffer <= 0) continue

            var record: AudioRecord? = null
            try {
                val format = AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build()
                record = AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(max(minBuffer * 4, sampleRate * 2))
                    .build()
                check(record.state == AudioRecord.STATE_INITIALIZED) {
                    "AudioRecord failed to initialize at ${sampleRate}Hz"
                }
                if (preferredInput != null) {
                    val preferred = runCatching { record.setPreferredDevice(preferredInput) }.getOrDefault(false)
                    Log.i(
                        TAG,
                        "stage=asr_input_device engine=moonshine type=${preferredInput.type} preferred=$preferred rate=$sampleRate",
                    )
                }
                return CaptureDevice(record, sampleRate, preferredInput)
            } catch (error: Throwable) {
                lastError = error
                runCatching { record?.release() }
                Log.w(TAG, "stage=asr_audio_config_rejected engine=moonshine rate=$sampleRate", error)
            }
        }
        throw IllegalStateException("No usable microphone configuration for Moonshine", lastError)
    }

    private fun pcm16ToTargetRate(input: ShortArray, count: Int, inputRate: Int): FloatArray {
        if (count <= 0) return FloatArray(0)
        if (inputRate == TARGET_SAMPLE_RATE) {
            return FloatArray(count) { index -> input[index] / 32768.0f }
        }

        val outputCount = max(1, ceil(count.toDouble() * TARGET_SAMPLE_RATE / inputRate).toInt())
        val scale = inputRate.toDouble() / TARGET_SAMPLE_RATE
        return FloatArray(outputCount) { outputIndex ->
            val sourcePosition = outputIndex * scale
            val left = floor(sourcePosition).toInt().coerceIn(0, count - 1)
            val right = (left + 1).coerceAtMost(count - 1)
            val fraction = (sourcePosition - left).toFloat()
            val leftValue = input[left] / 32768.0f
            val rightValue = input[right] / 32768.0f
            leftValue + (rightValue - leftValue) * fraction
        }
    }

    private fun isBluetoothInput(device: AudioDeviceInfo): Boolean =
        device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && device.type == AudioDeviceInfo.TYPE_BLE_HEADSET)

    /**
     * MainActivity selects the glasses communication route before recognition. Release it only after
     * AudioRecord has stopped so the eventual Cloud AI/TTS phase starts from a clean audio state.
     */
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

    private fun joinThread(thread: Thread?, timeoutMs: Long) {
        if (thread == null || thread === Thread.currentThread()) return
        runCatching { thread.join(timeoutMs) }
            .onFailure { if (it is InterruptedException) Thread.currentThread().interrupt() }
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
            "microphone" in message || "audio" in message || "record" in message -> SpeechRecognizer.ERROR_AUDIO
            "model" in message || "moonshine" in message -> SpeechRecognizer.ERROR_CLIENT
            else -> SpeechRecognizer.ERROR_CLIENT
        }
    }

    /** Process-wide native model cache. Streams are still one-shot per Ask request. */
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
        const val MIN_READ_SAMPLES = 512
        const val MAX_QUEUED_CHUNKS = 160
        const val PROCESS_POLL_MS = 80L
        const val CAPTURE_JOIN_MS = 500L
        const val PROCESS_JOIN_MS = 1_500L
        val INPUT_SAMPLE_RATES = intArrayOf(16_000, 48_000, 44_100, 8_000)
    }
}

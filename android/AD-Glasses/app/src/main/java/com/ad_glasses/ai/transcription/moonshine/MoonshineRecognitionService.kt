package com.ad_glasses.ai.transcription.moonshine

import ai.moonshine.voice.MicTranscriber
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/**
 * SpeechRecognizer-compatible bridge backed exclusively by the vendored Moonshine runtime.
 *
 * There is intentionally no platform recognizer fallback here. Ask AI either runs the installed
 * Moonshine streaming model or reports a recognition error to the caller.
 */
class MoonshineRecognitionService : RecognitionService() {
    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "moonshine-recognition-service").apply { isDaemon = true }
    }
    private val generation = AtomicLong(0L)

    @Volatile
    private var activeSession: Session? = null

    private class Session(
        val id: Long,
        val callback: Callback,
    ) {
        @Volatile var transcriber: MicTranscriber? = null
        @Volatile var beganSpeech: Boolean = false
        @Volatile var deliveredResult: Boolean = false
    }

    override fun onStartListening(recognizerIntent: Intent, listener: Callback) {
        val session = Session(generation.incrementAndGet(), listener)
        val previous = activeSession
        activeSession = session
        previous?.let(::closeAsync)

        worker.execute {
            try {
                if (!isCurrent(session)) return@execute

                val requestedLanguageTag = recognizerIntent
                    .getStringExtra(RecognizerIntent.EXTRA_LANGUAGE)
                    ?.trim()
                    .orEmpty()
                    .ifBlank { Locale.getDefault().toLanguageTag() }
                val model = MoonshineModelManager.chooseDefault(requestedLanguageTag)
                MoonshineModelManager.prepareForRuntime(applicationContext, model)

                // The installed model is explicitly English. The device/recognizer locale is only
                // a request hint; passing a different language into MicTranscriber changes the
                // ModelSpec cache key and would make Moonshine look for a model we do not ship.
                val transcriber = MicTranscriber(applicationContext)
                    .language(model.languageCode)
                    .modelArch(model.modelArch)
                    .modelsFrom(MoonshineModelManager.modelRoot(applicationContext))
                    .callbacksOnMainThread(false)
                    .onText { text -> onPartial(session, text.orEmpty()) }
                    .onLine { line -> onFinal(session, line.text.orEmpty()) }
                    .onError { error -> fail(session, error) }
                session.transcriber = transcriber

                val loadStarted = System.currentTimeMillis()
                transcriber.load()
                Log.i(
                    TAG,
                    "stage=asr_model_loaded engine=moonshine elapsedMs=${System.currentTimeMillis() - loadStarted} model=${model.id}",
                )
                if (!isCurrent(session)) {
                    closeAsync(session)
                    return@execute
                }

                transcriber.start()
                if (!isCurrent(session)) {
                    closeAsync(session)
                    return@execute
                }
                Log.i(
                    TAG,
                    "stage=asr_ready engine=moonshine language=${model.languageCode} requestedLanguage=$requestedLanguageTag model=${model.id}",
                )
                listener.readyForSpeech(Bundle.EMPTY)
            } catch (error: Throwable) {
                fail(session, error)
            }
        }
    }

    override fun onStopListening(listener: Callback) {
        val session = activeSession?.takeIf { it.callback === listener } ?: return
        worker.execute {
            runCatching { session.transcriber?.stop() }
                .onFailure { fail(session, it) }
            // Moonshine flushes the trailing line asynchronously after stop(). If there was no
            // speech/result, finish this one-shot recognition instead of leaving the client open.
            runCatching { Thread.sleep(STOP_FLUSH_GRACE_MS) }
            if (isCurrent(session) && !session.deliveredResult) {
                finishWithError(session, SpeechRecognizer.ERROR_NO_MATCH, "stage=asr_no_match engine=moonshine")
            }
        }
    }

    override fun onCancel(listener: Callback) {
        val session = activeSession?.takeIf { it.callback === listener } ?: return
        if (activeSession === session) activeSession = null
        generation.incrementAndGet()
        Log.i(TAG, "stage=asr_cancelled engine=moonshine")
        closeAsync(session)
    }

    override fun onDestroy() {
        val session = activeSession
        activeSession = null
        generation.incrementAndGet()
        if (session != null) {
            runCatching { session.transcriber?.stop() }
            runCatching { session.transcriber?.close() }
        }
        worker.shutdownNow()
        super.onDestroy()
    }

    private fun onPartial(session: Session, text: String) {
        val clean = text.trim()
        if (clean.isBlank() || !isCurrent(session) || session.deliveredResult) return
        if (!session.beganSpeech) {
            synchronized(session) {
                if (!session.beganSpeech && isCurrent(session)) {
                    session.beganSpeech = true
                    session.callback.beginningOfSpeech()
                    Log.i(TAG, "stage=asr_speech_started engine=moonshine")
                }
            }
        }
        if (isCurrent(session) && !session.deliveredResult) {
            session.callback.partialResults(resultBundle(clean))
        }
    }

    private fun onFinal(session: Session, text: String) {
        val clean = text.trim()
        if (clean.isBlank() || !isCurrent(session)) return
        val shouldDeliver = synchronized(session) {
            if (session.deliveredResult || !isCurrent(session)) false
            else {
                session.deliveredResult = true
                true
            }
        }
        if (!shouldDeliver) return

        if (session.beganSpeech) session.callback.endOfSpeech()
        session.callback.results(resultBundle(clean))
        Log.i(TAG, "stage=asr_final engine=moonshine chars=${clean.length}")
        complete(session)
    }

    private fun fail(session: Session, error: Throwable) {
        Log.e(TAG, "stage=asr_failed engine=moonshine message=${error.message}", error)
        finishWithError(session, mapError(error), null)
    }

    private fun finishWithError(session: Session, errorCode: Int, logLine: String?) {
        if (!isCurrent(session)) return
        val shouldDeliver = synchronized(session) {
            if (session.deliveredResult || !isCurrent(session)) false
            else {
                session.deliveredResult = true
                true
            }
        }
        if (!shouldDeliver) return
        logLine?.let { Log.i(TAG, it) }
        session.callback.error(errorCode)
        complete(session)
    }

    private fun complete(session: Session) {
        if (activeSession === session) activeSession = null
        closeAsync(session)
    }

    private fun closeAsync(session: Session) {
        worker.execute {
            val transcriber = session.transcriber ?: return@execute
            session.transcriber = null
            runCatching { transcriber.stop() }
            runCatching { transcriber.close() }
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
            "microphone" in message || "audio" in message -> SpeechRecognizer.ERROR_AUDIO
            else -> SpeechRecognizer.ERROR_CLIENT
        }
    }

    private companion object {
        const val TAG = "AssistantTiming"
        const val STOP_FLUSH_GRACE_MS = 350L
    }
}

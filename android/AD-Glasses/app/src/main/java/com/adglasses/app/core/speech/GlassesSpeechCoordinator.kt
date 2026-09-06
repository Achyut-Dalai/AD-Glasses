package com.adglasses.app.core.speech

import com.adglasses.app.integrations.heycyan.HeyCyanRepository
import com.adglasses.app.integrations.heycyan.HeyCyanVoiceStreamEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Secure access resolved from the existing encrypted Groq AI profile. */
data class GroqSpeechAccess(
    val apiKey: String,
    val baseUrl: String = CloudTranscriptionClient.DEFAULT_BASE_URL,
)

enum class SpeechTranscriptSource {
    Moonshine,
    GroqWhisper,
}

sealed interface GlassesSpeechStatus {
    data object Idle : GlassesSpeechStatus
    data object Listening : GlassesSpeechStatus
    data class Transcribing(val local: Boolean) : GlassesSpeechStatus
    data class Failed(val reason: String) : GlassesSpeechStatus
}

data class GlassesSpeechTranscript(
    val turnId: Long,
    val text: String,
    val source: SpeechTranscriptSource,
)

/**
 * Converts the verified HeyCyan voice lifecycle into Assistant-ready text.
 *
 * The repository gives us one ordered stream: start -> raw 40-byte Opus packets -> end. We decode
 * every packet immediately and keep a bounded PCM copy of the complete turn. Moonshine is the
 * preferred path once its on-device model is ready. While that model is downloading or if local
 * recognition fails, the same captured PCM can fall back to the iOS-compatible Groq Whisper path.
 *
 * Cloud fallback is deliberately concurrent with capture of the next turn, but publication remains
 * strictly turn-ordered. A slow Groq result for turn N can therefore never arrive at Assistant after
 * the already-completed local result for turn N+1.
 *
 * A voice lifecycle that starts while AD is speaking is consumed but intentionally not transcribed.
 * This prevents phone TTS from becoming a self-triggering Assistant/translation feedback loop.
 */
class GlassesSpeechCoordinator(
    private val glasses: HeyCyanRepository,
    private val localSpeech: MoonshineSpeechEngine,
    private val cloudSpeech: CloudTranscriptionClient,
    private val groqAccess: () -> GroqSpeechAccess?,
    private val outputActive: () -> Boolean = { false },
) {
    companion object {
        private const val MAX_BUFFERED_SECONDS = 60
        private const val MAX_BUFFERED_SAMPLES = GlassesOpusDecoder.SAMPLE_RATE * MAX_BUFFERED_SECONDS
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val decoder = GlassesOpusDecoder()
    private var currentTurn: VoiceTurn? = null
    private var nextTurnId = 1L

    private val completionLock = Any()
    private val completedTurns = mutableMapOf<Long, TurnOutcome>()
    private var nextTurnToPublish = 1L
    @Volatile private var latestStartedTurnId = 0L

    private val _status = MutableStateFlow<GlassesSpeechStatus>(GlassesSpeechStatus.Idle)
    val status: StateFlow<GlassesSpeechStatus> = _status.asStateFlow()

    private val _transcripts = MutableSharedFlow<GlassesSpeechTranscript>(extraBufferCapacity = 8)
    val transcripts: SharedFlow<GlassesSpeechTranscript> = _transcripts.asSharedFlow()

    init {
        // Download/load is deliberately decoupled from the first glasses voice turn.
        scope.launch { localSpeech.prewarm() }
        scope.launch {
            glasses.voiceStream.collect { event ->
                when (event) {
                    HeyCyanVoiceStreamEvent.Started -> beginTurn()
                    is HeyCyanVoiceStreamEvent.OpusPacket -> capture(event.bytes)
                    HeyCyanVoiceStreamEvent.Ended -> endTurn()
                }
            }
        }
    }

    private suspend fun beginTurn() {
        currentTurn?.let { interrupted ->
            if (interrupted.localSessionStarted) localSpeech.cancelSession()
            completeTurn(
                interrupted.id,
                if (interrupted.suppressedForOutput) {
                    TurnOutcome.Ignored
                } else {
                    TurnOutcome.Failure("The glasses started a new voice turn before the previous one ended")
                },
            )
        }
        decoder.reset()

        val id = nextTurnId++
        latestStartedTurnId = id
        val suppressForOutput = outputActive()
        val localReady = localSpeech.state.value is LocalSpeechModelState.Ready
        val localStarted = if (!suppressForOutput && localReady) {
            runCatching { localSpeech.startSession() }.isSuccess
        } else {
            false
        }

        currentTurn = VoiceTurn(
            id = id,
            localSessionStarted = localStarted,
            suppressedForOutput = suppressForOutput,
        )
        setStatusForTurn(
            id,
            if (suppressForOutput) GlassesSpeechStatus.Idle else GlassesSpeechStatus.Listening,
        )
    }

    private fun capture(packet: ByteArray) {
        val turn = currentTurn ?: return
        if (turn.suppressedForOutput) return

        val samples = runCatching { decoder.decode(packet) }
            .getOrElse { error ->
                turn.decodeFailure = error.message ?: "Could not decode a glasses audio packet"
                return
            }

        if (turn.localSessionStarted) localSpeech.addPcm16(samples)

        val remaining = MAX_BUFFERED_SAMPLES - turn.bufferedSamples
        if (remaining > 0) {
            val retained = if (samples.size <= remaining) samples else samples.copyOf(remaining)
            turn.pcmChunks += retained
            turn.bufferedSamples += retained.size
        }
    }

    private suspend fun endTurn() {
        val turn = currentTurn ?: return
        currentTurn = null

        if (turn.suppressedForOutput) {
            completeTurn(turn.id, TurnOutcome.Ignored)
            return
        }

        val pcm = flatten(turn)
        if (pcm.isEmpty()) {
            if (turn.localSessionStarted) localSpeech.cancelSession()
            val reason = turn.decodeFailure ?: "The glasses voice turn contained no decodable audio"
            completeTurn(turn.id, TurnOutcome.Failure(reason))
            return
        }

        setStatusForTurn(
            turn.id,
            GlassesSpeechStatus.Transcribing(local = turn.localSessionStarted),
        )
        val localText = if (turn.localSessionStarted) {
            runCatching { localSpeech.finishSession() }.getOrNull()?.trim().orEmpty()
        } else {
            ""
        }

        if (localText.isNotBlank()) {
            completeTurn(
                turn.id,
                TurnOutcome.Success(localText, SpeechTranscriptSource.Moonshine),
            )
            return
        }

        // Cloud work must not block capture of a following glasses voice turn.
        scope.launch { transcribeWithGroq(turn.id, pcm, turn.decodeFailure) }
    }

    private suspend fun transcribeWithGroq(turnId: Long, pcm: ShortArray, decodeFailure: String?) {
        val access = groqAccess()
        if (access == null) {
            val localReason = when (val state = localSpeech.state.value) {
                LocalSpeechModelState.NotLoaded -> "On-device speech is not loaded yet"
                is LocalSpeechModelState.Loading -> state.detail
                is LocalSpeechModelState.Failed -> state.reason
                LocalSpeechModelState.Ready -> "On-device speech returned no transcript"
            }
            val reason = decodeFailure?.let { "$localReason; $it" } ?: localReason
            completeTurn(
                turnId,
                TurnOutcome.Failure("$reason. Add a Groq profile to enable Whisper fallback."),
            )
            return
        }

        val result = runCatching {
            cloudSpeech.transcribe(
                pcm16 = pcm,
                apiKey = access.apiKey,
                baseUrl = access.baseUrl,
            )
        }

        result.onSuccess { text ->
            completeTurn(
                turnId,
                TurnOutcome.Success(text, SpeechTranscriptSource.GroqWhisper),
            )
        }.onFailure { error ->
            completeTurn(
                turnId,
                TurnOutcome.Failure(
                    error.message ?: "Could not transcribe the glasses voice turn",
                ),
            )
        }
    }

    /**
     * Store completion immediately but release outcomes only in protocol turn order. Failures and
     * ignored echo turns are consumed as ordering barriers too, so neither can block later speech.
     */
    private suspend fun completeTurn(turnId: Long, outcome: TurnOutcome) {
        val publishable = synchronized(completionLock) {
            if (turnId < nextTurnToPublish || completedTurns.containsKey(turnId)) {
                return@synchronized emptyList()
            }
            completedTurns[turnId] = outcome
            buildList {
                while (true) {
                    val next = completedTurns.remove(nextTurnToPublish) ?: break
                    add(nextTurnToPublish to next)
                    nextTurnToPublish += 1
                }
            }
        }

        publishable.forEach { (publishedTurnId, publishedOutcome) ->
            when (publishedOutcome) {
                is TurnOutcome.Success -> {
                    _transcripts.emit(
                        GlassesSpeechTranscript(
                            turnId = publishedTurnId,
                            text = publishedOutcome.text,
                            source = publishedOutcome.source,
                        )
                    )
                    setStatusForTurn(publishedTurnId, GlassesSpeechStatus.Idle)
                }
                is TurnOutcome.Failure -> {
                    setStatusForTurn(
                        publishedTurnId,
                        GlassesSpeechStatus.Failed(publishedOutcome.reason),
                    )
                }
                TurnOutcome.Ignored -> {
                    setStatusForTurn(publishedTurnId, GlassesSpeechStatus.Idle)
                }
            }
        }
    }

    private fun setStatusForTurn(turnId: Long, status: GlassesSpeechStatus) {
        if (turnId == latestStartedTurnId) _status.value = status
    }

    private fun flatten(turn: VoiceTurn): ShortArray {
        val output = ShortArray(turn.bufferedSamples)
        var offset = 0
        turn.pcmChunks.forEach { chunk ->
            chunk.copyInto(output, destinationOffset = offset)
            offset += chunk.size
        }
        return output
    }

    private sealed interface TurnOutcome {
        data class Success(
            val text: String,
            val source: SpeechTranscriptSource,
        ) : TurnOutcome

        data class Failure(val reason: String) : TurnOutcome
        data object Ignored : TurnOutcome
    }

    private data class VoiceTurn(
        val id: Long,
        val localSessionStarted: Boolean,
        val suppressedForOutput: Boolean,
        val pcmChunks: MutableList<ShortArray> = mutableListOf(),
        var bufferedSamples: Int = 0,
        var decodeFailure: String? = null,
    )
}

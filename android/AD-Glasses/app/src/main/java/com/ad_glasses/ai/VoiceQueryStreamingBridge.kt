package com.ad_glasses.ai

/**
 * Process-local bridge between the assistant inference layer and the Activity-owned TTS queue.
 *
 * MainActivity installs a sink only while a glasses voice query is active. Keeping this bridge
 * deliberately tiny lets transport code remain UI-agnostic while avoiding a second TextToSpeech
 * engine. If no Activity sink is active, streamed text is simply collected for the normal final
 * response path.
 */
object VoiceQueryStreamingBridge {
    interface Sink {
        fun onProviderDelta(delta: String)
    }

    @Volatile
    private var sink: Sink? = null

    fun install(activeSink: Sink) {
        sink = activeSink
    }

    fun clear(activeSink: Sink) {
        if (sink === activeSink) sink = null
    }

    fun emit(delta: String) {
        sink?.onProviderDelta(delta)
    }

    fun hasSink(): Boolean = sink != null
}

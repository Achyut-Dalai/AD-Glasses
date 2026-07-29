package com.fersaiyan.cyanbridge.ai.live

/** Routes a glasses AI-photo button event to the foreground Gemini Live session, if any. */
object GeminiLiveImageButtonRouter {
    @Volatile
    private var handler: (() -> Unit)? = null

    fun register(activeHandler: () -> Unit) {
        handler = activeHandler
    }

    fun unregister(activeHandler: () -> Unit) {
        if (handler === activeHandler) handler = null
    }

    fun handleImageButton(): Boolean {
        val activeHandler = handler ?: return false
        activeHandler()
        return true
    }
}

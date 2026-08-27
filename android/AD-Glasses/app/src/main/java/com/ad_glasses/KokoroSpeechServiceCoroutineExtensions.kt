package com.ad_glasses

import com.ad_glasses.ai.voice.KokoroSpeechEngine
import com.ad_glasses.ai.voice.KokoroSpeechService
import com.ad_glasses.ui.MyApplication
import kotlinx.coroutines.CoroutineScope

/**
 * `lifecycleScope.launch` uses a [CoroutineScope] receiver, so an unqualified `this` inside those
 * callbacks is the coroutine scope rather than the enclosing [MainActivity]. Kokoro is process-wide,
 * so resolve those calls through the application context instead of requiring an Activity instance.
 */
@Suppress("UNUSED_PARAMETER")
internal fun KokoroSpeechService.get(scope: CoroutineScope): KokoroSpeechEngine =
    get(MyApplication.CONTEXT)

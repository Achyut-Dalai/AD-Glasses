package com.achyut.adglasses.localagent.shizuku

import android.content.Context
import androidx.annotation.Keep
import java.util.concurrent.TimeUnit

/**
 * Privileged Shizuku user service with no generic command method. Its Binder surface is limited to
 * the fixed input operations that the Local Agent already knows how to perform.
 */
@Keep
class LocalAgentShizukuUserService @Keep constructor() : ILocalAgentShizukuInput.Stub() {

    @Keep
    constructor(@Suppress("UNUSED_PARAMETER") context: Context) : this()

    override fun destroy() {
        System.exit(0)
    }

    override fun pressEnter(): Boolean = runInput("keyevent", "66")

    override fun pressBack(): Boolean = runInput("keyevent", "4")

    override fun pressHome(): Boolean = runInput("keyevent", "3")

    override fun swipe(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        durationMs: Int,
    ): Boolean {
        if (listOf(startX, startY, endX, endY).any { it !in 0..MAX_COORDINATE }) return false
        if (durationMs !in MIN_SWIPE_DURATION_MS..MAX_SWIPE_DURATION_MS) return false
        return runInput(
            "swipe",
            startX.toString(),
            startY.toString(),
            endX.toString(),
            endY.toString(),
            durationMs.toString(),
        )
    }

    private fun runInput(vararg arguments: String): Boolean = runCatching {
        // ProcessBuilder receives a fixed executable and fixed argument shape. No shell is opened
        // and no model-produced command string can reach this process.
        val process = ProcessBuilder(INPUT_BINARY, *arguments)
            .redirectErrorStream(true)
            .start()
        try {
            process.waitFor(PROCESS_TIMEOUT_MS, TimeUnit.MILLISECONDS) && process.exitValue() == 0
        } finally {
            process.destroyForcibly()
        }
    }.getOrDefault(false)

    private companion object {
        private const val INPUT_BINARY = "/system/bin/input"
        private const val PROCESS_TIMEOUT_MS = 2_000L
        private const val MAX_COORDINATE = 10_000
        private const val MIN_SWIPE_DURATION_MS = 50
        private const val MAX_SWIPE_DURATION_MS = 5_000
    }
}

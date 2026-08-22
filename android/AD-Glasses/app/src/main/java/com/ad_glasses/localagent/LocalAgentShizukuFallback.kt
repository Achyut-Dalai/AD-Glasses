package com.ad_glasses.localagent

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.ad_glasses.BuildConfig
import com.ad_glasses.localagent.shizuku.ILocalAgentShizukuInput
import com.ad_glasses.localagent.shizuku.LocalAgentShizukuUserService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku

/**
 * Optional recovery path for a small subset of input actions. This never accepts a shell command
 * from the planner: it binds a narrowly scoped Shizuku user service with one Binder method per
 * fixed operation after Accessibility has already failed.
 */
object LocalAgentShizukuFallback {

    enum class Availability(val statusText: String) {
        UNAVAILABLE("Shizuku is unavailable"),
        UNSUPPORTED("Shizuku version is unsupported"),
        PERMISSION_REQUIRED("Shizuku permission is required"),
        READY("Shizuku ready"),
    }

    private sealed interface InputOperation {
        data object PressEnter : InputOperation
        data object PressBack : InputOperation
        data object PressHome : InputOperation
        data class Swipe(
            val startX: Int,
            val startY: Int,
            val endX: Int,
            val endY: Int,
            val durationMs: Int,
        ) : InputOperation
    }

    private val lock = Any()

    @Volatile
    private var inputService: ILocalAgentShizukuInput? = null
    private var pendingConnection: CompletableDeferred<ILocalAgentShizukuInput?>? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val connected = ILocalAgentShizukuInput.Stub.asInterface(service)
            inputService = connected
            synchronized(lock) {
                pendingConnection?.complete(connected)
                pendingConnection = null
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            inputService = null
            synchronized(lock) {
                pendingConnection?.complete(null)
                pendingConnection = null
            }
        }
    }

    fun availability(): Availability {
        if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
            return Availability.UNAVAILABLE
        }
        val version = runCatching { Shizuku.getVersion() }.getOrDefault(-1)
        if (runCatching { Shizuku.isPreV11() }.getOrDefault(true) || version < MIN_SHIZUKU_VERSION) {
            return Availability.UNSUPPORTED
        }
        return if (runCatching {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            }.getOrDefault(false)
        ) {
            Availability.READY
        } else {
            Availability.PERMISSION_REQUIRED
        }
    }

    fun requestPermission(context: Context): String {
        val current = availability()
        if (current != Availability.PERMISSION_REQUIRED) {
            LocalAgentPrefs.setShizukuStatus(context, current.statusText)
            return current.statusText
        }
        return runCatching {
            Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
            "Shizuku permission request sent"
        }.getOrElse {
            "Unable to request Shizuku permission"
        }.also { LocalAgentPrefs.setShizukuStatus(context, it) }
    }

    suspend fun performAfterAccessibilityFailure(context: Context, action: LocalAgentAction): Boolean {
        if (!LocalAgentPrefs.isShizukuFallbackEnabled(context)) return false
        if (!LocalAgentDeviceState.isReady(context)) return false

        val currentAvailability = availability()
        if (currentAvailability != Availability.READY) {
            LocalAgentPrefs.setShizukuStatus(context, currentAvailability.statusText)
            return false
        }

        val display = context.resources.displayMetrics
        val operation = inputOperationFor(action, display.widthPixels, display.heightPixels) ?: return false
        val service = awaitInputService(context) ?: run {
            LocalAgentPrefs.setShizukuStatus(context, "Shizuku input service unavailable")
            return false
        }

        val completed = withContext(Dispatchers.IO) {
            runCatching {
                when (operation) {
                    InputOperation.PressEnter -> service.pressEnter()
                    InputOperation.PressBack -> service.pressBack()
                    InputOperation.PressHome -> service.pressHome()
                    is InputOperation.Swipe -> service.swipe(
                        operation.startX,
                        operation.startY,
                        operation.endX,
                        operation.endY,
                        operation.durationMs,
                    )
                }
            }.getOrDefault(false)
        }
        LocalAgentPrefs.setShizukuStatus(
            context,
            if (completed) "Shizuku fallback executed ${action.javaClass.simpleName}" else "Shizuku fallback failed",
        )
        return completed
    }

    fun disconnect(context: Context) {
        inputService = null
        synchronized(lock) {
            pendingConnection?.complete(null)
            pendingConnection = null
        }
        runCatching { Shizuku.unbindUserService(userServiceArgs(context), connection, true) }
    }

    internal fun supportsFixedInputOperation(
        action: LocalAgentAction,
        screenWidth: Int,
        screenHeight: Int,
    ): Boolean = inputOperationFor(action, screenWidth, screenHeight) != null

    private suspend fun awaitInputService(context: Context): ILocalAgentShizukuInput? {
        inputService?.let { return it }
        val deferred = synchronized(lock) {
            inputService?.let { return@synchronized completedDeferred(it) }
            pendingConnection ?: CompletableDeferred<ILocalAgentShizukuInput?>().also { created ->
                pendingConnection = created
                runCatching { Shizuku.bindUserService(userServiceArgs(context), connection) }
                    .onFailure {
                        if (pendingConnection === created) pendingConnection = null
                        created.complete(null)
                    }
            }
        }
        val connected = withTimeoutOrNull(SERVICE_CONNECTION_TIMEOUT_MS) { deferred.await() }
        if (connected == null) {
            synchronized(lock) {
                if (pendingConnection === deferred) pendingConnection = null
            }
        }
        return connected
    }

    private fun userServiceArgs(context: Context): Shizuku.UserServiceArgs =
        Shizuku.UserServiceArgs(
            ComponentName(context.packageName, LocalAgentShizukuUserService::class.java.name),
        )
            .daemon(false)
            .processNameSuffix("local_agent_input")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)

    private fun completedDeferred(service: ILocalAgentShizukuInput): CompletableDeferred<ILocalAgentShizukuInput?> =
        CompletableDeferred<ILocalAgentShizukuInput?>().apply { complete(service) }

    private fun inputOperationFor(
        action: LocalAgentAction,
        screenWidth: Int,
        screenHeight: Int,
    ): InputOperation? {
        return when (action) {
            LocalAgentAction.PressEnter -> InputOperation.PressEnter
            LocalAgentAction.GlobalBack -> InputOperation.PressBack
            LocalAgentAction.GlobalHome -> InputOperation.PressHome
            is LocalAgentAction.Swipe -> {
                if (screenWidth <= 0 || screenHeight <= 0) return null
                if (action.startX !in 0..screenWidth || action.endX !in 0..screenWidth) return null
                if (action.startY !in 0..screenHeight || action.endY !in 0..screenHeight) return null
                if (action.durationMs !in MIN_SWIPE_DURATION_MS..MAX_SWIPE_DURATION_MS) return null
                InputOperation.Swipe(
                    startX = action.startX,
                    startY = action.startY,
                    endX = action.endX,
                    endY = action.endY,
                    durationMs = action.durationMs.toInt(),
                )
            }

            else -> null
        }
    }

    private const val MIN_SHIZUKU_VERSION = 10
    private const val PERMISSION_REQUEST_CODE = 7_391
    private const val SERVICE_CONNECTION_TIMEOUT_MS = 1_500L
    private const val MIN_SWIPE_DURATION_MS = 50L
    private const val MAX_SWIPE_DURATION_MS = 5_000L
}

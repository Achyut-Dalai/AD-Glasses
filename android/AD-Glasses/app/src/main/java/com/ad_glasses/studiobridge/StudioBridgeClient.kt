package com.ad_glasses.studiobridge

import android.content.Context
import android.os.Build
import android.util.Log
import com.ad_glasses.localmodels.remote.RemoteOpenAiPrefs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Persistent WebSocket client that connects to the ADGlasses Model Studio
 * desktop backend over Tailscale.
 *
 * Uses the same API key configured for remote model inference as the
 * authentication token.  Receives approval-request notifications and
 * delegates them to [StudioApprovalHandler].
 */
object StudioBridgeClient {

    private const val TAG = "StudioBridgeClient"
    private const val RECONNECT_BASE_MS = 2_000L
    private const val RECONNECT_MAX_MS = 60_000L
    private const val HEARTBEAT_INTERVAL_MS = 30_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connected = AtomicBoolean(false)
    private val shouldStayConnected = AtomicBoolean(false)
    private val activeWs = AtomicReference<WebSocket?>(null)
    private val connectionDone = AtomicReference<CompletableDeferred<Unit>?>(null)
    private val generation = AtomicInteger(0)
    private var approvalHandler: StudioApprovalHandler? = null

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)   // no read timeout for WS
        .connectTimeout(10, TimeUnit.SECONDS)
        .pingInterval(0, TimeUnit.MILLISECONDS)   // we handle heartbeats ourselves
        .build()

    /**
     * Start the bridge.  Call from Application.onCreate or when the user
     * enables Studio Bridge in settings.
     */
    fun start(context: Context, handler: StudioApprovalHandler) {
        approvalHandler = handler
        if (shouldStayConnected.getAndSet(true)) {
            Log.i(TAG, "start: already requested")
            return
        }
        val currentGeneration = generation.incrementAndGet()
        scope.launch { connectLoop(context.applicationContext, currentGeneration) }
    }

    /**
     * Stop the bridge and close the WebSocket.
     */
    fun stop() {
        shouldStayConnected.set(false)
        generation.incrementAndGet()
        activeWs.getAndSet(null)?.close(1000, "stopped")
        connectionDone.getAndSet(null)?.complete(Unit)
        connected.set(false)
    }

    fun isConnected(): Boolean = connected.get()

    fun isRunning(): Boolean = shouldStayConnected.get()

    /**
     * Send an approval response back to the desktop.
     */
    fun sendApprovalResponse(approvalId: String, decision: String, sessionId: String? = null) {
        val ws = activeWs.get()
        if (ws == null) {
            Log.w(TAG, "sendApprovalResponse: no active WebSocket")
            return
        }
        val payload = JSONObject().apply {
            put("type", "approval_response")
            put("approval_id", approvalId)
            put("decision", decision)
            sessionId?.let { put("session_id", it) }
        }
        ws.send(payload.toString())
        Log.i(TAG, "Sent approval response: $approvalId -> $decision")
    }

    // -----------------------------------------------------------------------
    // Internal
    // -----------------------------------------------------------------------

    private suspend fun connectLoop(appContext: Context, currentGeneration: Int) {
        var attempt = 0
        while (shouldStayConnected.get() && generation.get() == currentGeneration) {
            val baseUrl = RemoteOpenAiPrefs.getBaseUrl(appContext)
            val apiKey = RemoteOpenAiPrefs.getApiKey(appContext)

            if (baseUrl.isBlank() || apiKey.isBlank()) {
                Log.w(TAG, "No base URL or API key configured; retrying in 30s")
                delay(30_000)
                continue
            }
            if (!RemoteOpenAiPrefs.isCredentialTransportAllowed(baseUrl)) {
                Log.e(TAG, "Refusing to send an API key over a public cleartext URL")
                shouldStayConnected.set(false)
                break
            }

            val wsUrl = buildWsUrl(baseUrl)
            Log.i(TAG, "Connecting to $wsUrl (attempt ${attempt + 1})")

            try {
                val request = Request.Builder()
                    .url(wsUrl)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .build()

                val done = CompletableDeferred<Unit>()
                connectionDone.set(done)
                val ws = client.newWebSocket(request, object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        if (!shouldStayConnected.get() || generation.get() != currentGeneration) {
                            webSocket.close(1000, "superseded")
                            return
                        }
                        Log.i(TAG, "WebSocket opened")
                        connected.set(true)
                        activeWs.set(webSocket)
                        attempt = 0
                        scope.launch { heartbeatLoop(webSocket) }
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        handleMessage(appContext, text)
                    }

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        Log.i(TAG, "WebSocket closing: $code $reason")
                        webSocket.close(1000, null)
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        Log.i(TAG, "WebSocket closed: $code $reason")
                        connected.set(false)
                        activeWs.compareAndSet(webSocket, null)
                        done.complete(Unit)
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        Log.w(TAG, "WebSocket failure: ${t.message}")
                        connected.set(false)
                        activeWs.compareAndSet(webSocket, null)
                        done.complete(Unit)
                    }
                })

                // Wait for this exact socket attempt to close before reconnecting.
                done.await()
                connectionDone.compareAndSet(done, null)

                if (!shouldStayConnected.get() || generation.get() != currentGeneration) {
                    ws.close(1000, "stopped")
                    break
                }
            } catch (e: Exception) {
                connectionDone.getAndSet(null)?.complete(Unit)
                Log.w(TAG, "Connection error: ${e.message}")
            }

            // Exponential backoff
            attempt++
            val delayMs = (RECONNECT_BASE_MS * (1L shl (attempt - 1).coerceAtMost(5)))
                .coerceAtMost(RECONNECT_MAX_MS)
            Log.i(TAG, "Reconnecting in ${delayMs}ms")
            delay(delayMs)
        }
    }

    private fun handleMessage(context: Context, raw: String) {
        val msg = try {
            JSONObject(raw)
        } catch (e: Exception) {
            Log.w(TAG, "Invalid JSON: $raw")
            return
        }

        when (msg.optString("type")) {
            "paired" -> {
                val deviceId = msg.optString("device_id", "?")
                Log.i(TAG, "Paired with Studio as $deviceId")
            }

            "approval_request" -> {
                val approvalId = msg.optString("approval_id", "")
                val sessionId = msg.optString("session_id", "")
                val toolName = msg.optString("tool_name", "")
                val toolArgsSummary = msg.optString("tool_args_summary", "")
                val dangerLevel = msg.optString("danger_level", "medium")
                val ttsText = msg.optString("tts_text", "")

                Log.i(TAG, "Approval request: $toolName ($approvalId)")

                val handler = approvalHandler
                if (handler != null) {
                    scope.launch {
                        handler.handleApprovalRequest(
                            context = context,
                            approvalId = approvalId,
                            sessionId = sessionId,
                            toolName = toolName,
                            toolArgsSummary = toolArgsSummary,
                            dangerLevel = dangerLevel,
                            ttsText = ttsText,
                        )
                    }
                } else {
                    Log.w(TAG, "No approval handler; auto-denying")
                    sendApprovalResponse(approvalId, "deny", sessionId)
                }
            }

            "session_event" -> {
                val event = msg.optString("event", "")
                val message = msg.optString("message", "")
                Log.i(TAG, "Session event: $event - $message")
                approvalHandler?.handleSessionEvent(context, event, message)
            }

            "pong" -> { /* heartbeat ack */ }

            "approval_ack" -> {
                val resolved = msg.optBoolean("resolved", false)
                Log.i(TAG, "Approval ack: resolved=$resolved")
            }

            else -> {
                Log.d(TAG, "Unknown message type: ${msg.optString("type")}")
            }
        }
    }

    private suspend fun heartbeatLoop(ws: WebSocket) {
        while (connected.get() && shouldStayConnected.get()) {
            delay(HEARTBEAT_INTERVAL_MS)
            try {
                ws.send("""{"type":"heartbeat"}""")
            } catch (e: Exception) {
                Log.w(TAG, "Heartbeat failed: ${e.message}")
                break
            }
        }
    }

    internal fun buildWsUrl(httpBaseUrl: String): String {
        val uri = URI(httpBaseUrl.trim())
        val wsScheme = when (uri.scheme?.lowercase()) {
            "https" -> "wss"
            "http" -> "ws"
            else -> throw IllegalArgumentException("Studio URL must use http or https")
        }
        val cleanPath = uri.path.orEmpty().trimEnd('/')
        val v1Marker = Regex("/v1(?:/chat/completions)?$").find(cleanPath)
        val rootPath = if (v1Marker != null) cleanPath.substring(0, v1Marker.range.first) else cleanPath
        val bridgePath = "${rootPath.trimEnd('/')}/api/mobile/ws"
        val deviceId = "${Build.MANUFACTURER} ${Build.MODEL}"
        return URI(
            wsScheme,
            uri.userInfo,
            uri.host,
            uri.port,
            bridgePath,
            "device_id=$deviceId",
            null,
        ).toString()
    }
}

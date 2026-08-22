package com.ad_glasses.bridge.runtimes.mentra

import android.util.Log
import com.ad_glasses.bridge.core.DisplayCommand
import com.ad_glasses.bridge.core.GlassesBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID

/**
 * Local HTTP relay that accepts MentraOS-style display messages and routes
 * them through [GlassesBridge] to the active device adapter.
 *
 * ## Simplified HTTP mode
 *
 * This implementation uses plain HTTP POST (not WebSockets) for simplicity.
 * MentraOS apps send JSON messages to `POST /display` and receive JSON responses.
 *
 * ```
 * POST /display
 * Content-Type: application/json
 *
 * { "type": "display_event", "layout": { "layoutType": "text_wall", "text": "Hello" } }
 * ```
 *
 * ## Supported message types
 *
 * | Type                       | Description                        |
 * |----------------------------|------------------------------------|
 * | tpa_connection_init        | Handshake → returns ack           |
 * | display_event              | Render content on glasses          |
 * | subscription_update        | Subscribe to input streams         |
 * | dashboard_content_update   | Update dashboard view              |
 *
 * ## Input forwarding
 *
 * Input events from the glasses (button presses, head gestures) can be
 * forwarded to connected apps via [broadcastInput]. In the simplified HTTP
 * mode, this requires the app to maintain a persistent connection or poll.
 *
 * ## Thread safety
 *
 * The relay runs on [Dispatchers.IO] with a [SupervisorJob] so that
 * individual client-handling coroutines can fail independently.
 */
class MentraLocalRelay(
    /** Port to bind the HTTP server to. Default: 8002. */
    private val port: Int = 8002,
    /** Bridge instance to route display commands through. */
    private val glassesBridge: GlassesBridge = GlassesBridge,
) {

    companion object {
        private const val TAG = "MentraLocalRelay"

        /** HTTP response line templates. */
        private const val HTTP_OK = "HTTP/1.1 200 OK"
        private const val HTTP_BAD_REQUEST = "HTTP/1.1 400 Bad Request"
        private const val HTTP_NOT_FOUND = "HTTP/1.1 404 Not Found"
        private const val HTTP_INTERNAL_ERROR = "HTTP/1.1 500 Internal Server Error"

        @Volatile
        private var currentInstance: MentraLocalRelay? = null

        /** Stop the currently running relay instance (if any). */
        fun stopGlobal() {
            currentInstance?.stop()
            currentInstance = null
        }

        /** Check if a relay is currently running. */
        fun isRunning(): Boolean = currentInstance != null
    }

    // ── State ────────────────────────────────────────────────────────────────

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverSocket: ServerSocket? = null
    private val sessionManager = MentraSessionManager()
    private val mapper = MentraDisplayMapper
    private var isActive = false

    // ── Lifecycle ────────────────────────────────────────────────────────────

    /**
     * Start the HTTP relay server.
     *
     * The server accepts connections in a loop on [Dispatchers.IO] and
     * spawns a new coroutine for each client. Call [stop] to shut down.
     */
    fun start() {
        if (isActive) {
            Log.w(TAG, "Relay already running on port $port")
            return
        }
        isActive = true
        currentInstance = this

        scope.launch {
            try {
                serverSocket = ServerSocket(port)
                serverSocket?.reuseAddress = true
                Log.i(TAG, "MentraOS relay listening on port $port")

                while (isActive) {
                    val clientSocket: Socket = try {
                        serverSocket?.accept() ?: break
                    } catch (e: Exception) {
                        if (isActive) Log.e(TAG, "Accept failed: ${e.message}")
                        break
                    }
                    scope.launch { handleClient(clientSocket) }
                }
            } catch (e: Exception) {
                if (isActive) {
                    Log.e(TAG, "Server error: ${e.message}", e)
                }
            } finally {
                Log.i(TAG, "Relay accept loop exited")
            }
        }
    }

    /**
     * Stop the HTTP relay server and close all sessions.
     */
    fun stop() {
        isActive = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        sessionManager.clear()
        scope.coroutineContext[Job]?.let { job ->
            if (job.isActive) {
                scope.cancel()
            }
        }
        if (currentInstance === this) {
            currentInstance = null
        }
        Log.i(TAG, "Relay stopped")
    }

    // ── Client handling ──────────────────────────────────────────────────────

    /**
     * Handle a single client connection.
     *
     * Reads the HTTP request, dispatches to the appropriate handler,
     * and closes the connection when done.
     */
    private suspend fun handleClient(socket: Socket) {
        val remote = socket.inetAddress?.hostAddress ?: "unknown"
        Log.d(TAG, "Client connected: $remote")

        try {
            socket.soTimeout = 10_000 // 10 s read timeout
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            val output = socket.getOutputStream()

            // ── Parse request line ───────────────────────────────────────
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) {
                sendHttpResponse(output, 400, "{\"error\":\"Bad Request\"}")
                return
            }
            val method = parts[0]
            val path = parts[1]

            // ── Parse headers ────────────────────────────────────────────
            val headers = mutableMapOf<String, String>()
            var contentLength = 0
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val headerLine = line ?: break
                if (headerLine.isEmpty()) break
                val colonIdx = headerLine.indexOf(':')
                if (colonIdx > 0) {
                    val name = headerLine.substring(0, colonIdx).trim().lowercase()
                    val value = headerLine.substring(colonIdx + 1).trim()
                    headers[name] = value
                    if (name == "content-length") {
                        contentLength = value.toIntOrNull() ?: 0
                    }
                }
            }

            // ── Parse body ───────────────────────────────────────────────
            val body = if (contentLength > 0) {
                val buf = CharArray(contentLength)
                var totalRead = 0
                while (totalRead < contentLength) {
                    val read = reader.read(buf, totalRead, contentLength - totalRead)
                    if (read == -1) break
                    totalRead += read
                }
                String(buf, 0, totalRead)
            } else ""

            Log.d(TAG, "$method $path — ${body.length} bytes")

            // ── Route ────────────────────────────────────────────────────
            when {
                method == "POST" && path == "/display" -> {
                    handleDisplayPost(output, body)
                }
                method == "GET" && path == "/health" -> {
                    sendJsonResponse(output, JSONObject().apply {
                        put("status", "ok")
                        put("port", port)
                        put("sessions", sessionManager.getAllSessions().size)
                    })
                }
                else -> {
                    sendHttpResponse(output, 404, "{\"error\":\"Not Found\"}")
                }
            }
        } catch (e: java.net.SocketTimeoutException) {
            Log.w(TAG, "Client read timeout: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error handling client: ${e.message}", e)
        } finally {
            try {
                socket.close()
            } catch (_: Exception) {}
        }
    }

    // ── Message dispatch ────────────────────────────────────────────────────

    /**
     * Handle a `POST /display` request.
     *
     * Parses the JSON body and dispatches based on the `type` field.
     * Supported types are defined in [MentraMessageTypes].
     */
    private suspend fun handleDisplayPost(output: OutputStream, body: String) {
        try {
            val json = JSONObject(body)
            val type = json.optString("type", "")

            @Suppress("UNUSED_VARIABLE")
            val packageName = json.optString("packageName", "unknown")

            when (type) {
                MentraMessageTypes.TPA_CONNECTION_INIT -> {
                    handleConnectionInit(output, json)
                }

                MentraMessageTypes.DISPLAY_EVENT -> {
                    handleDisplayEvent(output, json)
                }

                MentraMessageTypes.SUBSCRIPTION_UPDATE -> {
                    handleSubscriptionUpdate(output, json)
                }

                MentraMessageTypes.DASHBOARD_CONTENT_UPDATE -> {
                    // Treat dashboard content as a display event
                    val layoutJson = json.optJSONObject("layout")
                    if (layoutJson != null) {
                        routeDisplayCommand(layoutJson)
                    }
                    sendJsonResponse(output, JSONObject().apply {
                        put("status", "ok")
                    })
                }

                // If layout is present directly without a type, try to render it
                else -> {
                    val layoutJson = json.optJSONObject("layout")
                    if (layoutJson != null) {
                        Log.d(TAG, "No type field, routing layout directly")
                        routeDisplayCommand(layoutJson)
                        sendJsonResponse(output, JSONObject().apply {
                            put("status", "ok")
                        })
                    } else {
                        Log.w(TAG, "Unknown message type: $type")
                        sendJsonResponse(output, JSONObject().apply {
                            put("status", "error")
                            put("message", "Unknown message type: $type")
                        })
                    }
                }
            }
        } catch (e: JSONException) {
            Log.e(TAG, "JSON parse error: ${e.message}", e)
            sendHttpResponse(output, 400, JSONObject().apply {
                put("status", "error")
                put("message", "Invalid JSON: ${e.message}")
            }.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error processing display message: ${e.message}", e)
            sendHttpResponse(output, 500, JSONObject().apply {
                put("status", "error")
                put("message", e.message ?: "Internal error")
            }.toString())
        }
    }

    // ── Message type handlers ───────────────────────────────────────────────

    /**
     * Handle `tpa_connection_init`: validate and acknowledge the connection.
     */
    private fun handleConnectionInit(output: OutputStream, json: JSONObject) {
        val sessionId = json.optString(
            "sessionId",
            UUID.randomUUID().toString()
        )
        val pkgName = json.optString("packageName", "unknown")

        // Register the session
        val session = MentraSessionManager.Session(
            sessionId = sessionId,
            packageName = pkgName,
            outputStream = output,
        )
        sessionManager.addSession(session)

        Log.i(TAG, "Connection init from $pkgName (session: $sessionId)")

        val ack = JSONObject().apply {
            put("type", MentraMessageTypes.TPA_CONNECTION_ACK)
            put("sessionId", sessionId)
            put("settings", JSONObject())
            put("status", "ok")
        }
        sendJsonResponse(output, ack)
    }

    /**
     * Handle `display_event`: map the layout to a [DisplayCommand] and
     * route it through the glasses bridge.
     */
    private suspend fun handleDisplayEvent(output: OutputStream, json: JSONObject) {
        val layoutJson = json.optJSONObject("layout")
        val packageName = json.optString("packageName", "unknown")

        if (layoutJson == null) {
            sendJsonResponse(output, JSONObject().apply {
                put("status", "error")
                put("message", "Missing 'layout' field")
            })
            return
        }

        val command = mapper.mapToDisplayCommand(layoutJson)
        Log.i(TAG, "Display from $packageName: ${command::class.simpleName}")

        routeDisplayCommand(command)

        sendJsonResponse(output, JSONObject().apply {
            put("status", "ok")
            put("type", command::class.simpleName)
        })
    }

    /**
     * Handle `subscription_update`: record the app's desired input streams.
     */
    private fun handleSubscriptionUpdate(output: OutputStream, json: JSONObject) {
        val sessionId = json.optString("sessionId", "")
        val subscriptions = mutableSetOf<String>()

        val subsArray = json.optJSONArray("subscriptions")
        if (subsArray != null) {
            for (i in 0 until subsArray.length()) {
                subsArray.optString(i)?.let { subscriptions.add(it) }
            }
        }

        // Update session subscriptions if we have a matching session
        val session = sessionManager.getSession(sessionId)
        if (session != null) {
            session.subscriptions.clear()
            session.subscriptions.addAll(subscriptions)
            Log.i(TAG, "Updated subscriptions for $sessionId: $subscriptions")
        } else {
            Log.w(TAG, "Subscription update for unknown session: $sessionId")
        }

        sendJsonResponse(output, JSONObject().apply {
            put("status", "ok")
            put("subscriptions", subscriptions.toList())
        })
    }

    // ── Command routing ─────────────────────────────────────────────────────

    /**
     * Route a [DisplayCommand] through the glasses bridge.
     */
    private suspend fun routeDisplayCommand(command: DisplayCommand) {
        try {
            val result = when (command) {
                is DisplayCommand.Text -> glassesBridge.showText(command)
                is DisplayCommand.Lines -> glassesBridge.showLines(command)
                is DisplayCommand.Card -> glassesBridge.showCard(command)
                is DisplayCommand.Clear -> glassesBridge.clearDisplay()
            }
            if (result.isFailure) {
                Log.w(TAG, "Bridge command failed: ${result.exceptionOrNull()?.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Bridge routing error: ${e.message}", e)
        }
    }

    /**
     * Convenience: parse a layout JSON object and route the resulting command.
     */
    private suspend fun routeDisplayCommand(layoutJson: JSONObject) {
        val command = mapper.mapToDisplayCommand(layoutJson)
        routeDisplayCommand(command)
    }

    // ── Input forwarding ────────────────────────────────────────────────────

    /**
     * Broadcast an input event to all connected sessions.
     *
     * In the simplified HTTP mode, this sends the event over the session's
     * output stream. For persistent push, the app should either:
     * - Keep the HTTP connection alive (not ideal)
     * - Use a long-poll or WebSocket upgrade (future enhancement)
     *
     * @param eventType One of the [MentraMessageTypes].STREAM_* constants.
     * @param data JSON object with event-specific fields.
     */
    fun broadcastInput(eventType: String, data: JSONObject) {
        val message = JSONObject().apply {
            put("type", MentraMessageTypes.DATA_STREAM)
            put("streamType", eventType)
            put("data", data)
        }
        sessionManager.broadcastMessage(message)
    }

    // ── HTTP helpers ─────────────────────────────────────────────────────────

    /**
     * Send a complete HTTP response with the given status code and JSON body.
     */
    private fun sendHttpResponse(output: OutputStream, statusCode: Int, body: String) {
        val statusLine = when (statusCode) {
            200 -> HTTP_OK
            400 -> HTTP_BAD_REQUEST
            404 -> HTTP_NOT_FOUND
            500 -> HTTP_INTERNAL_ERROR
            else -> "HTTP/1.1 $statusCode"
        }
        val bodyBytes = body.toByteArray(Charsets.UTF_8)

        val response = buildString {
            append(statusLine)
            append("\r\n")
            append("Content-Type: application/json; charset=utf-8\r\n")
            append("Content-Length: ${bodyBytes.size}\r\n")
            append("Connection: close\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("\r\n")
        }.toByteArray(Charsets.UTF_8)

        try {
            output.write(response)
            output.write(bodyBytes)
            output.flush()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send HTTP response: ${e.message}")
        }
    }

    /**
     * Send a 200 OK response with a JSON object body.
     */
    private fun sendJsonResponse(output: OutputStream, json: JSONObject) {
        sendHttpResponse(output, 200, json.toString())
    }

    // ── Accessors ───────────────────────────────────────────────────────────

    /** The port the relay is bound to. */
    fun getPort(): Int = port

    /** Number of currently registered sessions. */
    fun getSessionCount(): Int = sessionManager.getAllSessions().size

    /** Whether the relay accept loop is active. */
    fun isRunning(): Boolean = isActive
}

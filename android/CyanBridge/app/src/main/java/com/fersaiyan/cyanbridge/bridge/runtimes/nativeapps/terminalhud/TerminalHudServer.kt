package com.fersaiyan.cyanbridge.bridge.runtimes.nativeapps.terminalhud

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket

/**
 * HTTP server that receives agent state updates from a laptop
 * and forwards them to TerminalHudApp for display on glasses.
 *
 * Endpoints:
 * - POST /state — update agent state (JSON body)
 * - POST /permission — trigger permission prompt (JSON body)
 * - POST /clear — clear the display
 * - GET /status — check if the HUD is running
 *
 * Example:
 * ```
 * curl -X POST http://<phone-ip>:8080/state \
 *   -H 'Content-Type: application/json' \
 *   -d '{"provider":"claude","repo":"my-project","status":"working","lines":["Adding feature X","Building..."]}'
 * ```
 */
class TerminalHudServer(
    private val port: Int = 8080,
) {
    companion object {
        private const val TAG = "TerminalHudServer"

        @Volatile
        private var currentInstance: TerminalHudServer? = null

        fun stopGlobal() {
            currentInstance?.stop()
            currentInstance = null
        }

        fun isRunning(): Boolean = currentInstance != null
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverSocket: ServerSocket? = null

    fun start() {
        currentInstance = this
        scope.launch {
            try {
                serverSocket = ServerSocket(port)
                Log.i(TAG, "Terminal HUD server listening on port $port")
                while (isActive) {
                    val socket = serverSocket?.accept() ?: break
                    scope.launch { handleConnection(socket) }
                }
            } catch (e: Exception) {
                if (isActive) {
                    Log.e(TAG, "Server error", e)
                }
            }
        }
    }

    fun stop() {
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        if (currentInstance == this) currentInstance = null
    }

    private fun handleConnection(socket: java.net.Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val writer = PrintWriter(socket.getOutputStream(), true)

            // Read HTTP request line
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0]
            val path = parts[1]

            // Read headers
            val headers = mutableMapOf<String, String>()
            var line = reader.readLine()
            while (line != null && line.isNotEmpty()) {
                val colonIdx = line.indexOf(':')
                if (colonIdx > 0) {
                    headers[line.substring(0, colonIdx).trim().lowercase()] =
                        line.substring(colonIdx + 1).trim()
                }
                line = reader.readLine()
            }

            // Read body if Content-Length present
            val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
            val body = if (contentLength > 0) {
                val chars = CharArray(contentLength)
                reader.read(chars, 0, contentLength)
                String(chars)
            } else {
                ""
            }

            // Route request
            val response = when {
                method == "POST" && path == "/state" -> handleState(body)
                method == "POST" && path == "/permission" -> handlePermission(body)
                method == "POST" && path == "/clear" -> handleClear()
                method == "GET" && path == "/status" -> handleStatus()
                else -> HttpResponse(404, """{"error":"not found"}""")
            }

            // Send response
            writer.println("HTTP/1.1 ${response.code} ${if (response.code == 200) "OK" else "Error"}")
            writer.println("Content-Type: application/json")
            writer.println("Content-Length: ${response.body.length}")
            writer.println("Connection: close")
            writer.println()
            writer.println(response.body)

            socket.close()
        } catch (e: Exception) {
            Log.e(TAG, "Connection error", e)
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun handleState(body: String): HttpResponse {
        return try {
            val json = JSONObject(body)
            val provider = when (json.optString("provider", "").lowercase()) {
                "claude" -> AgentProvider.CLAUDE
                "codex" -> AgentProvider.CODEX
                "opencode" -> AgentProvider.OPENCODE
                else -> AgentProvider.UNKNOWN
            }
            val status = when (json.optString("status", "").lowercase()) {
                "idle" -> AgentStatus.IDLE
                "thinking" -> AgentStatus.THINKING
                "working" -> AgentStatus.WORKING
                "permission" -> AgentStatus.WAITING_PERMISSION
                "error" -> AgentStatus.ERROR
                "completed", "done" -> AgentStatus.COMPLETED
                else -> AgentStatus.IDLE
            }
            val lines = mutableListOf<String>()
            val linesArray = json.optJSONArray("lines")
            if (linesArray != null) {
                for (i in 0 until linesArray.length()) {
                    lines.add(linesArray.getString(i))
                }
            }

            TerminalHudApp.update(TerminalHudState(
                provider = provider,
                repoName = json.optString("repo", ""),
                status = status,
                recentLines = lines,
            ))

            HttpResponse(200, """{"ok":true}""")
        } catch (e: Exception) {
            HttpResponse(400, """{"error":"${e.message}"}""")
        }
    }

    private fun handlePermission(body: String): HttpResponse {
        return try {
            val json = JSONObject(body)
            TerminalHudApp.update(TerminalHudState(
                provider = TerminalHudApp.state.value.provider,
                repoName = TerminalHudApp.state.value.repoName,
                status = AgentStatus.WAITING_PERMISSION,
                recentLines = TerminalHudApp.state.value.recentLines,
                pendingPermission = PermissionRequest(
                    description = json.optString("description", "Permission requested"),
                    allowLabel = json.optString("allowLabel", "ALLOW"),
                    denyLabel = json.optString("denyLabel", "DENY"),
                ),
            ))
            HttpResponse(200, """{"ok":true}""")
        } catch (e: Exception) {
            HttpResponse(400, """{"error":"${e.message}"}""")
        }
    }

    private fun handleClear(): HttpResponse {
        TerminalHudApp.update(TerminalHudState())
        return HttpResponse(200, """{"ok":true}""")
    }

    private fun handleStatus(): HttpResponse {
        return HttpResponse(200, """{"running":true,"active":${TerminalHudApp.state.value.status != AgentStatus.IDLE}}""")
    }

    private data class HttpResponse(val code: Int, val body: String)
}

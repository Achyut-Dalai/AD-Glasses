package com.fersaiyan.cyanbridge.ota

import android.util.Log
import java.io.File
import java.net.ServerSocket
import java.net.Socket

/**
 * Minimal HTTP server that serves a single file to the glasses during OTA.
 *
 * The glasses' ai_glass_ota agent does a plain HTTP GET to fetch the .swu file.
 * This server binds to the phone's P2P IP and serves the firmware from [serveFile].
 */
class OtaHttpServer(
    private val port: Int = 8080,
) {
    private var serverSocket: ServerSocket? = null
    private var serveFile: File? = null

    @Volatile
    private var running = false

    val isRunning: Boolean get() = running

    fun start(file: File) {
        if (running) {
            Log.w(TAG, "Server already running, stopping previous instance")
            stop()
        }
        serveFile = file
        running = true
        Thread(
            {
                try {
                    serverSocket = ServerSocket(port)
                    Log.i(TAG, "Listening on port $port, serving ${file.name} (${file.length()} bytes)")
                    while (running) {
                        try {
                            val client = serverSocket!!.accept()
                            handleClient(client, file)
                        } catch (e: Exception) {
                            if (running) Log.e(TAG, "Accept error: ${e.message}")
                        }
                    }
                } catch (e: Exception) {
                    if (running) Log.e(TAG, "Server error: ${e.message}", e)
                }
            },
            "OtaHttpServer",
        ).start()
    }

    fun stop() {
        running = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
        Log.i(TAG, "Stopped")
    }

    private fun handleClient(socket: Socket, file: File) {
        try {
            val input = socket.getInputStream().bufferedReader()
            val requestLine = input.readLine() ?: return
            // Drain remaining headers
            while (true) {
                val line = input.readLine()
                if (line.isNullOrEmpty()) break
            }

            if (requestLine.startsWith("GET /")) {
                val out = socket.getOutputStream()
                val header = buildString {
                    append("HTTP/1.1 200 OK\r\n")
                    append("Content-Type: application/octet-stream\r\n")
                    append("Content-Length: ${file.length()}\r\n")
                    append("Connection: close\r\n")
                    append("Access-Control-Allow-Origin: *\r\n")
                    append("\r\n")
                }
                out.write(header.toByteArray())
                file.inputStream().use { fis ->
                    val buf = ByteArray(8192)
                    var read: Int
                    while (fis.read(buf).also { read = it } > 0) {
                        out.write(buf, 0, read)
                        out.flush()
                    }
                }
                Log.i(TAG, "Served ${file.name} to ${socket.inetAddress.hostAddress}")
            } else {
                val resp = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                socket.getOutputStream().write(resp.toByteArray())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Client error: ${e.message}")
        } finally {
            try {
                socket.close()
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        private const val TAG = "OtaHttpServer"
    }
}

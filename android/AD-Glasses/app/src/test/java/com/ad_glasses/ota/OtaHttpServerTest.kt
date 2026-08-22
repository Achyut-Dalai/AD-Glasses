package com.ad_glasses.ota

import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class OtaHttpServerTest {
    @Test
    fun `server binds synchronously serves file and stops`() {
        val firmware = Files.createTempFile("ota-http-", ".swu").toFile().apply {
            writeBytes("firmware-body".toByteArray())
        }
        val port = unusedLoopbackPort()
        val server = OtaHttpServer(port)

        try {
            server.start(firmware, "127.0.0.1")
            assertTrue(server.isRunning)

            val response = request(port)
            assertTrue(response.startsWith("HTTP/1.1 200 OK\r\n"))
            assertTrue(response.endsWith("firmware-body"))

            server.stop()
            assertFalse(server.isRunning)
        } finally {
            server.stop()
            firmware.delete()
        }
    }

    @Test
    fun `restarting the same instance does not let the old thread stop the new server`() {
        val firmware = Files.createTempFile("ota-http-restart-", ".swu").toFile().apply {
            writeBytes("restart-body".toByteArray())
        }
        val port = unusedLoopbackPort()
        val server = OtaHttpServer(port)

        try {
            server.start(firmware, "127.0.0.1")
            server.start(firmware, "127.0.0.1")
            Thread.sleep(100)

            assertTrue(server.isRunning)
            assertTrue(request(port).endsWith("restart-body"))
        } finally {
            server.stop()
            firmware.delete()
        }
    }

    private fun request(port: Int): String = Socket().use { socket ->
        socket.soTimeout = 2_000
        socket.connect(InetSocketAddress("127.0.0.1", port), 2_000)
        socket.getOutputStream().apply {
            write("GET /firmware.swu HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n".toByteArray())
            flush()
        }
        String(socket.getInputStream().readBytes(), StandardCharsets.ISO_8859_1)
    }

    private fun unusedLoopbackPort(): Int = ServerSocket(0).use { it.localPort }
}

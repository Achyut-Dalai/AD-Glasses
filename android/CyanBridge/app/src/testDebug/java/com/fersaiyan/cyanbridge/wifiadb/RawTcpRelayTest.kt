package com.achyut.adglasses.wifiadb

import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RawTcpRelayTest {
    @Test
    fun forwardsBothDirectionsSupportsReconnectAndCanRestartAfterStop() {
        val backend = EchoServer()
        val relayPort = unusedPort()
        val relay = RawTcpRelay(relayPort) {
            Socket().apply { connect(InetSocketAddress("127.0.0.1", backend.port), 1_000) }
        }
        try {
            relay.start()
            assertEquals("first", roundTrip(relayPort, "first"))
            assertEquals("later reconnect", roundTrip(relayPort, "later reconnect"))

            relay.close()
            assertFalse(relay.isRunning)
            assertFalse(canConnect(relayPort))

            relay.start()
            assertTrue(relay.isRunning)
            assertEquals("after restart", roundTrip(relayPort, "after restart"))
        } finally {
            relay.close()
            backend.close()
        }
    }

    private fun roundTrip(port: Int, payload: String): String =
        Socket().use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", port), 1_000)
            socket.soTimeout = 2_000
            socket.getOutputStream().write(payload.toByteArray())
            socket.shutdownOutput()
            socket.getInputStream().readBytes().decodeToString()
        }

    private fun canConnect(port: Int): Boolean = runCatching {
        Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 250) }
    }.isSuccess

    private fun unusedPort(): Int = ServerSocket(0).use { it.localPort }

    private class EchoServer : AutoCloseable {
        private val server = ServerSocket(0)
        private val executor = Executors.newCachedThreadPool()
        val port: Int = server.localPort

        init {
            executor.execute {
                while (!server.isClosed) {
                    val socket = runCatching { server.accept() }.getOrNull() ?: break
                    executor.execute {
                        socket.use {
                            it.getInputStream().copyTo(it.getOutputStream())
                        }
                    }
                }
            }
        }

        override fun close() {
            server.close()
            executor.shutdownNow()
            executor.awaitTermination(2, TimeUnit.SECONDS)
        }
    }
}

package com.adglasses.app.core.model

import java.util.UUID

data class ScannedGlasses(
    val name: String,
    val address: String,
    val rssi: Int,
)

enum class ConnectionPhase {
    Disconnected,
    Scanning,
    Connecting,
    Discovering,
    Initializing,
    Ready,
    Error,
}

data class GlassesConnectionState(
    val phase: ConnectionPhase = ConnectionPhase.Disconnected,
    val deviceName: String? = null,
    val address: String? = null,
    val batteryPercent: Int? = null,
    val charging: Boolean = false,
    val detail: String? = null,
) {
    val isReady: Boolean get() = phase == ConnectionPhase.Ready
}

enum class MessageRole { User, Assistant }

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: MessageRole,
    val text: String,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
)

data class CapturedNotification(
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val postedAtEpochMs: Long,
    val key: String,
)

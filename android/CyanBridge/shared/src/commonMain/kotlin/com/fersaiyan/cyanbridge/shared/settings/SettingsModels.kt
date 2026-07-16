package com.fersaiyan.cyanbridge.shared.settings

enum class AgentProviderType {
    PRO_SUBSCRIPTION,
    LOCAL_MODEL,
    REMOTE_SERVER,
    STUDIO_BRIDGE;

    val label: String
        get() = when (this) {
            PRO_SUBSCRIPTION -> "Pro subscription (cloud)"
            LOCAL_MODEL -> "Local model (on-device)"
            REMOTE_SERVER -> "Remote server (LAN)"
            STUDIO_BRIDGE -> "Studio Bridge"
        }
}

enum class CaptureSource {
    BLUETOOTH_MIC,
    PHONE_MIC;
}

enum class MemoryPrivacyMode {
    PRIVATE_LOCAL,
    CLOUD_SYNC;
}

enum class MemorySourceType {
    EXPLICIT,
    DAILY,
    OCR,
    DERIVED;
}

enum class SettingsSection {
    AI_AUTOMATION,
    LOCAL_AGENT,
    MEMORY_PRIVACY,
    TRANSCRIPTS,
    DATA,
    AGENT,
    SUPPORT,
    FAQ,
}

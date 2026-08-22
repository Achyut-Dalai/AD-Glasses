package com.fersaiyan.cyanbridge.shared.settings

enum class AgentProviderType {
    LOCAL_AGENT,
    /** Persisted legacy enum token retained for migration compatibility; functionally this is AD Cloud AI. */
    PRO_SUBSCRIPTION;

    val label: String
        get() = when (this) {
            LOCAL_AGENT -> "Local AI"
            PRO_SUBSCRIPTION -> "Cloud AI"
        }
}

enum class CaptureSource {
    BLUETOOTH_MIC,
    PHONE_MIC;
}

enum class MemoryPrivacyMode(
    val title: String,
    val description: String,
) {
    PRIVATE_LOCAL(
        title = "Private Local",
        description = "All memory, indexes, and retrieval stay on-device.",
    ),
    ENCRYPTED_SYNC(
        title = "Encrypted Sync",
        description = "Client-side encrypted sync payloads are prepared locally. Backend pending.",
    ),
    FAST_CLOUD_MEMORY(
        title = "Fast Cloud Memory",
        description = "Future cloud memory mode. Unavailable until backend exists.",
    ),
    CONFIDENTIAL_CLOUD_BETA(
        title = "Confidential Cloud Beta",
        description = "Future confidential cloud mode. Unavailable until backend exists.",
    );

    companion object {
        fun fromRaw(raw: String?): MemoryPrivacyMode {
            val normalized = raw?.trim()?.uppercase().orEmpty()
            return entries.firstOrNull { it.name == normalized } ?: PRIVATE_LOCAL
        }
    }
}

enum class MemorySourceType {
    EXPLICIT_USER_FACT,
    AUTO_DAILY_FACT,
    SCREEN_OCR,
    DERIVED_SUMMARY,
    IMPORTED_TEXT,
    SYSTEM_NOTE,
}

enum class SettingsSection {
    AI_AUTOMATION,
    MEMORY_PRIVACY,
    TRANSCRIPTS,
    DATA,
    SUPPORT,
    FAQ,
}

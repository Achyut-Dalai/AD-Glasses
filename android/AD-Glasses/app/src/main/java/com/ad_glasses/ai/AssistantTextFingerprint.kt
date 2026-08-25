package com.ad_glasses.ai

import java.security.MessageDigest

/** Privacy-safe correlation token for proving that the same assistant text crossed subsystem boundaries. */
internal object AssistantTextFingerprint {
    fun of(text: String): String {
        val clean = text.trim()
        if (clean.isEmpty()) return "empty"
        val digest = MessageDigest.getInstance("SHA-256").digest(clean.toByteArray(Charsets.UTF_8))
        return digest.take(HASH_BYTES).joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private const val HASH_BYTES = 6
}

package com.fersaiyan.cyanbridge.glasses

import java.net.URLEncoder

/** Input validation boundary for the glasses' firmware-owned cleartext Wi-Fi Direct server. */
object GlassesTransportSecurityPolicy {
    fun privateIpv4OrNull(value: String?): String? {
        val normalized = value?.trim().orEmpty()
        val parts = normalized.split('.')
        if (parts.size != 4) return null
        val octets = parts.map { part ->
            if (part.isBlank() || part.length > 3 || part.any { !it.isDigit() }) return null
            part.toIntOrNull()?.takeIf { it in 0..255 } ?: return null
        }
        val privateAddress = octets[0] == 10 ||
            (octets[0] == 172 && octets[1] in 16..31) ||
            (octets[0] == 192 && octets[1] == 168)
        if (!privateAddress || octets.all { it == 0 } || octets[3] == 0 || octets[3] == 255) return null
        return octets.joinToString(".")
    }

    fun mediaFileNameOrNull(value: String?): String? {
        val name = value?.trim().orEmpty()
        if (name.isBlank() || name.length > MAX_MEDIA_FILE_NAME_CHARS) return null
        if (name == "." || name == ".." || name.contains("..")) return null
        if (name.any { it.isISOControl() }) return null
        if (name.any { it in FORBIDDEN_FILE_NAME_CHARS }) return null
        return name
    }

    fun mediaUrl(deviceIp: String, fileName: String): String {
        val safeIp = requireNotNull(privateIpv4OrNull(deviceIp)) { "Glasses IP is not a private IPv4 address" }
        val safeName = requireNotNull(mediaFileNameOrNull(fileName)) { "Unsafe media filename" }
        val encodedName = URLEncoder.encode(safeName, Charsets.UTF_8.name()).replace("+", "%20")
        return "http://$safeIp/files/$encodedName"
    }

    private const val MAX_MEDIA_FILE_NAME_CHARS = 180
    private val FORBIDDEN_FILE_NAME_CHARS = setOf('/', '\\', '?', '#', '%', ':')
}

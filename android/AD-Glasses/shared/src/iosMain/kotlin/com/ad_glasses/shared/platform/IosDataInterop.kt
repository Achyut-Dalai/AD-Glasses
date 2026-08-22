package com.ad_glasses.shared.platform

import platform.Foundation.NSData
import platform.Foundation.NSISOLatin1StringEncoding
import platform.Foundation.NSString
import platform.Foundation.dataUsingEncoding

/** Converts arbitrary bytes without relying on unavailable NSData factory overloads. */
@Suppress("CAST_NEVER_SUCCEEDS")
internal fun ByteArray.toIosNSData(): NSData {
    val value = buildString(size) {
        for (byte in this@toIosNSData) append((byte.toInt() and 0xFF).toChar())
    }
    return (value as NSString).dataUsingEncoding(NSISOLatin1StringEncoding)
        ?: error("Unable to create NSData")
}

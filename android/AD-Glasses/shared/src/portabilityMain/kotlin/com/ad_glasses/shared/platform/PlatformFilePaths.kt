package com.ad_glasses.shared.platform

import java.io.File

actual object PlatformFilePaths {
    actual fun dataDirectory(): String = System.getProperty("user.home") ?: "/tmp"
    actual fun cacheDirectory(): String = System.getProperty("java.io.tmpdir") ?: "/tmp"
    actual fun tempDirectory(): String = System.getProperty("java.io.tmpdir") ?: "/tmp"
    actual fun separator(): String = File.separator
}

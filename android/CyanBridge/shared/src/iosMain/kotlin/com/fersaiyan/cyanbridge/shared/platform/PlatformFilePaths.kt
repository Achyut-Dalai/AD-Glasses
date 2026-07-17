package com.fersaiyan.cyanbridge.shared.platform

import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSTemporaryDirectory

actual object PlatformFilePaths {
    actual fun dataDirectory(): String {
        val fm = NSFileManager.defaultManager
        val urls = fm.URLsForDirectory(NSDocumentDirectory, NSUserDomainMask)
        return (urls.firstOrNull()?.path) ?: ""
    }

    actual fun cacheDirectory(): String {
        val fm = NSFileManager.defaultManager
        val urls = fm.URLsForDirectory(NSCachesDirectory, NSUserDomainMask)
        return (urls.firstOrNull()?.path) ?: ""
    }

    actual fun tempDirectory(): String = NSTemporaryDirectory()

    actual fun separator(): String = "/"
}

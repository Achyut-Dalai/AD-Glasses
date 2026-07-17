package com.fersaiyan.cyanbridge.shared.platform

actual object PlatformFilePaths {
    actual fun dataDirectory(): String {
        val fm = platform.Foundation.NSFileManager.defaultManager
        val urls = fm.URLsForDirectory(platform.Foundation.NSDocumentDirectory, platform.Foundation.NSUserDomainMask)
        val url = urls.firstOrNull() as? platform.Foundation.NSURL
        return url?.relativePath ?: ""
    }

    actual fun cacheDirectory(): String {
        val fm = platform.Foundation.NSFileManager.defaultManager
        val urls = fm.URLsForDirectory(platform.Foundation.NSCachesDirectory, platform.Foundation.NSUserDomainMask)
        val url = urls.firstOrNull() as? platform.Foundation.NSURL
        return url?.relativePath ?: ""
    }

    actual fun tempDirectory(): String = platform.Foundation.NSTemporaryDirectory()

    actual fun separator(): String = "/"
}

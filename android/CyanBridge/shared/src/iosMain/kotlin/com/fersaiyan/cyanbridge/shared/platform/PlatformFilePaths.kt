package com.achyut.adglasses.shared.platform

actual object PlatformFilePaths {
    actual fun dataDirectory(): String {
        // Use NSHomeDirectory() + /Documents
        return platform.Foundation.NSHomeDirectory() + "/Documents"
    }

    actual fun cacheDirectory(): String {
        return platform.Foundation.NSHomeDirectory() + "/Library/Caches"
    }

    actual fun tempDirectory(): String = platform.Foundation.NSTemporaryDirectory()

    actual fun separator(): String = "/"
}

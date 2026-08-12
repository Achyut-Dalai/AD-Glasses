package com.achyut.adglasses.shared.platform

/**
 * Cross-platform file path abstraction.
 * Provides access to app-specific directories for data storage.
 */
expect object PlatformFilePaths {
    /** Directory for persistent app data (documents, databases). */
    fun dataDirectory(): String

    /** Directory for caches that can be cleared. */
    fun cacheDirectory(): String

    /** Directory for temporary files. */
    fun tempDirectory(): String

    /** Platform-specific path separator. */
    fun separator(): String
}
